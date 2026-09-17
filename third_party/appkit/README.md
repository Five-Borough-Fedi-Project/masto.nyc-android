# appkit, patched

A copy of [grishka/appkit](https://github.com/grishka/appkit) with one patch on top, built from
source in place of the `me.grishka.appkit:appkit` artifact. `mastodon/fork.gradle` does the
substitution and `fork-settings.gradle` includes the module.

appkit is released under the Unlicense, as declared in its published POM.

## Why

Play Console flags Masto NYC for calling `Window.setStatusBarColor` and
`Window.setNavigationBarColor`, which are deprecated from Android 15 and do nothing there. Most of
those calls are in appkit, so the fix can't be made in the app alone.
[`patches/`](patches) has the change as a `git format-patch` file, ready to send to appkit.

## What's here

- `src/`: appkit's `appkit/src` at commit `2f69cf4` (published as 1.5.3), with the patch applied.
- `src/main/AndroidManifest.xml`: the `package` attribute removed, since this build's AGP rejects
  it in favor of `namespace`. That's a build difference only, not part of the patch.
- `build.gradle`: ours. Upstream's targets an older AGP and applies its publishing script.

The app side of the change lives in upstream Mastodon files, so it merges like any other edit. It's
a separate commit on the same branch, written to apply to mastodon/mastodon-android unchanged once
it depends on an appkit release that has the patch.

## Removing this

Once appkit publishes a version with the change and upstream Mastodon depends on it:

1. Delete `third_party/appkit/` and `fork-settings.gradle`.
2. Remove the `apply from: 'fork-settings.gradle'` line from `settings.gradle`.
3. Remove the `dependencySubstitution` block from `mastodon/fork.gradle`.
4. Remove `android.nonTransitiveRClass=false` from `gradle.properties`.

## Updating appkit

```bash
git clone https://github.com/grishka/appkit && cd appkit
git checkout <new commit>
git am <this repo>/third_party/appkit/patches/*.patch
rm -rf <this repo>/third_party/appkit/src && cp -r appkit/src <this repo>/third_party/appkit/
```

Then take out the manifest's `package` attribute again, and check the dependencies in `build.gradle`
still match appkit's.
