#!/usr/bin/env python3
"""Compares a PNG against a golden, optionally over one region, and writes a diff mask.

Used by tools/visual-check.sh. Pure stdlib: no Pillow on a fresh checkout.

  compare.py actual.png golden.png [--diff out.png] [--max-changed 0.001]
  compare.py --extract x,y,w,h in.png out.png

Exit 0 when they match, 1 when they differ, 2 when something is wrong with the inputs.
"""
import struct
import sys
import zlib

CHANNEL_TOLERANCE = 24


def read_png(path):
    data = open(path, 'rb').read()
    if data[:8] != b'\x89PNG\r\n\x1a\n':
        raise ValueError(f'{path} is not a PNG')
    pos, idat, width, height, bpp = 8, b'', 0, 0, 0
    while pos < len(data):
        length, = struct.unpack('>I', data[pos:pos + 4])
        kind = data[pos + 4:pos + 8]
        chunk = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if kind == b'IHDR':
            width, height, depth, color_type = struct.unpack('>IIBB', chunk[:10])
            if depth != 8 or color_type not in (2, 6):
                raise ValueError(f'{path}: only 8-bit RGB/RGBA PNGs are supported')
            bpp = 3 if color_type == 2 else 4
        elif kind == b'IDAT':
            idat += chunk
    raw = zlib.decompress(idat)
    rows, prev, i = [], bytearray(width * bpp), 0
    for _ in range(height):
        filter_type = raw[i]
        line = bytearray(raw[i + 1:i + 1 + width * bpp])
        i += 1 + width * bpp
        for x in range(len(line)):
            left = line[x - bpp] if x >= bpp else 0
            up = prev[x]
            up_left = prev[x - bpp] if x >= bpp else 0
            if filter_type == 1:
                line[x] = (line[x] + left) & 255
            elif filter_type == 2:
                line[x] = (line[x] + up) & 255
            elif filter_type == 3:
                line[x] = (line[x] + (left + up) // 2) & 255
            elif filter_type == 4:
                p = left + up - up_left
                pa, pb, pc = abs(p - left), abs(p - up), abs(p - up_left)
                line[x] = (line[x] + (left if pa <= pb and pa <= pc else up if pb <= pc else up_left)) & 255
        rows.append(bytes(line))
        prev = line
    return width, height, bpp, rows


def write_png(path, width, height, rows):
    raw = b''.join(b'\x00' + row for row in rows)
    def chunk(kind, body):
        c = struct.pack('>I', len(body)) + kind + body
        return c + struct.pack('>I', zlib.crc32(kind + body) & 0xffffffff)
    png = (b'\x89PNG\r\n\x1a\n'
           + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 2, 0, 0, 0))
           + chunk(b'IDAT', zlib.compress(raw))
           + chunk(b'IEND', b''))
    open(path, 'wb').write(png)


def extract(spec, src, dst):
    x0, y0, w, h = [int(v) for v in spec.split(',')]
    width, height, bpp, rows = read_png(src)
    x1, y1 = min(x0 + w, width), min(y0 + h, height)
    out = []
    for y in range(y0, y1):
        row = bytearray()
        for x in range(x0, x1):
            row += rows[y][x * bpp:x * bpp + 3]
        out.append(bytes(row))
    write_png(dst, x1 - x0, y1 - y0, out)
    return 0


def main():
    args = sys.argv[1:]
    if args and args[0] == '--extract':
        if len(args) != 4:
            print(__doc__)
            return 2
        return extract(args[1], args[2], args[3])
    if len(args) < 2:
        print(__doc__)
        return 2
    actual_path, golden_path = args[0], args[1]
    crop = diff_path = None
    max_changed = 0.001
    i = 2
    while i < len(args):
        if args[i] == '--crop':
            crop = [int(v) for v in args[i + 1].split(',')]
            i += 2
        elif args[i] == '--diff':
            diff_path = args[i + 1]
            i += 2
        elif args[i] == '--max-changed':
            max_changed = float(args[i + 1])
            i += 2
        else:
            print(f'unknown argument {args[i]}')
            return 2

    aw, ah, abpp, a = read_png(actual_path)
    gw, gh, gbpp, g = read_png(golden_path)
    if (aw, ah) != (gw, gh):
        print(f'size differs: {aw}x{ah} vs golden {gw}x{gh}')
        return 1

    x0, y0, w, h = crop if crop else (0, 0, aw, ah)
    x1, y1 = min(x0 + w, aw), min(y0 + h, ah)
    changed = 0
    diff_rows = [bytearray(b'\x00' * (aw * 3)) for _ in range(ah)] if diff_path else None
    for y in range(y0, y1):
        for x in range(x0, x1):
            pa = a[y][x * abpp:x * abpp + 3]
            pg = g[y][x * gbpp:x * gbpp + 3]
            if any(abs(pa[c] - pg[c]) > CHANNEL_TOLERANCE for c in range(3)):
                changed += 1
                if diff_rows:
                    diff_rows[y][x * 3:x * 3 + 3] = b'\xff\x00\x00'
            elif diff_rows:
                diff_rows[y][x * 3:x * 3 + 3] = bytes(pa)
    total = max(1, (x1 - x0) * (y1 - y0))
    fraction = changed / total
    if diff_rows and fraction > max_changed:
        write_png(diff_path, aw, ah, [bytes(r) for r in diff_rows])
    region = f' in {x1-x0}x{y1-y0} at {x0},{y0}' if crop else ''
    print(f'{fraction*100:.2f}% of pixels differ{region} (budget {max_changed*100:.2f}%)')
    return 1 if fraction > max_changed else 0


if __name__ == '__main__':
    sys.exit(main())
