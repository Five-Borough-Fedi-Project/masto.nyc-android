# Masto NYC for Android

A fork of the [official Mastodon Android app](https://github.com/mastodon/mastodon-android), rebranded and locked to the [masto.nyc](https://masto.nyc) server.

This app is not affiliated with or endorsed by the Mastodon non-profit organisation.

## Why

Mastodon's federated onboarding — "pick a server" before you can even sign up — is a well-known point of confusion for new users. This fork removes that step entirely: the app talks to one server, masto.nyc, and nothing else. You still get the whole fediverse once you're in; you just don't have to understand it to get started.

## Differences from upstream

- Signup and login are locked to `masto.nyc`. There is no server picker and no server catalog.
- Branding is "Masto NYC", with Five Borough Fedi Project artwork for the launcher icon, splash logo and notification icon.
- Application ID is `nyc.masto.android`, so it installs alongside the official app.

See [FORK.md](./FORK.md) for the full inventory of changed files and the conventions that keep upstream merges cheap.

## Building

Requires **JDK 21** and **Android SDK Platform 37.0**.

Two things that will otherwise cost you an afternoon. Do not use a newer JDK — the Gradle wrapper pulls Gradle 8.13, which predates JDK 24/25 support; 21 is also what CI pins. And note the `.0` on the platform: Google no longer publishes a bare `android-37`, so `sdkmanager "platforms;android-37"` fails and takes the rest of its arguments down with it.

```shell
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
```

```shell
./gradlew assembleDebug
```

Build-Tools are downloaded by AGP itself once the SDK licences are accepted, so there's no version to pin. See [FORK.md](./FORK.md#toolchain) for the full toolchain notes.

## Releasing

**Tag releases `vX.Y.Z`. Exactly three numbers, `v` prefix, no suffix.** That is the whole process — publish a GitHub release with a conforming tag and everything else is automatic.

`v1.2.3` ✅ · `v0.1-alpha` ❌ · `v1.2` ❌ · `1.2.3` ❌

This is enforced, not a convention: the release workflow validates the tag before doing anything and fails in seconds if it doesn't conform.

**Why suffixes are banned.** The in-app updater matches `/v?(\d+)\.(\d+)(?:\.(\d+))?/` and ignores everything after the third number, so `v1.0.0-beta` and `v1.0.0` compare as **equal** — users would simply never be offered the update. It fails silently, which is why the check exists.

Publishing a release automatically:

- derives `versionName` (`1.2.3`) and `versionCode` (`1002003`) from the tag
- builds a signed APK and attaches it as `masto-nyc-vX.Y.Z.apk`
- publishes `assetlinks.json` to Cloudflare with the current signing fingerprint

### Which number to bump

| Bump | When |
| --- | --- |
| patch | your own changes between upstream merges — fixes, strings, artwork |
| minor | you merged an upstream release, or shipped a real feature |
| major | your call |

Versioning is **independent of upstream's**. Don't try to encode their version into yours — anything past the third number is invisible to the updater. Upstream's version is still recorded automatically: `build.gradle` is never edited by this fork, so its `versionName` remains an accurate note of which upstream release the fork sits on, and CI reads it at build time.

**Never re-tag a released version.** Bump the patch instead. `versionCode` derives from the tag, Play rejects duplicates, and the updater caches by version.

## Contributing

Bug reports and pull requests specific to this fork are welcome here. Anything that isn't masto.nyc-specific should go upstream to [mastodon/mastodon-android](https://github.com/mastodon/mastodon-android) instead, so that it benefits everyone and flows back into this fork on the next merge.

Translations are inherited from upstream via Crowdin. Please do not create pull requests that modify `strings.xml` files for languages other than English.

## License

This project is released under the [GPL-3 License](./LICENSE).

The Mastodon name and logo are trademarks of the Mastodon non-profit organisation. This fork uses a distinct name, and the launcher icon, splash logo, notification icon and store images are all Five Borough Fedi Project artwork. The splash background illustration and the empty-state art are still upstream's; see [FORK.md](./FORK.md#artwork) for what remains.
