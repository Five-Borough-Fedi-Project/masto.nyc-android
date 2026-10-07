#!/usr/bin/env python3
"""Turns the 5BFP pigeon drawing into a tab icon pair at Material's weight.

Tracing the drawing as-is gives strokes about 2.7% of the canvas, which at 24dp is a third of the
weight Material icons use, and it disappears in the tab bar. So this derives two masks from the
artwork instead:

  unselected: the body as an outline of Material weight, plus the eyes and heart beak
  selected:   the body filled solid, with the eyes and heart knocked out

Morphology is done with a chamfer distance transform, which makes "grow by n pixels" and "shrink
by n pixels" one threshold each.
"""
import sys

sys.setrecursionlimit(20000)
import os
exec(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "trace.py")).read().split("def main()")[0])

INF = 10 ** 9


def distance(mask, w, h, inside=True):
    """Chamfer distance to the nearest pixel where mask == inside."""
    d = [[0 if mask[y][x] == inside else INF for x in range(w)] for y in range(h)]
    for y in range(h):
        for x in range(w):
            best = d[y][x]
            if y: best = min(best, d[y - 1][x] + 5)
            if x: best = min(best, d[y][x - 1] + 5)
            if y and x: best = min(best, d[y - 1][x - 1] + 7)
            if y and x + 1 < w: best = min(best, d[y - 1][x + 1] + 7)
            d[y][x] = best
    for y in range(h - 1, -1, -1):
        for x in range(w - 1, -1, -1):
            best = d[y][x]
            if y + 1 < h: best = min(best, d[y + 1][x] + 5)
            if x + 1 < w: best = min(best, d[y][x + 1] + 5)
            if y + 1 < h and x + 1 < w: best = min(best, d[y + 1][x + 1] + 7)
            if y + 1 < h and x: best = min(best, d[y + 1][x - 1] + 7)
            d[y][x] = best
    return d


def grow(mask, w, h, r):
    d = distance(mask, w, h, True)
    return [[d[y][x] <= r * 5 for x in range(w)] for y in range(h)]


def shrink(mask, w, h, r):
    d = distance(mask, w, h, False)
    return [[d[y][x] > r * 5 for x in range(w)] for y in range(h)]


def flood_background(mask, w, h):
    """Everything reachable from the border without crossing the mask."""
    seen = [[False] * w for _ in range(h)]
    stack = [(x, 0) for x in range(w)] + [(x, h - 1) for x in range(w)] \
            + [(0, y) for y in range(h)] + [(w - 1, y) for y in range(h)]
    while stack:
        x, y = stack.pop()
        if not (0 <= x < w and 0 <= y < h) or seen[y][x] or mask[y][x]:
            continue
        seen[y][x] = True
        stack += [(x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)]
    return seen


def to_vector(mask, w, h, path):
    loops = [simplify(l, 1.1) for l in trace_loops(mask, w, h)]
    scale = 24.0 / max(w, h)
    data = path_data(loops, scale)
    open(path, "w").write(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<!-- masto.nyc fork: the Neighbors tab. Derived from Five Borough Fedi Project pigeon\n'
        '     artwork by tools/artwork/pigeonize.py, which rebuilds it at the weight Material tab\n'
        '     icons use; tracing the drawing directly leaves strokes too thin to read at 24dp. -->\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '\tandroid:width="24dp"\n\tandroid:height="24dp"\n'
        '\tandroid:viewportWidth="24"\n\tandroid:viewportHeight="24">\n'
        '\t<path\n\t\tandroid:fillColor="@android:color/white"\n'
        f'\t\tandroid:fillType="evenOdd"\n\t\tandroid:pathData="{data}"/>\n'
        '</vector>\n')
    return len(loops), len(data)


def main():
    src, out_dir = sys.argv[1], sys.argv[2]
    w, h, bpp, rows = read_png(src)
    ink = ink_mask(w, h, bpp, rows)
    unit = max(w, h) / 24.0          # one dp in source pixels
    stroke = round(1.9 * unit)       # Material tab icons sit near 2dp

    # The drawing's outline has a gap at the crown, so close it before deciding what is "inside".
    closed = grow(ink, w, h, round(0.6 * unit))
    outside = flood_background(closed, w, h)
    body = [[closed[y][x] or not outside[y][x] for x in range(w)] for y in range(h)]
    body = shrink(body, w, h, round(0.6 * unit))   # undo the closing

    # Face details: ink well inside the body, above the chest lines, which are clutter at 24dp.
    # The heart is drawn as an outline; filling it in reads far better small than a thickened
    # stroke does, which just closes up into a blob.
    deep = shrink(body, w, h, round(1.2 * unit))
    cutoff = int(h * 0.62)
    details = [[ink[y][x] and deep[y][x] and y < cutoff for x in range(w)] for y in range(h)]
    enclosed = flood_background(details, w, h)
    details = [[details[y][x] or not enclosed[y][x] for x in range(w)] for y in range(h)]
    details = grow(details, w, h, round(0.15 * unit))

    inner = shrink(body, w, h, stroke)
    outline_ring = [[body[y][x] and not inner[y][x] for x in range(w)] for y in range(h)]
    unselected = [[outline_ring[y][x] or details[y][x] for x in range(w)] for y in range(h)]
    selected = [[body[y][x] and not details[y][x] for x in range(w)] for y in range(h)]

    for name, mask in (("ic_pigeon_24px.xml", unselected), ("ic_pigeon_fill1_24px.xml", selected)):
        loops, chars = to_vector(mask, w, h, f"{out_dir}/{name}")
        print(f"{name}: {loops} loops, {chars} chars")


if __name__ == "__main__":
    main()
