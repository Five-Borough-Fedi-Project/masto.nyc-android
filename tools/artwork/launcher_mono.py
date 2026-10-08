#!/usr/bin/env python3
"""Traces the themed-icon silhouette into a vector drawable.

The monochrome layer of an adaptive icon is what a launcher tints when themed icons are on. It
was a PNG here, which works but is not what the platform asks for: the layer is drawn at whatever
size the launcher wants, and some of them will not use a bitmap one at all. A vector scales, tints
cleanly and costs a couple of kilobytes.

Traced from the PNG that was there rather than redrawn, so the shape is unchanged.

  python3 tools/artwork/launcher_mono.py
  python3 tools/artwork/rasterize.py mastodon/src/main/res/drawable/ic_launcher_monochrome_path.xml
"""
import sys

sys.setrecursionlimit(50000)
exec(open(__file__.replace("launcher_mono.py", "trace.py")).read().split("def main()")[0])

SOURCE = "tools/artwork/launcher-mono.png"
TARGET = "mastodon/src/main/res/drawable/ic_launcher_monochrome_path.xml"
VIEWPORT = 108.0   # adaptive icon layers are 108dp square
EPSILON = 0.9      # in source pixels; the silhouette is 432px for 108dp, so this is ~0.2dp


def main():
    w, h, bpp, rows = read_png(SOURCE)
    mask = ink_mask(w, h, bpp, rows)
    loops = [simplify(l, EPSILON) for l in trace_loops(mask, w, h)]
    scale = VIEWPORT / max(w, h)
    data = path_data(loops, scale)
    open(TARGET, "w").write(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<!-- masto.nyc fork: the themed-icon silhouette, traced from\n'
        '     tools/artwork/launcher-mono.png by tools/artwork/launcher_mono.py.\n'
        '     A vector rather than that PNG because this layer is what a launcher tints for\n'
        '     themed icons, and it gets drawn at whatever size the launcher likes. Only the\n'
        '     alpha matters; the system supplies the colour. -->\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '\tandroid:width="108dp"\n\tandroid:height="108dp"\n'
        '\tandroid:viewportWidth="108"\n\tandroid:viewportHeight="108">\n'
        '\t<path\n\t\tandroid:fillColor="@android:color/white"\n'
        f'\t\tandroid:fillType="evenOdd"\n\t\tandroid:pathData="{data}"/>\n'
        '</vector>\n')
    print(f"{TARGET}: {len(loops)} loops, {sum(len(l) for l in loops)} points, {len(data)} chars")


if __name__ == "__main__":
    main()
