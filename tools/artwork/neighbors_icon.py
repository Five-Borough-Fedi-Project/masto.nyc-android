#!/usr/bin/env python3
"""Traces the Neighbors tab icon from the drawing in this folder.

The Neighbors tab is the neighbourhood rather than a building: a New Yorker already lives in a
building, so Home is the block you live in and this is the skyline and the trees in front of it.
The drawing is Five Borough Fedi Project's.

It arrives as a 512px silhouette, which is traced rather than redrawn so the shape stays exactly
as given. Material's own icons sit on a 960 grid; this one is put on the same grid and inset to
Material's optical margin so it lines up with the buildings and the search glass next to it.

  python3 tools/artwork/neighbors_icon.py
  python3 tools/artwork/rasterize.py mastodon/src/main/res/drawable/ic_neighbors_park_24px.xml
"""
import sys

sys.setrecursionlimit(100000)
exec(open(__file__.replace("neighbors_icon.py", "trace.py")).read().split("def main()")[0])

SOURCE = "tools/artwork/neighbors-source.png"
TARGET = "mastodon/src/main/res/drawable/ic_neighbors_park_24px.xml"
VIEWPORT = 960.0
# Material draws inside 840 of its 960 grid, leaving 60 clear on each side. Matching that is what
# stops this reading as larger than the icons beside it.
CONTENT = 840.0
EPSILON = 1.1   # in source pixels, so about a fifth of a dp once scaled down


def main():
    w, h, bpp, rows = read_png(SOURCE)
    mask = ink_mask(w, h, bpp, rows)
    loops = [simplify(l, EPSILON) for l in trace_loops(mask, w, h)]
    if not loops:
        raise SystemExit(f"{SOURCE}: no ink found. Is it a dark drawing on a light background?")

    # Fit the drawing's own extent to the content box, rather than assuming it fills its canvas.
    xs = [p[0] for loop in loops for p in loop]
    ys = [p[1] for loop in loops for p in loop]
    scale = CONTENT / max(max(xs) - min(xs), max(ys) - min(ys))
    dx = (VIEWPORT - (max(xs) - min(xs)) * scale) / 2 - min(xs) * scale
    dy = (VIEWPORT - (max(ys) - min(ys)) * scale) / 2 - min(ys) * scale

    def fmt(v):
        return str(int(v)) if float(v).is_integer() else f"{v:.1f}"

    data = "".join(
        "M" + f"{fmt(loop[0][0] * scale + dx)},{fmt(loop[0][1] * scale + dy)}"
        + "".join(f"L{fmt(x * scale + dx)},{fmt(y * scale + dy)}" for x, y in loop[1:]) + "Z"
        for loop in loops)

    open(TARGET, "w").write(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<!-- masto.nyc fork: the Neighbors tab. Five Borough Fedi Project\'s drawing of the\n'
        '     skyline with trees in front of it, traced from tools/artwork/neighbors-source.png\n'
        '     by tools/artwork/neighbors_icon.py and fitted to the same\n'
        '     840-of-960 content box Material uses, so it sits at the weight of the icons beside\n'
        '     it. Re-run that rather than editing this by hand. -->\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '\tandroid:width="24dp"\n\tandroid:height="24dp"\n'
        '\tandroid:viewportWidth="960"\n\tandroid:viewportHeight="960">\n'
        '\t<path\n\t\tandroid:fillColor="@android:color/white"\n'
        f'\t\tandroid:fillType="evenOdd"\n\t\tandroid:pathData="{data}"/>\n'
        '</vector>\n')
    print(f"{TARGET}: {len(loops)} loops, {sum(len(l) for l in loops)} points, {len(data)} chars")


if __name__ == "__main__":
    main()
