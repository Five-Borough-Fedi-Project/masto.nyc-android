# Artwork

## The pigeon

`pigeon-source.png` is Five Borough Fedi Project's pigeon drawing, and the Neighbors tab icon is
derived from it:

```bash
python3 tools/artwork/pigeonize.py tools/artwork/pigeon-source.png mastodon/src/main/res/drawable
```

That writes `ic_pigeon_24px.xml` (unselected) and `ic_pigeon_fill1_24px.xml` (selected).

Tracing the drawing directly doesn't work at tab size. Its strokes are about 2.7% of the canvas,
where Material's tab icons sit near 8%, so a straight trace renders as a faint smudge at 24dp. The
script rebuilds the artwork at that weight instead:

- closes the gap at the crown, floods from the border, and takes what's left as the body
- **unselected**: the body as a 2dp outline, plus the eyes and the heart beak
- **selected**: the body filled, with the eyes and heart knocked out of it
- the heart is drawn as an outline in the source and is filled in here, because a thickened
  outline closes into a blob at this size
- the chest lines are dropped; they're clutter at 24dp

`trace.py` does the PNG decoding and contour following, and is imported by the above. Both are
stdlib only, since this machine had no Pillow, potrace or ImageMagick.

To check a change without a device:

```bash
python3 tools/artwork/rasterize.py mastodon/src/main/res/drawable/ic_pigeon_24px.xml
```

which renders the path data back to PNGs at 96px and 24px to look at.
