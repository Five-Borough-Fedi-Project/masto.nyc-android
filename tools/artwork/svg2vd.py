#!/usr/bin/env python3
"""Material Symbols SVG -> Android vector drawable.

Material Symbols ship on a 960 grid with viewBox "0 -960 960 960", so y runs from -960 to 0 and
Android wants 0..960.

Two ways to deal with that. Flattening rewrites every coordinate, which only works for icons
drawn with straight lines; it is what the buildings in this repo use, and it produces path data
that tools/artwork/rasterize.py can also draw. Wrapping puts the original path, untouched, inside
a group translated by 960, which works for any icon including the ones with curves -- every park
and tree icon Material has is curved, and flattening refuses them.

  python3 tools/artwork/svg2vd.py icon.svg            # path data, straight lines only
  python3 tools/artwork/svg2vd.py --drawable icon.svg # a whole vector drawable, any icon
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

def drawable(svg_text, note=""):
    """A whole vector drawable, keeping the path exactly as Google wrote it."""
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            + (f'<!-- {note} -->\n' if note else '')
            + '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
              '\tandroid:width="24dp"\n\tandroid:height="24dp"\n'
              '\tandroid:viewportWidth="960"\n\tandroid:viewportHeight="960">\n'
              '\t<!-- Material ships this on a 0 -960 960 960 viewBox; the translate is what moves\n'
              '\t     it onto Android\'s 0..960 one, leaving the path data untouched. -->\n'
              '\t<group android:translateY="960">\n'
              '\t\t<path\n\t\t\tandroid:fillColor="@android:color/white"\n'
              f'\t\t\tandroid:pathData="{path_of(svg_text)}"/>\n'
              '\t</group>\n'
              '</vector>\n')


if __name__ == '__main__':
    args = sys.argv[1:]
    as_drawable = '--drawable' in args
    if as_drawable:
        args.remove('--drawable')
    text = open(args[0]).read()
    print(drawable(text) if as_drawable else convert(path_of(text)))
