# Fork notes

A fork of [mastodon/mastodon-android](https://github.com/mastodon/mastodon-android), rebranded as
Masto NYC and locked to masto.nyc.

Everything below exists so that pulling upstream stays a chore rather than a project. The fewer
upstream lines we touch, the less there is to reconcile.

## Conventions

**Values live in `ForkConfig`, not scattered through upstream files.**
[`ForkConfig.java`](mastodon/src/main/java/org/joinmastodon/android/fork/ForkConfig.java) holds the
server domain and anything else specific to us. It's a new file in a new package, so it can never
conflict. When an upstream file needs one of these values, import it and read it from there; that
keeps the edit down to a line or two.

**New strings go in a new file.** Put genuinely new strings in
`mastodon/src/main/res/values/strings_nyc.xml` (create it when you need it), never appended to
upstream's `strings.xml`. Overrides of existing upstream strings have to be edited in place, since
Android won't allow the same resource name in two files in one source set.

**Don't delete upstream files.** `InstanceChooserLoginFragment`, `InstanceCatalogSignupFragment` and
`InstanceCatalogFragment` are unreachable now, but they stay. Deleting a file upstream still
maintains turns every future change to it into a modify/delete conflict, which is worse to deal with
than some dead code. Same for the `intro_bottom_sheet` layout and the `welcome_*`, `pick_server` and
`learn_more` strings.

**Mark non-obvious edits** with a `// masto.nyc fork:` comment, so it's clear during a merge that
the line is ours on purpose.

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
| `mastodon/build.gradle` | `applicationId` → `nyc.masto.android`; added `compileSdkMinor 0` |
| `mastodon/src/main/AndroidManifest.xml` | deep links point at `masto.nyc` |
| `mastodon/src/main/res/values/strings.xml` | `app_name`, `settings_contribute`, `settings_app_version`, `local_timeline_info_banner` |
| `mastodon/src/main/res/values/urls.xml` | `github_url`, `privacy_policy_url` |
| `mastodon/src/main/res/layout/fragment_splash.xml` | dropped the server picker and the "Learn more" sheet |
| `.../fragments/SplashFragment.java` | server is fixed; log in goes straight to OAuth; no catalog request |
| `.../fragments/onboarding/GoogleMadeMeAddThisFragment.java` | privacy policy item points at ours |
| `.../api/requests/oauth/CreateOAuthApp.java` | OAuth client name and website |
| `.../api/MastodonAPIController.java`, `.../MastodonApp.java` | `User-Agent`, see [below](#user-agent) |
| `.../updater/GithubSelfUpdaterImpl.java` | self-update from this repo, not upstream's |
| `res/drawable/splash_logo.xml`, `res/drawable/ic_ntf_logo.xml` | replaced artwork |
| `res/drawable-anydpi-v26/ic_launcher_{foreground,background,monochrome}.xml` | replaced artwork |
| `res/mipmap-*/ic_launcher.png` | replaced artwork |
| `README.md`, `fastlane/metadata/android/en-US/*` | store listing and repo docs |

New files can't conflict: `ForkConfig.java`, `ci_version.gradle`, `FORK.md`, `deploy/`, and the
generated artwork under `res/drawable-*dpi/`.

Changing `applicationId` also moves the OAuth callback scheme (`${applicationId}-auth://callback`)
and the FileProvider authority, so neither needs editing. The built APK confirms it: the manifest
ends up with `nyc.masto.android-auth`.

### Why `compileSdkMinor 0` is there

A build fix, not a branding change, and upstream hits it too on a fresh SDK. Google no longer
publishes a bare `platforms;android-37`. Every API 37 platform is minor-versioned (`android-37.0`,
`android-37.1`, plus `37.2` betas) and `source.properties` reports `AndroidVersion.ApiLevel=37.0`,
so upstream's bare `compileSdk 37` fails with:

    Failed to find target with hash string 'android-37' in: <sdk>

AGP 8.13.2 supports `compileSdkMinor` even though upstream doesn't use it, so adding
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

The ban on suffixes isn't stylistic. `GithubSelfUpdaterImpl` matches `/v?(\d+)\.(\d+)(?:\.(\d+))?/`
and discards anything past the third number, so `v1.0.0-beta` and `v1.0.0` compare equal and no
update is ever offered. Nothing errors, hence the up-front check.

While you're in that file: there's a real bug at `GithubSelfUpdaterImpl:124`, where the
current-version branch tests `matcher.group(3)` (the tag) but parses `curMatcher.group(3)` (the
installed version). A three-part tag against a two-part installed version throws, the catch swallows
it, and update detection dies with no symptom. Three-part versions everywhere avoids it. Worth
sending upstream.

## Toolchain

- JDK 21 (Temurin), matching both CI workflows. Nothing newer: the wrapper pulls Gradle 8.13, which
  predates JDK 24 and 25.
- Android SDK Platform 37.0. Note the `.0`, per above.
- AGP downloads Build-Tools itself once SDK licences are accepted, so there's no version to pin.
- `local.properties` (gitignored) needs `sdk.dir=<path to SDK>`.

`./gradlew assembleDebug` produces a 7.2 MB APK with package `nyc.masto.android` and label
Masto NYC. It's been installed and used on a physical device, not just compiled: splash, signup,
email activation polling and branding all check out.

## Artwork

Upstream's license notice requires a redistributed fork to use its own name and icon, so the
trademarked marks had to go. Identity artwork is Five Borough Fedi Project's now. The background
illustrations are still upstream's, tracked below.

Replacements keep the same filename and path, so no code or layout changes, and no merge surface.

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
| `res/mipmap-*/ic_launcher.png` | PNG ×5 | 48–192px, API 23–25 only |
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

- `fastlane/metadata/android/en-US/images/phoneScreenshots/1–8.png` still show upstream branding.
- `drawable-nodpi/donation_successful_art.webp` is already unreachable, since upstream only offers
  donations to `mastodon.social` and `mastodon.online` accounts.

Not branded, nothing to do: `ic_shortcut_compose` and `ic_shortcut_explore` (generic glyphs),
`ic_notification_fallback` (a black dot), `poof.png`.

## Server-side pieces

`deploy/` holds what belongs on the masto.nyc side rather than in the app. `assetlinks.json` is the
one that matters: without it Android can't verify this app's claim on `https://masto.nyc/...`, and
the `autoVerify="true"` filter in the manifest quietly does nothing.

`deploy/cloudflare/` is a Worker that serves it, deployed by
`.github/workflows/deploy-assetlinks.yml` on every published release. Two things there are
load-bearing:

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

- **Non-English branding.** `app_name` is `translatable="false"` and lives only in `values/`, so the
  app name rebrands everywhere. But some translated strings in `values-*/strings.xml` still say
  "Mastodon" in prose. Overriding those means editing 60+ Crowdin-managed files, which is the exact
  merge pain this structure exists to avoid.
- **Donation prompts** are already inert, since upstream only shows them to `mastodon.social` and
  `mastodon.online` accounts.
