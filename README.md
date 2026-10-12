# Masto NYC for Android

[<img alt="Get it on Google Play" src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" height="50">](https://play.google.com/store/apps/details?id=nyc.masto.android)

[![CI](https://github.com/Five-Borough-Fedi-Project/masto.nyc-android/actions/workflows/ci.yml/badge.svg)](https://github.com/Five-Borough-Fedi-Project/masto.nyc-android/actions/workflows/ci.yml)
[![Sync upstream](https://github.com/Five-Borough-Fedi-Project/masto.nyc-android/actions/workflows/sync-upstream.yml/badge.svg)](https://github.com/Five-Borough-Fedi-Project/masto.nyc-android/actions/workflows/sync-upstream.yml)
[![Deploy main to internal testing](https://github.com/Five-Borough-Fedi-Project/masto.nyc-android/actions/workflows/deploy-main.yml/badge.svg)](https://github.com/Five-Borough-Fedi-Project/masto.nyc-android/actions/workflows/deploy-main.yml)
[![Latest release](https://img.shields.io/github/v/release/Five-Borough-Fedi-Project/masto.nyc-android)](https://github.com/Five-Borough-Fedi-Project/masto.nyc-android/releases)

A fork of the [official Mastodon Android app](https://github.com/mastodon/mastodon-android), rebranded for [masto.nyc](https://masto.nyc) and locked to that server — no server picker, signup and login go straight to masto.nyc. Not affiliated with or endorsed by the Mastodon non-profit.

## What's different from upstream

- Signup and login only work with masto.nyc. The server picker and catalog are gone.
- Branded as Masto NYC, with Five Borough Fedi Project artwork for the launcher icon, splash logo and notification icon.
- The application ID is `nyc.masto.android`, so it installs alongside the official app.

[FORK.md](./FORK.md) lists every changed file and the conventions that keep upstream merges cheap.

## Building

JDK 21 and Android SDK Platform `37.0` (the `.0` matters — Google stopped publishing a bare `android-37`).

```shell
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
./gradlew assembleDebug
```

[FORK.md](./FORK.md#toolchain) has the rest of the toolchain.

## Releasing

Every merge to `main` builds and uploads to Play's internal track automatically. Shipping to production is promoting one of those builds, via the "Promote a Play release" workflow. See [FORK.md](./FORK.md#releasing-to-play) for how versioning and release notes work.

## Contributing

Bug reports and pull requests for fork-specific things are welcome here. Anything that isn't masto.nyc-specific is better sent to [mastodon/mastodon-android](https://github.com/mastodon/mastodon-android), where everyone benefits and it'll reach this fork on the next merge.

Translations come from upstream via Crowdin — please don't send pull requests that change `strings.xml` for languages other than English.

## License

[GPL-3](./LICENSE). The Mastodon name and logo are trademarks of the Mastodon non-profit; this fork's name and artwork are Five Borough Fedi Project's. See [FORK.md](./FORK.md#artwork) for what's still upstream's.
