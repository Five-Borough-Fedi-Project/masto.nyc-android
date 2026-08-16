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

### Deploying

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

The fingerprint currently listed is a **debug** keystore — the one on the machine that built the
APK. It is fine for testing and useless to anyone else, since every developer's debug keystore
differs.

Before release, add the release signing key's fingerprint. The field is an array, so keep both and
debug builds continue to work:

    keytool -list -v -keystore <release>.jks -alias <alias> | grep SHA256

If the app is ever distributed through Google Play with Play App Signing, the fingerprint that
matters is the one Play shows under *Release → Setup → App signing*, not your local upload key.

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
