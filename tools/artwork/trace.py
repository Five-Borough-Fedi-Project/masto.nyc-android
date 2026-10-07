#!/usr/bin/env python3
"""Traces a black-on-white PNG into Android vector drawable paths. Pure stdlib.

Follows the cracks between ink and background to get exact closed loops, simplifies them, and
writes the result as path data. Loops are nested (a stroke gives an outer and an inner loop), so
the nesting depth decides which loops each variant keeps.
"""
import struct
import sys
import zlib


def read_png(path):
    data = open(path, 'rb').read()
    pos, idat, w, h, bpp = 8, b'', 0, 0, 0
    while pos < len(data):
        length, = struct.unpack('>I', data[pos:pos + 4])
        kind = data[pos + 4:pos + 8]
        chunk = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if kind == b'IHDR':
            w, h, depth, color = struct.unpack('>IIBB', chunk[:10])
            assert depth == 8 and color in (0, 2, 4, 6), (depth, color)
            bpp = {0: 1, 2: 3, 4: 2, 6: 4}[color]
        elif kind == b'IDAT':
            idat += chunk
    raw = zlib.decompress(idat)
    rows, prev, i = [], bytearray(w * bpp), 0
    for _ in range(h):
        f = raw[i]
        line = bytearray(raw[i + 1:i + 1 + w * bpp])
        i += 1 + w * bpp
        for x in range(len(line)):
            a = line[x - bpp] if x >= bpp else 0
            b = prev[x]
            c = prev[x - bpp] if x >= bpp else 0
            if f == 1: line[x] = (line[x] + a) & 255
            elif f == 2: line[x] = (line[x] + b) & 255
            elif f == 3: line[x] = (line[x] + (a + b) // 2) & 255
            elif f == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[x] = (line[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        rows.append(bytes(line))
        prev = line
    return w, h, bpp, rows


def ink_mask(w, h, bpp, rows, threshold=128):
    """True where the drawing has ink: dark and not transparent."""
    mask = [[False] * w for _ in range(h)]
    for y in range(h):
        row = rows[y]
        for x in range(w):
            px = row[x * bpp:(x + 1) * bpp]
            if bpp == 1: lum, alpha = px[0], 255
            elif bpp == 2: lum, alpha = px[0], px[1]
            elif bpp == 3: lum, alpha = (px[0] * 299 + px[1] * 587 + px[2] * 114) // 1000, 255
            else: lum, alpha = (px[0] * 299 + px[1] * 587 + px[2] * 114) // 1000, px[3]
            mask[y][x] = alpha > 128 and lum < threshold
    return mask


def trace_loops(mask, w, h):
    """Walks the boundary between ink and background, returning closed pixel-corner loops."""
    def ink(x, y):
        return 0 <= x < w and 0 <= y < h and mask[y][x]

    # Each boundary crack, directed so ink is on the left.
    edges = {}
    for y in range(h):
        for x in range(w):
            if not ink(x, y):
                continue
            if not ink(x, y - 1): edges.setdefault((x + 1, y), []).append((x, y))
            if not ink(x + 1, y): edges.setdefault((x + 1, y + 1), []).append((x + 1, y))
            if not ink(x, y + 1): edges.setdefault((x, y + 1), []).append((x + 1, y + 1))
            if not ink(x - 1, y): edges.setdefault((x, y), []).append((x, y + 1))

    loops = []
    while edges:
        start = next(iter(edges))
        loop = [start]
        node = start
        while True:
            outs = edges.get(node)
            if not outs:
                break
            nxt = outs.pop()
            if not outs:
                del edges[node]
            loop.append(nxt)
            node = nxt
            if node == start:
                break
        if len(loop) > 8:
            loops.append(loop)
    return loops


def simplify(points, epsilon):
    """Douglas-Peucker, which turns the pixel staircase into something readable at 24dp."""
    if len(points) < 3:
        return points
    ax, ay = points[0]
    bx, by = points[-1]
    dx, dy = bx - ax, by - ay
    norm = (dx * dx + dy * dy) ** 0.5
    worst, index = 0.0, 0
    for i in range(1, len(points) - 1):
        px, py = points[i]
        d = abs(dy * px - dx * py + bx * ay - by * ax) / norm if norm else ((px - ax) ** 2 + (py - ay) ** 2) ** 0.5
        if d > worst:
            worst, index = d, i
    if worst <= epsilon:
        return [points[0], points[-1]]
    return simplify(points[:index + 1], epsilon)[:-1] + simplify(points[index:], epsilon)


def area(loop):
    s = 0.0
    for i in range(len(loop) - 1):
        x0, y0 = loop[i]
        x1, y1 = loop[i + 1]
        s += x0 * y1 - x1 * y0
    return s / 2


def contains(loop, point):
    x, y = point
    inside = False
    for i in range(len(loop) - 1):
        x0, y0 = loop[i]
        x1, y1 = loop[i + 1]
        if (y0 > y) != (y1 > y):
            xx = x0 + (y - y0) * (x1 - x0) / (y1 - y0)
            if xx > x:
                inside = not inside
    return inside


def path_data(loops, scale):
    out = []
    for loop in loops:
        pts = [(round(x * scale, 1), round(y * scale, 1)) for x, y in loop]
        out.append("M" + f"{pts[0][0]},{pts[0][1]}" + "".join(f"L{x},{y}" for x, y in pts[1:]) + "Z")
    return "".join(out)


def main():
    src, out_prefix = sys.argv[1], sys.argv[2]
    viewport = 24.0
    w, h, bpp, rows = read_png(src)
    mask = ink_mask(w, h, bpp, rows)
    loops = [simplify(l, 1.2) for l in trace_loops(mask, w, h)]
    loops.sort(key=lambda l: -abs(area(l)))

    # Depth: how many other loops enclose this one. 0 is the outer edge of the body stroke, 1 its
    # inner edge, 2 the outer edge of a detail drawn inside the body, and so on.
    depths = []
    for i, loop in enumerate(loops):
        sample = loop[len(loop) // 3]
        depths.append(sum(1 for j, other in enumerate(loops) if j != i and contains(other, sample)))

    scale = viewport / max(w, h)
    outline = [l for l in loops]
    filled = [l for l, d in zip(loops, depths) if d % 2 == 0]

    print(f"{len(loops)} loops, depths {sorted(set(depths))}, "
          f"{sum(len(l) for l in loops)} points after simplification")
    for name, selected in (("outline", outline), ("filled", filled)):
        data = path_data(selected, scale)
        xml = (f'<?xml version="1.0" encoding="utf-8"?>\n'
               f'<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
               f'\tandroid:width="24dp"\n\tandroid:height="24dp"\n'
               f'\tandroid:viewportWidth="24"\n\tandroid:viewportHeight="24">\n'
               f'\t<path\n\t\tandroid:fillColor="@android:color/white"\n'
               f'\t\tandroid:fillType="evenOdd"\n\t\tandroid:pathData="{data}"/>\n'
               f'</vector>\n')
        open(f"{out_prefix}-{name}.xml", "w").write(xml)
        print(f"  {name}: {len(selected)} loops, {len(data)} chars of path data")


if __name__ == "__main__":
    main()
