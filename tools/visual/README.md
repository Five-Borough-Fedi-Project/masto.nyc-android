# Emulator visual checks

`tools/visual-check.sh` drives a running emulator and compares regions of the screen against the
goldens here. It exists because the Robolectric tests draw view trees only: system bars, dialog
windows and everything else the platform draws around the app are invisible to them, and that is
where this fork's changes land.

```bash
tools/visual-check.sh            # check
tools/visual-check.sh --record   # re-record, then look at the diff before committing
```

It needs an emulator already running and signed in, because every screen worth checking is behind
a login. It never clears app data.

## What it compares

Only a region of each screenshot, named in the script: mostly the status bar strip. A full-screen
comparison would fail on every new post in the timeline. Full screenshots land in
`build/visual-check/` for a human to look at, along with a diff mask on failure.

Goldens are per API level, under `goldens/api<N>/`, because the platform draws the bars
differently across versions: that difference is the point. They were recorded at 1080x2400 on
`pixel_9` (API 35) and `pixel_7` (API 34) AVDs with Google APIs images. A different screen size
means re-recording.

Determinism comes from SystemUI demo mode, which pins the clock and the status bar icons, and from
disabling animation scales. Both are set by the script and undone at the end.

## Worked example

This catches the action mode regression found while removing the deprecated bar color APIs: on
API 34, `main` tints the status bar during selection mode and the branch leaves it black.

```
0.00% of pixels differ (budget 0.10%)
  ok   home-status-bar
51.46% of pixels differ (budget 0.10%)
  FAIL selection-mode-status-bar
```
