# Server-side files for masto.nyc

## `assetlinks.json` — Android App Links

**This file must end up at exactly one URL:**

    https://masto.nyc/.well-known/assetlinks.json

That path is hardcoded in Android. It cannot be configured, moved, or redirected. The `deploy/`
directory in this repo is only where the file is kept under version control — it has no meaning to
Android, and serving it from `masto.nyc/deploy/` does nothing.

Android will only let this app claim `https://masto.nyc/...` links if masto.nyc vouches for it this
way. Until the file is live, domain verification fails and the `autoVerify="true"` profile-link
filter in `AndroidManifest.xml` does nothing — tapping a `masto.nyc/@user` link stays in the browser.

### Deploying: automated, via Cloudflare (preferred)

`.github/workflows/deploy-assetlinks.yml` publishes it through a Cloudflare Worker on every
published GitHub release, and on manual dispatch. `deploy/cloudflare/` holds the Worker.

A Worker rather than a file on the origin, because it needs no filesystem access to the Mastodon
host and keeps working if the origin is down. The route is scoped to the exact path
`masto.nyc/.well-known/assetlinks.json` — **never widen it to `/.well-known/*`**, or Mastodon's
webfinger, nodeinfo and host-meta endpoints stop reaching the origin and federation breaks.

The fingerprints are **derived from the release keystore at deploy time**, not committed. A stale
fingerprint in git would break App Links for every user silently — links just quietly start opening
in the browser again, with no error surfaced anywhere. That's why `src/assetlinks.json` is
gitignored.

Required repository secrets:

| Secret | Purpose |
| --- | --- |
| `CLOUDFLARE_API_TOKEN` | Needs *Workers Scripts: Edit* and *Workers Routes: Edit* on the masto.nyc zone |
| `CLOUDFLARE_ACCOUNT_ID` | Cloudflare account the Worker deploys into |
| `KEYSTORE_FILE`, `KEYSTORE_PASSWORD` | Already used by the existing release workflows |
| `PLAY_APP_SIGNING_SHA256` | **Only if shipping via Google Play.** See below |

**The Play App Signing trap.** With Play App Signing enabled, the APK users install is re-signed by
*Google's* key, not your upload key — and Google's is the fingerprint Android verifies against. If
that secret is unset, verification passes for APKs built in CI and fails for every Play install,
which is a miserable thing to debug. Copy the SHA-256 from *Play Console → Release → Setup → App
signing*. The workflow emits a warning when it is missing rather than failing, since the fork is
also usable without Play.

The workflow finishes by fetching the live URL and asserting Android's actual requirements: HTTP
200 with no redirect, `application/json`, and the current `applicationId` and fingerprint present.
`workflow_dispatch` accepts `dry_run` to print the generated file without deploying.

### Deploying: manually, on the origin

A standard Mastodon nginx config sets `root .../public` with `try_files $uri @proxy`, so anything
under `public/` is served directly. Put the file at:

    /home/mastodon/live/public/.well-known/assetlinks.json

On Docker the path inside the container is the same, but `public/` usually isn't writable at
runtime — you'll need a volume mount or to bake it into the image.

Then check it from outside the server:

    curl -i https://masto.nyc/.well-known/assetlinks.json

All three must hold, and Android is unforgiving about each:

- **200** — not 301 or 302. Android does not follow redirects for this file.
- **`Content-Type: application/json`** — nginx infers this from the `.json` extension.
- **HTTPS**, with a valid certificate.

Most likely snag: if nginx has a `location /.well-known/` block for ACME/certbot, it can shadow
this path and return 404 even though the file is on disk. Check there first.

### Re-verifying on a device

Verification is attempted at install time, so an already-installed app will not pick it up:

    adb shell pm verify-app-links --re-verify nyc.masto.android
    adb shell pm get-app-links nyc.masto.android

You are looking for `masto.nyc: verified`.

### Fingerprints

The fingerprint in the committed `deploy/assetlinks.json` is a **debug** keystore — the one on the
machine that built the APK. It is fine for local testing and useless to anyone else, since every
developer's debug keystore differs. It exists only for the manual path above; the automated
Cloudflare deploy derives its fingerprints from the release keystore and ignores this file.

To read a fingerprint out of any keystore:

    keytool -list -v -keystore <keystore>.jks -alias <alias> | grep SHA256

`sha256_cert_fingerprints` is an array, so debug and release keys can coexist and both builds
verify.

## What this does not fix

Publishing this file does **not** by itself make the signup confirmation email return to the app.
That link redirects to the OAuth app's registered `redirect_uri`, which is the custom scheme
`nyc.masto.android-auth://callback`, and browsers commonly block server-initiated redirects to
custom schemes. Fixing that properly means changing `AccountSessionManager.REDIRECT_URI` to an
`https://masto.nyc/...` App Link and registering it in the manifest — which only works once this
file is live and verified, and which breaks login entirely if verification ever fails. Worth doing
deliberately, not as a side effect.

In the meantime `AccountActivationFragment` polls `GetOwnAccount` every 10 seconds and re-checks
whenever it becomes visible, so switching back to the app after confirming in the browser advances
onboarding on its own.
