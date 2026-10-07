#!/usr/bin/env python3
"""Renders the M/L/Z path data from a vector drawable to a PNG, so it can be looked at."""
import re, struct, sys, zlib

def parse(xml):
    data = re.search(r'android:pathData="([^"]+)"', xml).group(1)
    loops, cur = [], []
    for cmd, args in re.findall(r'([MLZ])([^MLZ]*)', data):
        if cmd == 'Z':
            if cur: loops.append(cur); cur = []
            continue
        for pair in args.strip().split('L') if cmd == 'L' else [args]:
            if not pair.strip(): continue
            x, y = pair.split(',')
            cur.append((float(x), float(y)))
    if cur: loops.append(cur)
    return loops

def render(loops, size, pad=1.0):
    scale = (size - 2 * pad) / 24.0
    px = [[255] * size for _ in range(size)]
    # even-odd scanline fill, 3x3 supersampled so edges don't look like a staircase
    ss = 3
    for py in range(size):
        for sy in range(ss):
            y = (py + (sy + 0.5) / ss - pad) / scale
            xs = []
            for loop in loops:
                for i in range(len(loop)):
                    x0, y0 = loop[i]
                    x1, y1 = loop[(i + 1) % len(loop)]
                    if (y0 > y) != (y1 > y):
                        xs.append(x0 + (y - y0) * (x1 - x0) / (y1 - y0))
            xs.sort()
            for i in range(0, len(xs) - 1, 2):
                a = int(xs[i] * scale + pad); b = int(xs[i + 1] * scale + pad)
                for x in range(max(0, a), min(size, b + 1)):
                    px[py][x] = max(0, px[py][x] - 255 // ss)
    return px

def write_png(path, px):
    h = len(px); w = len(px[0])
    raw = b''.join(b'\x00' + bytes(bytearray(row)) for row in px)
    def chunk(k, b):
        c = struct.pack('>I', len(b)) + k + b
        return c + struct.pack('>I', zlib.crc32(k + b) & 0xffffffff)
    open(path, 'wb').write(b'\x89PNG\r\n\x1a\n'
        + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 0, 0, 0, 0))
        + chunk(b'IDAT', zlib.compress(raw)) + chunk(b'IEND', b''))

for name in sys.argv[1:]:
    loops = parse(open(name).read())
    for size in (96, 24):
        out = name.replace('.xml', f'-{size}.png')
        write_png(out, render(loops, size))
        print(f"{out}: {len(loops)} loops")
