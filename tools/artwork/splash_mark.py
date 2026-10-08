#!/usr/bin/env python3
"""Crops the 5BFP lockup down to just the mark, for the welcome screen.

The welcome screen used to show the full lockup: the crowned mastodon and the words "5BFP". The
words are set in type now, as "Masto NYC" with a byline under it, so the image is only the mark.

Each density is cropped on its own rather than resampled from one source, so nothing is softened.

Which pixels belong to the mark is decided by connected components, not by a column cut. A cut
has to be placed somewhere, and anywhere that keeps the whole raised trunk also keeps a sliver of
the "5", because the two overlap horizontally. Grouping the ink and keeping the groups that sit
left of the gap takes the mark exactly, whatever it overlaps.

  python3 tools/artwork/splash_mark.py

Reads  tools/artwork/splash-logo/<density>.png, the full lockup, which lives here rather than in
       res/ so it is kept without being shipped in the APK
Writes mastodon/src/main/res/drawable-<density>/splash_mark_5bfp.png
"""
import glob
import struct
import zlib

SEARCH = (0.40, 0.54)   # where to look for the gap between mark and type, as a fraction of width
PAD = 6                 # px of clear space kept around the mark, at xhdpi scale


def read_png(path):
    data = open(path, "rb").read()
    pos, idat, w, h, bpp = 8, b"", 0, 0, 0
    while pos < len(data):
        length, = struct.unpack(">I", data[pos:pos + 4])
        kind = data[pos + 4:pos + 8]
        chunk = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if kind == b"IHDR":
            w, h, depth, color = struct.unpack(">IIBB", chunk[:10])
            if depth != 8 or color != 6:
                raise SystemExit(f"{path}: expected 8-bit RGBA")
            bpp = 4
        elif kind == b"IDAT":
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
    return w, h, rows


def write_png(path, w, h, rows):
    def chunk(tag, body):
        return struct.pack(">I", len(body)) + tag + body + struct.pack(">I", zlib.crc32(tag + body) & 0xFFFFFFFF)
    raw = b"".join(b"\x00" + r for r in rows)
    open(path, "wb").write(
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(raw, 9))
        + chunk(b"IEND", b""))


def components(w, h, rows):
    """Groups of touching ink, 8-connected, as (minx, miny, maxx, maxy, size)."""
    seen = bytearray(w * h)
    out = []
    for sy in range(h):
        for sx in range(w):
            if seen[sy * w + sx] or rows[sy][sx * 4 + 3] <= 16:
                continue
            stack = [(sx, sy)]
            seen[sy * w + sx] = 1
            minx = maxx = sx
            miny = maxy = sy
            size = 0
            while stack:
                x, y = stack.pop()
                size += 1
                if x < minx: minx = x
                if x > maxx: maxx = x
                if y < miny: miny = y
                if y > maxy: maxy = y
                for dx in (-1, 0, 1):
                    for dy in (-1, 0, 1):
                        nx, ny = x + dx, y + dy
                        if 0 <= nx < w and 0 <= ny < h and not seen[ny * w + nx] \
                                and rows[ny][nx * 4 + 3] > 16:
                            seen[ny * w + nx] = 1
                            stack.append((nx, ny))
            out.append((minx, miny, maxx, maxy, size))
    return out


def main():
    for src in sorted(glob.glob("tools/artwork/splash-logo/*.png")):
        density = src.rsplit("/", 1)[1].removesuffix(".png")
        w, h, rows = read_png(src)
        ink = [sum(1 for y in range(h) if rows[y][x * 4 + 3] > 16) for x in range(w)]
        lo, hi = int(w * SEARCH[0]), int(w * SEARCH[1])
        gap = min(range(lo, hi), key=lambda x: ink[x])

        # Keep every group that starts left of the gap: the body, the crown, the trunk, the eye.
        # The letters all start to the right of it.
        kept = [c for c in components(w, h, rows) if c[0] < gap]
        if not kept:
            raise SystemExit(f"{src}: found no ink left of x={gap}")
        pad = max(1, round(PAD * w / 600))
        x0 = max(0, min(c[0] for c in kept) - pad)
        x1 = min(w, max(c[2] for c in kept) + 1 + pad)
        y0 = max(0, min(c[1] for c in kept) - pad)
        y1 = min(h, max(c[3] for c in kept) + 1 + pad)

        # Anything to the right of the gap is type; drop it rather than carry a sliver of the 5.
        blank = bytes(4)
        cropped = []
        for y in range(y0, y1):
            row = bytearray(rows[y][x0 * 4:x1 * 4])
            for x in range(x0, x1):
                if x >= gap:
                    row[(x - x0) * 4:(x - x0) * 4 + 4] = blank
            cropped.append(bytes(row))
        out = f"mastodon/src/main/res/drawable-{density}/splash_mark_5bfp.png"
        write_png(out, x1 - x0, y1 - y0, cropped)
        print(f"{out}: {w}x{h} -> {x1 - x0}x{y1 - y0} "
              f"({len(kept)} of {len(components(w, h, rows))} ink groups, gap at {gap / w:.1%})")


if __name__ == "__main__":
    main()
