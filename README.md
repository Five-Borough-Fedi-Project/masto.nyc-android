# Masto NYC for Android

A fork of the [official Mastodon Android app](https://github.com/mastodon/mastodon-android), rebranded for [masto.nyc](https://masto.nyc) and locked to that server.

Not affiliated with or endorsed by the Mastodon non-profit.

Having to pick a server before you can sign up puts a lot of people off. This app skips that step. There's no server picker; signup and login both go straight to masto.nyc. You still get the whole fediverse once you're in.

## What's different from upstream

- Signup and login only work with masto.nyc. The server picker and catalog are gone.
- Branded as Masto NYC, with Five Borough Fedi Project artwork for the launcher icon, splash logo and notification icon.
- Application ID is `nyc.masto.android`, so it installs alongside the official app.

[FORK.md](./FORK.md) lists every changed file and the conventions that keep upstream merges cheap.

## Building

You need JDK 21 and Android SDK Platform 37.0.

Don't reach for a newer JDK. The Gradle wrapper pulls Gradle 8.13, which predates JDK 24 and 25, and CI pins 21 as well.

Mind the `.0` on the platform. Google stopped publishing a bare `android-37`, so `sdkmanager "platforms;android-37"` fails, and it takes the rest of its arguments down with it.

```shell
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
```

```shell
./gradlew assembleDebug
```

AGP downloads Build-Tools itself once you've accepted the SDK licences, so there's nothing to pin. [FORK.md](./FORK.md#toolchain) has the rest.

## Releasing

Tag it `vX.Y.Z` and publish. That's the whole process.

Three numbers, a `v` in front, nothing after. `v1.2.3` works; `v0.1-alpha`, `v1.2` and `1.2.3` don't, and the release workflow rejects them before it builds anything.

The reason is the in-app updater. It parses versions with `/v?(\d+)\.(\d+)(?:\.(\d+))?/`, which throws away anything past the third number, so it reads `v1.0.0-beta` and `v1.0.0` as the same version and never offers the update. Nothing errors out. Users just quietly stop getting updates, which is why the check exists.

Publishing a release will:

- turn the tag into a `versionName` (`1.2.3`) and `versionCode` (`1002003`)
- build a signed APK and attach it as `masto-nyc-vX.Y.Z.apk`
- push `assetlinks.json` to Cloudflare with the current signing fingerprint

Bump the patch for your own changes between upstream merges. Bump the minor when you pull in an upstream release or ship a feature. Major is your call.

Our version numbers have nothing to do with upstream's, and it isn't worth trying to encode theirs into ours, since the updater ignores anything past the third number anyway. We do still track which upstream release we're sitting on: this fork never edits `build.gradle`, so the `versionName` in there stays upstream's, and CI reads it at build time.

Don't re-tag a version you've already released; bump the patch instead. `versionCode` comes from the tag, Play rejects duplicates, and the updater caches by version.

## Contributing

Bug reports and pull requests for fork-specific things are welcome here. Anything that isn't masto.nyc-specific is better sent to [mastodon/mastodon-android](https://github.com/mastodon/mastodon-android), where everyone benefits and it'll reach this fork on the next merge.

Translations come from upstream via Crowdin. Please don't send pull requests that change `strings.xml` for languages other than English.

## License

[GPL-3](./LICENSE).

The Mastodon name and logo are trademarks of the Mastodon non-profit. This fork uses its own name, and the launcher icon, splash logo, notification icon and store images are Five Borough Fedi Project artwork. The splash background and empty-state illustrations are still upstream's; [FORK.md](./FORK.md#artwork) tracks what's left.
