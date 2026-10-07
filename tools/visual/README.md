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

Only a region of each screenshot, named in the script. A full-screen comparison would fail on
every new post in the timeline. Full screenshots land in `build/visual-check/` for a human to look
at, along with a diff mask on failure.

Eight screens, each walked twice, in light and dark:

| golden | what it pins |
| --- | --- |
| `neighbors-status-bar` | the tab the app opens on, whose toolbar paints the strip behind the status bar itself |
| `home-status-bar` | the same for the Home tab |
| `fork-tab-icons` | the pigeon and buildings icons, and the selected-tab treatment |
| `tab-bar-landscape` | the bar rotated, where the side insets have to keep it off the edges |
| `followed-hashtags` | the accordion header on the hashtags tab, open |
| `profile-tab-menu` | the sheet behind a long press on the profile tab, the only way into settings |
| `qr-dialog-status-bar` | a real dialog window, which gets its transparent bars from a theme rather than from code |
| `selection-mode-status-bar` | action mode, where the status bar used to be tinted by the window |

Choosing those crops is most of the work, and the rule is that **a golden must not contain
anything the server decides**. Three of these were recorded, failed on the very next run, and had
to be re-cropped:

- `profile-tab-menu` was tall enough to catch the timeline behind the sheet, and a post arrived
  mid-run.
- `fork-tab-icons` reached far enough along the bar to include the notifications badge.
- `followed-hashtags` reached into upstream's trending list, where every row carries a live
  "N people are talking".

There is no landscape golden of the QR dialog, which is the screen the orientation work was
actually about, because its particle animation never settles and no strip of it is reproducible.
`QrCodeLayoutTest` measures that screen in landscape instead, where nothing is animating.

Always run without `--record` after recording. Goldens that cannot reproduce themselves on the
very next run are worse than no goldens at all.

Goldens are per API level, under `goldens/api<N>/`, because the platform draws the bars
differently across versions: that difference is the point. They were recorded at 1080x2400 on
`pixel_9` (API 35) and `pixel_7` (API 34) AVDs with Google APIs images. A different screen size
means re-recording.

`api34/` still holds only the three screens from before the tab redesign, so a run against an
API 34 device will report missing goldens for the rest and fail. That is the honest state: they
need recording on an API 34 device, which is the only way to get them right.

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
