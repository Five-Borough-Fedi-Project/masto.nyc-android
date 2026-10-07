# Fork notes

A fork of [mastodon/mastodon-android](https://github.com/mastodon/mastodon-android), rebranded as
Masto NYC and locked to masto.nyc.

The point of everything below is to keep pulling upstream cheap. The fewer upstream lines we touch,
the less there is to reconcile.

## Conventions

Fork-specific values belong in
[`ForkConfig.java`](mastodon/src/main/java/org/joinmastodon/android/fork/ForkConfig.java) rather
than scattered through upstream files. It holds the server domain and anything else particular to
us, and because it's a new file in a new package it can never conflict. When an upstream file needs
one of those values, import it and read it from there, which keeps the edit down to a line or two.

Genuinely new strings go in `mastodon/src/main/res/values/strings_nyc.xml`, which you'll have to
create the first time. Don't append them to upstream's `strings.xml`. Overrides of existing upstream
strings do have to be edited in place, because Android won't allow the same resource name in two
files in one source set.

Don't delete upstream files. `InstanceChooserLoginFragment`, `InstanceCatalogSignupFragment` and
`InstanceCatalogFragment` are unreachable now, but they stay, because deleting a file that upstream
still maintains turns every future change to it into a modify/delete conflict. Carrying some dead
code is easier to live with. The same goes for the `intro_bottom_sheet` layout and the `welcome_*`,
`pick_server` and `learn_more` strings.

Where an edit to an upstream file isn't self-explanatory, mark it with a `// masto.nyc fork:`
comment so it's clear during a merge that the line is ours on purpose.

## Merging upstream

```bash
git remote add upstream https://github.com/mastodon/mastodon-android
git fetch upstream
git merge upstream/master
```

Conflicts should be confined to the files below.

## Upstream files this fork touches

| File | Change |
| --- | --- |
| `mastodon/build.gradle` | one line at the end: `apply from: 'fork.gradle'` |
| `build.gradle`, `gradle/wrapper/gradle-wrapper.properties` | AGP 9.4.0 and Gradle 9.7.1, ahead of upstream's 8.13 |
| `gradle.properties` | opts out of AGP 9's built-in Kotlin and tested-build-type-only unit tests |
| `mastodon/src/main/AndroidManifest.xml` | deep links point at `masto.nyc` |
| `mastodon/src/main/res/values/strings.xml` | `app_name`, `settings_contribute`, `settings_app_version`, `local_timeline_info_banner` |
| `mastodon/src/main/res/values/urls.xml` | `github_url`, `privacy_policy_url` |
| `mastodon/src/main/res/layout/fragment_splash.xml` | dropped the server picker and the "Learn more" sheet |
| `.../fragments/profile/ProfileQrCodeFragment.java`, `res/layout/fragment_profile_qr.xml` | no portrait lock; the code is sized to fit landscape |
| `.../fragments/HomeFragment.java`, `.../settings/SettingsMainFragment.java`, `.../onboarding/AccountActivationFragment.java` | one account per install, see [below](#one-account-per-install) |
| `.../fragments/HomeTimelineFragment.java` | the toolbar gear only appears when an update is ready, and the dropdown is gone, see [below](#neighbors-is-its-own-tab) |
| `res/layout/tab_bar.xml`, `.../fragments/settings/SettingsBehaviorFragment.java` | the Neighbors tab and which tab opens, see [below](#neighbors-is-its-own-tab) |
| `.../discover/DiscoverFragment.java`, `.../discover/TrendingHashtagsFragment.java`, `res/values/ids.xml` | lists and followed hashtags, see [below](#lists-and-followed-hashtags-live-on-the-search-screen) |
| `.../fragments/SplashFragment.java` | server is fixed; log in goes straight to OAuth; no catalog request |
| `.../fragments/onboarding/GoogleMadeMeAddThisFragment.java` | privacy policy item points at ours |
| `.../api/requests/oauth/CreateOAuthApp.java` | OAuth client name and website |
| `.../api/MastodonAPIController.java`, `.../MastodonApp.java` | `User-Agent`, see [below](#user-agent) |
| `.../updater/GithubSelfUpdaterImpl.java` | self-update from this repo, not upstream's |
| `res/drawable/splash_logo.xml`, `res/drawable/ic_ntf_logo.xml` | replaced artwork |
| `res/drawable-anydpi-v26/ic_launcher_{foreground,background,monochrome}.xml` | replaced artwork |
| `res/mipmap-*/ic_launcher.png` | replaced artwork |
| `README.md`, `fastlane/metadata/android/en-US/*` | store listing and repo docs |
| `settings.gradle` | one line at the end: `apply from: 'fork-settings.gradle'` |
| `gradle.properties` | `android.nonTransitiveRClass=false`, see [below](#patched-appkit) |
| `res/values/styles.xml`, `.../ui/utils/ActionModeHelper.java`, `.../utils/ElevationOnScrollListener.java`, `.../ui/photoviewer/{PhotoViewer,AvatarCropper}.java`, `.../fragments/{ListMembers,CreateListAddMembers}Fragment.java`, `.../settings/FilterWordsFragment.java`, `.../profile/ProfileQrCodeFragment.java` | system bar colors without deprecated APIs, see [below](#patched-appkit) |

These are new files, so they can never conflict: `ForkConfig.java`, `ci_version.gradle`,
`FORK.md`, `deploy/`, `fork-settings.gradle`, `third_party/`, and the
generated artwork under `res/drawable-*dpi/`.

Changing `applicationId` also moves the OAuth callback scheme (`${applicationId}-auth://callback`)
and the FileProvider authority, so neither needs editing. The built APK confirms it: the manifest
ends up with `nyc.masto.android-auth`.

### Why the build config lives in fork.gradle

`applicationId` and `compileSdkMinor` sit in `mastodon/fork.gradle`, applied by a single line at the
bottom of `build.gradle`. This is not tidiness. Upstream bumps `versionCode` and `versionName` in
`defaultConfig` on every release, and git's three lines of context mean anything we add near them
conflicts every time. Merging v2.13.2 onto v2.13.1 plus our delta was measured conflicting on
exactly that, on `compileSdkMinor 0`, four lines above the version bump. At 23 upstream releases a
year that is 23 guaranteed conflicts. With the config moved, the same merge is clean.

Anything else we ever need to change about the build belongs in `fork.gradle` for the same reason.

### Why `compileSdkMinor 0` is there

This is a build fix rather than a branding change, and upstream hits it too on a fresh SDK. Google
no longer
publishes a bare `platforms;android-37`. Every API 37 platform is minor-versioned (`android-37.0`,
`android-37.1`, plus `37.2` betas) and `source.properties` reports `AndroidVersion.ApiLevel=37.0`,
so upstream's bare `compileSdk 37` fails with:

    Failed to find target with hash string 'android-37' in: <sdk>

AGP (8.13.2 at the time) supports `compileSdkMinor` even though upstream doesn't use it, so adding
`compileSdkMinor 0` resolves the platform to `android-37.0`. Drop the line if a future upstream
merge fixes this another way.

### User-Agent

Upstream sends `MastodonAndroid/<versionName>`, which would make this fork indistinguishable from
the official app in anyone's server logs. We send `MastoNYCAndroid/<versionName>`, from
`ForkConfig.USER_AGENT_PRODUCT`.

Two places set it, and it's easy to catch only one, because a case-sensitive grep for `userAgent`
misses `NetworkUtils.setUserAgent`:

- `MastodonAPIController.submitRequest` covers every Mastodon API call
- `MastodonApp.onCreate` covers appkit's image and media fetching

The `Android` suffix keeps the platform visible to admins, and anything already matching
`*Android/*` keeps working. No device model or OS version, because upstream sends none and it would
just hand every instance a fingerprinting signal.

Worth re-checking after an upstream merge that adds new HTTP clients:

```bash
for d in $(unzip -l app.apk | grep -oE "classes[0-9]*\.dex"); do
  unzip -p app.apk $d | strings | grep -c MastodonAndroid
done
```

### Lists and followed hashtags live on the search screen

Both were in the home toolbar's dropdown that [the Neighbors tab](#neighbors-is-its-own-tab)
removed, so they moved rather than disappeared.

Lists are a fifth tab on the search screen, hosting upstream's own `ManageListsFragment` with
`__is_tab`, which drops its toolbar the way the other tab fragments do. `DiscoverFragment` needed
the tab count, the id, the page mapping and the offscreen page limit; nothing else.

Followed hashtags open and close at the top of the Hashtags tab, as `FollowedHashtagsAdapter` in
`fork/`, merged in front of upstream's adapter. It is collapsed by default, since that tab is for
finding new hashtags, and it refetches every time it is opened: caching the first response left it
stale for the rest of the session after following something.

### Neighbors is its own tab

Upstream puts Home, Local, Lists and Followed hashtags behind a dropdown in the home toolbar. The
local timeline is what this fork is for, so it has its own tab at the left of the bottom bar,
called Neighbors, and the toolbar just names whichever timeline you are on. Lists and followed
hashtags move to the search screen.

Both tabs are `HomeTimelineFragment`; the Neighbors one gets a `localOnly` argument. That has to
be read in `onAttach`, before upstream's `loadData()` call there, or the tab loads the home
timeline instead. `HomeFragment` adds the second fragment, hides whichever tab isn't opening, and
forwards window insets to it, which it otherwise does not get and its toolbar lands under the
status bar.

The app opens on Neighbors, which Settings > Behavior > "Opening tab" can change. `ForkPrefs`
keeps that out of upstream's `GlobalUserPreferences`, in its own SharedPreferences file, so
neither file conflicts.

The home tab's icon is Material's `location_city`, which has no distinct filled variant, so the
selected state reads through the tab colours. The Neighbors icon is derived from Five Borough Fedi
Project's pigeon drawing by `tools/artwork/pigeonize.py`, which rebuilds it at the weight Material
tab icons use; a straight trace of the drawing is too faint to read at 24dp.
[tools/artwork/README.md](tools/artwork/README.md) covers regenerating it.

### Settings behind the profile tab

The gear is out of the home toolbar: settings and log out are behind a long press on the profile
tab, where upstream put the account switcher. `ProfileTabMenuSheet` is a new file in `fork/`, so
the upstream delta is the long press in `HomeFragment` and the gear's visibility in
`HomeTimelineFragment`.

The gear still appears there for one reason: the self updater uses it to show that an update is
ready, and github builds have nowhere else to say so. It is hidden until then.

### One account per install

This fork is locked to one server and one account, so the account switcher and "add account" are
unreachable: the long press on the profile tab, the "Add account..." row in settings, and the back
arrow on the email activation screen, which opened the switcher during signup.

`AccountSwitcherSheet` and the multi-account support in `AccountSessionManager` are untouched.
Nothing navigates to them, which keeps the delta to three removed call sites rather than a rewrite
of session handling, and keeps upstream merges clean.

### Patched appkit

Play Console flags calls to `Window.setStatusBarColor` and `setNavigationBarColor`, deprecated from
Android 15, and most of ours were in appkit. `third_party/appkit` is appkit built from source with a
patch that makes the bars transparent through the theme instead, and renames
`FragmentRootLinearLayout`'s color setters, which Play flags by name. Its
[README](third_party/appkit/README.md) covers what's patched and how to drop it.

The app side is ordinary edits to upstream files, kept as its own commit so it can go to
mastodon/mastodon-android as-is once appkit publishes the change. After an upstream merge, the
compiler catches anything that brought a call back: a new `rootView.setStatusBarColor(...)` on a
`FragmentRootLinearLayout` won't resolve. A new `Window.setStatusBarColor` call would compile, so
it's worth grepping for.

`mastodon/fork.gradle` fails the build if the vendored copy's version stops matching what
`build.gradle` asks for, since substitution would otherwise swallow an upstream appkit bump
silently. CI greps for the deprecated calls returning, which the compiler can't catch.

`android.nonTransitiveRClass=false` is there because upstream code reaches recyclerview resources
through `me.grishka.appkit.R`. The published AAR was built with transitive R classes, and a source
build has to match.

### Tests for all this

`mastodon/src/test/java/org/joinmastodon/android/fork/` holds tests for the fork's own changes, and
`fork.gradle` adds Robolectric for them, so upstream's `build.gradle` stays untouched:

- `SystemBarColorApiTest` checks the vendored appkit still has the renamed accessors and none of
  the flagged names. The compiler covers direct calls; this covers `ObjectAnimator`, which resolves
  property names by reflection, where a stale name compiles and animates nothing.
- `ThemeSystemBarColorsTest` checks every app theme leaves the window's bars transparent and gives
  fragments a color to paint behind them, and that the QR dialog theme matches the platform's
  private `NoFrame` theme it copies.
- `QrCodeLayoutTest` measures the QR code screen in both orientations and checks nothing lands off
  the bottom, which is what the portrait lock used to hide.
- `QrCodeScreenshotTest` renders the same screen to golden images under `src/test/screenshots`,
  through Robolectric's native graphics. Re-record after an intentional change with
  `./gradlew testDebugUnitTest -Dfork.screenshots.record=true`, and look at the diff before
  committing it. `ForkScreenshot` writes actual, golden and diff images to
  `build/reports/fork-screenshots` on a failure.

Each was checked by reverting the fix it covers and watching it fail.

What none of them reach is the window: Robolectric draws view trees, so the system bars, real
dialog windows and anything the platform draws around the app are invisible to it. The action mode
status bar regression, the worst one found while writing this, would not have been caught by any
test here.

That one needs an emulator, which is what `tools/visual-check.sh` is for: it drives a running,
signed-in emulator and compares regions of the screen, mostly status bar strips, against goldens
per API level. It is not in CI, because the screens worth checking are behind a login and a CI
emulator has no account. Run it by hand when touching anything around the system bars.
[tools/visual/README.md](tools/visual/README.md) has the details, including the run where it
catches that exact regression.

Pixels are not the only thing an emulator can tell you, though, and most of what this fork has
got wrong was not a pixel. A tab wired to the wrong timeline looks completely normal; so does a
crash that only happens on a hard fling. So `tools/smoke-test.sh` drives the same emulator and
asserts facts instead of comparing images:

- which endpoint each timeline tab actually called, read out of the request log that
  `MastodonAPIController` writes on debug builds. The Neighbors tab silently loading the home
  timeline is invisible on screen and obvious here.
- that both timeline toolbars land at the same height, which is how a tab that isn't being handed
  the window insets shows up.
- that the followed hashtags section survives being flung, with any `FATAL EXCEPTION` in logcat
  failing the check that was running at the time.
- that settings are still reachable from the profile tab's long press, now the only way in.

It found the followed hashtags crash that could not be reproduced by hand: six hard flings in each
direction, where a dozen deliberate scrolls had not been enough. Both scripts share
`tools/lib/emulator.sh` for adb, taps and demo mode, so there is one copy of each.

### Crashes from real phones

The hashtags crash arrived as a sentence from the person who hit it, and the only way to act on it
was to guess which contract it had violated. Play had the stack trace all along; nothing here
could read it. `tools/play-crashes.py` does, through the Play Developer Reporting API, and prints
the cause, the location, the affected versionCodes and a sample stack trace. The `Play crashes`
workflow runs it on demand and weekly; it only prints, because the point is to be read rather than
to block anything.

The service account needs "View app information and download bulk reports" in Play Console, which
is not implied by the publishing permission the release pipeline uses. A 403 says so in those
words.

The key is a secret, so that script cannot be run end to end on a laptop, which is a good way to
ship a tool that has never worked. `--check` reads the API's unauthenticated discovery document
and fails if the script sends a parameter the API doesn't accept or reads a field it doesn't
return; CI runs it on every PR. Writing it caught three real mistakes before the first live call,
the worst being a missing `sampleErrorReportLimit`, which defaults to 0 and would have returned
every stack trace empty.

### Releasing to Play

`PLAY_TRACK` and `PLAY_RELEASE_STATUS` are repository variables, so where a release lands is a
settings change rather than a code change. With `PLAY_RELEASE_STATUS=completed`, publishing a
GitHub release rolls out to production with no Play Console visit.

The deploy workflow builds everything before uploading anything, runs the tests and lint against
the tag, refuses to publish when `changelogs/<versionCode>.txt` is missing, and validates the
upload against Play before performing it. `workflow_dispatch` with `dry_run: true` stops after
validation, which is how to test a pipeline change without consuming a versionCode. A versionCode
can only ever be uploaded once.

A build already uploaded can't be re-released; it has to be promoted. The "Promote a Play release"
workflow does that by versionCode, for drafts uploaded before the status variable was set or for
ramping a staged rollout.

### Versioning

The user-facing rule is in the README. The mechanics:

`mastodon/ci_version.gradle` derives `versionName` and `versionCode` from `RELEASE_TAG`, applied by
CI with `apply from:`, the same way `ci_signing.gradle` works. `build.gradle` is never edited.
Upstream bumps those two version lines on every release, so owning them would mean a conflict every
time we pull, and it would throw away the useful side effect of leaving them alone: `build.gradle`'s
`versionName` stays an accurate record of which upstream release we're on, maintained for free. CI
reads it before overriding.

`versionCode = major*1000000 + minor*1000 + patch`, which caps minor and patch at 999. Past that the
arithmetic collides, since `1.0.1000` would equal `1.1.0`. Both `ci_version.gradle` and the workflow
reject it, along with any tag that isn't exactly three numbers.

Suffixes are banned for a mechanical reason. `GithubSelfUpdaterImpl` matches
`/v?(\d+)\.(\d+)(?:\.(\d+))?/`
and discards anything past the third number, so `v1.0.0-beta` and `v1.0.0` compare equal and no
update is ever offered. Nothing errors when that happens, so the workflow checks the tag before it
builds.

While you're in that file: there's a real bug at `GithubSelfUpdaterImpl:124`, where the
current-version branch tests `matcher.group(3)` (the tag) but parses `curMatcher.group(3)` (the
installed version). A three-part tag against a two-part installed version throws, the catch swallows
it, and update detection dies with no symptom. Three-part versions everywhere avoids it. Worth
sending upstream.

## Toolchain

- JDK 21 (Temurin), matching both CI workflows. The wrapper pulls Gradle 9.7.1 for AGP 9.4.0.
- Android SDK Platform 37.0. Note the `.0`, per above.
- AGP downloads Build-Tools itself once SDK licences are accepted, so there's no version to pin.
- `local.properties` (gitignored) needs `sdk.dir=<path to SDK>`.

`./gradlew assembleDebug` produces a 7.2 MB APK with package `nyc.masto.android` and label
Masto NYC. It's been installed and used on a physical device, not just compiled: splash, signup,
email activation polling and branding all check out.

## Edge-to-edge

Play Console says "Edge-to-edge may not display for all users" and suggests calling
`enableEdgeToEdge()`. The app already draws edge-to-edge on every version it supports, so there is
nothing to call.

appkit's `FragmentStackActivity.onCreate` sets `SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN`,
`LAYOUT_HIDE_NAVIGATION` and `LAYOUT_STABLE` and makes the bars transparent, on every API level.
Fragments receive the insets themselves: `FragmentRootLinearLayout.onApplyWindowInsets` pads by the
system window insets and paints the bar colors into the padding. Google's suggested
`EdgeToEdge.enable()` takes an AndroidX `ComponentActivity`, and these activities extend
`android.app.Activity` through appkit, so it doesn't apply.

Checked on Android 14 and 15 emulators, gesture and 3-button navigation, portrait and both
landscape rotations, with a display cutout emulated: no clipped content, bars padded correctly,
and the compose screen resizes for the keyboard. Android 14 matters because it's the version where
edge-to-edge isn't enforced, so a regression would show up there first.

Worth re-checking after an upstream merge that touches window insets, and on a device with a
cutout in landscape, where the cutout mode from API 35 is `ALWAYS` rather than `SHORT_EDGES`.

## Artwork

Upstream's license notice requires a redistributed fork to use its own name and icon, so the
trademarked marks had to go. Identity artwork is Five Borough Fedi Project's now. The background
illustrations are still upstream's, tracked below.

Replacements keep the same filename and path, so nothing in the code or layouts has to change and
there's no extra merge surface.

The launcher icon comes from `5bfplogo.png` (yellow elephant, green Liberty crown). Generated layers
sit in `drawable-{m,h,x,xx,xxx}hdpi/ic_launcher_elephant{,_mono}.png` on a 108dp canvas with the art
at 62%, so no launcher mask clips the trunk or crown.

The splash logo is the 5BFP lockup, keyed off its white JPEG background. Two things there are worth
knowing if the art gets re-exported:

- The cream tusk and eye highlight have to stay opaque, while the counters inside the B and P have
  to knock through, or they read as printing errors against the blue. The background was found by
  flooding inward from the border; the counters were isolated by eroding the enclosed white regions
  to seeds, which erases the small eye highlight, then flooding those seeds back out.
- The lockup is 2.32:1 against the 3.86:1 of upstream's wordmark, so the ImageView in
  `fragment_splash.xml` went from 300×78dp to 300×129dp. Re-export at a different ratio and that
  height needs updating, or `fitCenter` letterboxes it.

`ic_ntf_logo` is the Liberty crown on its own, and it stayed a vector while the other replacements
became bitmaps. Both were deliberate:

- The full elephant is illegible at 24dp. It reads as a blob, and knocking out the eye to fix that
  produces something unfriendly. The crown survives the size and is unmistakably NYC. One solid
  contour, no knockouts, so it needs no `android:fillType`, which also sidesteps minSdk 23 predating
  that attribute and silently falling back to nonZero winding.
- Despite the name it isn't only the notification icon. `ProfileQrCodeFragment` drops it into the
  middle of the profile QR code, where `FancyQrCodeDrawable` draws it at `size/3` of a QR that
  `saveCodeAsFile` renders at 1080×1080, so roughly 360px. A 24dp bitmap would blur badly.
  `LinkCardHolder` uses it too, at 17dp.

### Replaced

| File | Format | Size |
| --- | --- | --- |
| `drawable-anydpi-v26/ic_launcher_foreground.xml` | bitmap wrapper | wraps `ic_launcher_elephant` |
| `drawable-anydpi-v26/ic_launcher_background.xml` | shape | flat `#FFFFFF`, one line to retheme |
| `drawable-anydpi-v26/ic_launcher_monochrome.xml` | bitmap wrapper | wraps `ic_launcher_elephant_mono` |
| `res/mipmap-*/ic_launcher.png` | PNG ×5 | 48-192px, API 23-25 only |
| `res/drawable/splash_logo.xml` | bitmap wrapper | wraps `splash_logo_5bfp` |
| `res/drawable/ic_ntf_logo.xml` | vector | 24×24dp, Liberty crown |
| `fastlane/.../images/icon.png` | PNG | 512×512, full-bleed, opaque |
| `fastlane/.../images/featureGraphic.png` | PNG | 1024×500, subway scene |

Neither store image is uploaded by CI: both Fastfile lanes pass `skip_upload_images: true` and both
workflows set `SUPPLY_SKIP_UPLOAD_METADATA: true`. Until that changes they're the source of truth in
git, but the live listing gets set by hand.

### Still upstream's

Background illustrations rather than identity marks, so not license-blocking, but recognisably
Mastodon's mascot and the most visible thing left. The three elephants on the splash screen are
these.

Five parallax layers, scaled onto a 360×640dp stage. Keep each aspect ratio or the offsets in
`fragment_splash.xml` need retuning.

| File | Size (px) | Drawn at | Layer |
| --- | --- | --- | --- |
| `drawable-nodpi/splash_art_layer0.webp` | 870×1137 | 414×541dp | Clouds, 30% opacity |
| `drawable-nodpi/splash_art_layer4.webp` | 656×195 | 245.64×72.65dp | Elephant on a paper plane, 30% opacity |
| `drawable-nodpi/splash_art_layer1.webp` | 443×518 | 150.84×176.44dp | Right elephant |
| `drawable-nodpi/splash_art_layer2.webp` | 599×466 | 197.2×153.61dp | Left elephant |
| `drawable-nodpi/splash_art_layer3.webp` | 870×756 | 400×346dp | Centre elephants |
| `drawable/empty_state_elephant_light.xml` | viewport 400×400 | 200×200dp | Empty list, light |
| `drawable/empty_state_elephant_dark.xml` | viewport 400×400 | 200×200dp | Empty list, dark |

Several layers start off-canvas on purpose, so nothing shows a hard edge during the parallax motion.
Background fills are hardcoded in `fragment_splash.xml` as `#50D5ED` on top and `#478E6A` below;
change those with the art if the palette doesn't match.

### Optional

- `fastlane/metadata/android/en-US/images/phoneScreenshots/1-8.png` still show upstream branding.
- `drawable-nodpi/donation_successful_art.webp` is already unreachable, since upstream only offers
  donations to `mastodon.social` and `mastodon.online` accounts.

Not branded, nothing to do: `ic_shortcut_compose` and `ic_shortcut_explore` (generic glyphs),
`ic_notification_fallback` (a black dot), `poof.png`.

## Server-side pieces

`deploy/` holds what belongs on the masto.nyc side rather than in the app. `assetlinks.json` is the
one that matters: without it Android can't verify this app's claim on `https://masto.nyc/...`, and
the `autoVerify="true"` filter in the manifest quietly does nothing.

`deploy/cloudflare/` is a Worker that serves it, deployed by
`.github/workflows/deploy-assetlinks.yml` on every published release. Two details there matter:

- The route is scoped to exactly `masto.nyc/.well-known/assetlinks.json`. Widening it to
  `/.well-known/*` would swallow Mastodon's webfinger, nodeinfo and host-meta endpoints and break
  federation.
- Fingerprints come from the release keystore at deploy time and are never committed. A stale one
  fails silently, with links just going back to opening in the browser, so
  `deploy/cloudflare/src/assetlinks.json` is gitignored.

`deploy/README.md` has the rest, including the Play App Signing trap: with App Signing on, the
installed APK carries Google's signature rather than the upload key's, so `PLAY_APP_SIGNING_SHA256`
has to be set or verification fails for Play installs while passing for CI-built APKs.

## Deliberately not changed: "Open email app"

The button on the signup confirmation screen uses
`Intent.makeMainSelectorActivity(ACTION_MAIN, CATEGORY_APP_EMAIL)`. That's the documented Android
intent for the job and there's no better API. Android has no "open the inbox" contract beyond it,
and `mailto:` is for composing, not opening a mailbox.

A `mailto:`-resolution heuristic was tried here and reverted. On a test device the button opened
Tasker, but that took three unusual things at once: no Gmail installed (Gmail does declare the
category), a mail app declaring only `mailto:`, and Tasker declaring `CATEGORY_APP_EMAIL`. Carrying
a heuristic in an upstream file to paper over that is a bad trade against merge cost, and the
heuristic has its own failure mode: Android 11+ package visibility hides any mail app that doesn't
also declare `http`/`https` filters, so it needs extra `<queries>` entries and still turns up false
positives like a pharmacy app that registers `mailto:`.

If someone hits this, the fix is on their device: set a default mail app, or ask the vendor to
declare `CATEGORY_APP_EMAIL`.

## Known gaps

`app_name` is `translatable="false"` and lives only in `values/`, so the app name rebrands in every
locale. Some translated strings in `values-*/strings.xml` still say "Mastodon" in prose, though.
Overriding those means editing 60+ Crowdin-managed files, which is the exact merge pain this
structure exists to avoid.

Donation prompts are already inert, since upstream only shows them to `mastodon.social` and
`mastodon.online` accounts.
