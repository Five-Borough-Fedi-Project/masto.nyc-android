# Artwork

## The pigeon

**Not currently used in the app.** The Neighbors tab was a pigeon for a while and is now
`location_city`: a New Yorker already lives in a building, so the community the tab stands for is
buildings of buildings, against Home's single apartment block. The source drawing and the pipeline
that turns it into an icon are kept here because the drawing is ours and the pipeline works; the
generated drawables were deleted rather than left in `res/` unreferenced. Running the command
below brings them back.

`pigeon-source.png` is Five Borough Fedi Project's pigeon drawing, and the icon is derived from
it:

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


## Material Symbols

`svg2vd.py` turns a Material Symbols SVG into Android vector drawable path data. Google ships them
on a 960 grid with `viewBox="0 -960 960 960"`, so y runs from -960 to 0 and every coordinate needs
shifting by 960; the script does that and emits absolute `M`/`L`/`Z`, which is what the icons
already in `res/drawable/` look like.

```bash
curl -s "https://fonts.gstatic.com/s/i/short-term/release/materialsymbolsoutlined/apartment/default/24px.svg" -o /tmp/apartment.svg
python3 tools/artwork/svg2vd.py /tmp/apartment.svg
```

`--drawable` writes a whole vector drawable instead, keeping Google's path data exactly as
written and moving it onto Android's viewport with a translated group. Use that for anything
curved: every park and tree icon Material has is curved, and the flattening path refuses them.
The flattened form exists because `rasterize.py` can draw it, which is how the building icons
here were checked; a curved icon has to be rendered through Android instead.

Flattening only handles straight-line commands, and says so rather than guessing if an icon needs
curves.
To check it, run it over an icon already committed here and compare: converting `location_city`
reproduces `ic_location_city_24px.xml`'s path data exactly, which is how this script was verified
before it was used for `apartment`.

Note that the `fill1` segment of that URL is ignored by the server. For an icon that genuinely has
a filled variant, fetch it from the `google/material-design-icons` repository instead, and compare
the two before assuming they differ: `apartment` and `location_city` are identical in both.
