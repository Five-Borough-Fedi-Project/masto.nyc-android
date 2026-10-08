#!/usr/bin/env python3
"""Material Symbols SVG -> Android vector drawable path data.

Material Symbols ship on a 960 grid with viewBox "0 -960 960 960", so y runs from -960 to 0.
Android wants a 0..960 viewport, which means every y shifted by +960. Only absolute commands
need it; relative ones are deltas. Everything is emitted as absolute M/L/Z, which is what the
icons already in this repo look like.
"""
import re, sys

def parse(d):
    tokens = re.findall(r'([MmLlHhVvZz])|(-?\d*\.?\d+)', d)
    out, cmd = [], None
    nums = []
    def flush():
        nonlocal nums
        if cmd: out.append((cmd, nums))
        nums = []
    for letter, num in tokens:
        if letter:
            flush()
            cmd_local = letter
            out.append if False else None
            nums = []
            globals()['_'] = None
            yield_cmd = cmd_local
            # start a new command group
            out.append((yield_cmd, nums))
        else:
            if out: out[-1][1].append(float(num))
    return out

def convert(d, dy=960.0):
    cmds = parse(d)
    x = y = 0.0
    sx = sy = 0.0
    parts = []
    def fmt(v):
        return str(int(v)) if float(v).is_integer() else f'{v:g}'
    for cmd, args in cmds:
        if cmd in 'Mm':
            for i in range(0, len(args), 2):
                px, py = args[i], args[i+1]
                if cmd == 'M': x, y = px, py
                else: x, y = x+px, y+py
                if i == 0:
                    sx, sy = x, y
                    parts.append(f'M{fmt(x)},{fmt(y+dy)}')
                else:
                    parts.append(f'L{fmt(x)},{fmt(y+dy)}')
        elif cmd in 'Ll':
            for i in range(0, len(args), 2):
                px, py = args[i], args[i+1]
                if cmd == 'L': x, y = px, py
                else: x, y = x+px, y+py
                parts.append(f'L{fmt(x)},{fmt(y+dy)}')
        elif cmd in 'Hh':
            for px in args:
                x = px if cmd == 'H' else x+px
                parts.append(f'L{fmt(x)},{fmt(y+dy)}')
        elif cmd in 'Vv':
            for py in args:
                y = py if cmd == 'V' else y+py
                parts.append(f'L{fmt(x)},{fmt(y+dy)}')
        elif cmd in 'Zz':
            parts.append('Z')
            x, y = sx, sy
        else:
            raise SystemExit(f'unsupported path command {cmd!r}; this icon needs curves')
    return ''.join(parts)

def path_of(svg_text):
    m = re.search(r'\sd="([^"]+)"', svg_text)
    if not m: raise SystemExit('no path in svg')
    return m.group(1)

if __name__ == '__main__':
    print(convert(path_of(open(sys.argv[1]).read())))
