#!/usr/bin/env python3
"""Rebuilds the launcher icon layers so no mask clips the art.

An adaptive icon is a 108dp canvas of which only the inner 66dp *circle* is guaranteed to survive
whatever mask the launcher applies. The art here was sized to fill 66dp as a *square*, which is
not the same thing: the corners of that square fall outside the circle, and the corners are where
the crown's left spike and the raised trunk are. On a round mask both were cut off, along with
the bottom of the body. FORK.md claimed the opposite; a screenshot of the launcher settled it.

So the art is scaled until its bounding box's diagonal fits the safe circle, then centred. That
is the only size that is safe for every mask rather than for the one you happened to test.

Resampled from the largest existing layer with a box filter, on premultiplied alpha so the edges
don't fringe. Run it, then look at the result on a launcher.

  python3 tools/artwork/launcher_icon.py
"""
import math
import struct
import zlib

SOURCE_DIR = "mastodon/src/main/res"
# The colour layer goes to res/ at every density. The silhouette is only ever traced into a
# vector by launcher_mono.py, so one copy of it lives here instead of five in res/.
COLOUR = "ic_launcher_elephant"
MONO_SOURCE = "tools/artwork/launcher-mono.png"
DENSITIES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
SAFE_FRACTION = 66.0 / 108.0   # diameter of the guaranteed-visible circle


def read_png(path):
    data = open(path, "rb").read()
    pos, idat, w, h = 8, b"", 0, 0
    while pos < len(data):
        length, = struct.unpack(">I", data[pos:pos + 4])
        kind = data[pos + 4:pos + 8]
        chunk = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if kind == b"IHDR":
            w, h, depth, color = struct.unpack(">IIBB", chunk[:10])
            if depth != 8 or color != 6:
                raise SystemExit(f"{path}: expected 8-bit RGBA")
        elif kind == b"IDAT":
            idat += chunk
    raw = zlib.decompress(idat)
    rows, prev, i = [], bytearray(w * 4), 0
    for _ in range(h):
        f = raw[i]
        line = bytearray(raw[i + 1:i + 1 + w * 4])
        i += 1 + w * 4
        for x in range(len(line)):
            a = line[x - 4] if x >= 4 else 0
            b = prev[x]
            c = prev[x - 4] if x >= 4 else 0
            if f == 1: line[x] = (line[x] + a) & 255
            elif f == 2: line[x] = (line[x] + b) & 255
            elif f == 3: line[x] = (line[x] + (a + b) // 2) & 255
            elif f == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[x] = (line[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        rows.append(bytes(line))
        prev = line
    return w, h, rows


def write_png(path, w, h, rows):
    def chunk(tag, body):
        return struct.pack(">I", len(body)) + tag + body \
            + struct.pack(">I", zlib.crc32(tag + body) & 0xFFFFFFFF)
    raw = b"".join(b"\x00" + bytes(r) for r in rows)
    open(path, "wb").write(
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(raw, 9))
        + chunk(b"IEND", b""))


def bbox(w, h, rows):
    xs, ys = [], []
    for y in range(h):
        row = rows[y]
        for x in range(w):
            if row[x * 4 + 3] > 8:
                xs.append(x)
                ys.append(y)
    return min(xs), min(ys), max(xs) + 1, max(ys) + 1


def resample(src_w, src_h, rows, box, out_art, out_canvas):
    """Box-filters the art inside `box` down to out_art px, centred on an out_canvas square."""
    x0, y0, x1, y1 = box
    bw, bh = x1 - x0, y1 - y0
    scale = out_art / max(bw, bh)
    aw, ah = max(1, round(bw * scale)), max(1, round(bh * scale))
    ox, oy = (out_canvas - aw) // 2, (out_canvas - ah) // 2
    out = [bytearray(out_canvas * 4) for _ in range(out_canvas)]
    for ty in range(ah):
        sy0 = y0 + ty * bh / ah
        sy1 = y0 + (ty + 1) * bh / ah
        for tx in range(aw):
            sx0 = x0 + tx * bw / aw
            sx1 = x0 + (tx + 1) * bw / aw
            r = g = b = a = n = 0.0
            for sy in range(int(sy0), max(int(sy0) + 1, math.ceil(sy1))):
                if sy >= src_h: break
                row = rows[sy]
                for sx in range(int(sx0), max(int(sx0) + 1, math.ceil(sx1))):
                    if sx >= src_w: break
                    px = row[sx * 4:sx * 4 + 4]
                    al = px[3] / 255.0
                    r += px[0] * al; g += px[1] * al; b += px[2] * al; a += al
                    n += 1
            if n == 0:
                continue
            al = a / n
            dst = out[oy + ty]
            i = (ox + tx) * 4
            if al > 0:
                dst[i] = min(255, round(r / a)); dst[i + 1] = min(255, round(g / a))
                dst[i + 2] = min(255, round(b / a)); dst[i + 3] = round(al * 255)
    return out


def silhouette(w, h, rows):
    """Black wherever the art is opaque. The themed layer is the colour one's own shape."""
    out = []
    for y in range(h):
        line = bytearray(w * 4)
        for x in range(w):
            line[x * 4 + 3] = rows[y][x * 4 + 3]
        out.append(line)
    return out


def main():
    src = f"{SOURCE_DIR}/drawable-xxxhdpi/{COLOUR}.png"
    w, h, rows = read_png(src)
    box = bbox(w, h, rows)
    bw, bh = box[2] - box[0], box[3] - box[1]
    diagonal = math.hypot(bw, bh)
    # The longest side, once the diagonal is made to fit the safe circle.
    target_long = max(bw, bh) * (SAFE_FRACTION * w / diagonal)
    print(f"art {bw}x{bh} of {w} (diagonal {diagonal / w:.0%} of canvas) "
          f"-> longest side {target_long / w:.0%}")
    for density, size in DENSITIES.items():
        out = resample(w, h, rows, box, target_long * size / w, size)
        path = f"{SOURCE_DIR}/drawable-{density}/{COLOUR}.png"
        write_png(path, size, size, out)
        print(f"   {path}: {size}x{size}")

    full = resample(w, h, rows, box, target_long, w)
    write_png(MONO_SOURCE, w, w, silhouette(w, w, full))
    print(f"   {MONO_SOURCE}: {w}x{w} (traced into a vector by launcher_mono.py)")


if __name__ == "__main__":
    main()
