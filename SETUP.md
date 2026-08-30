# Setting up the upstream sync pipeline

What you have to do by hand before the weekly sync can run. Nothing here is optional except where
it says so.

## 1. GitHub App

The original design called for a bot account with a fine-grained PAT. A GitHub App is better on
every axis: no second user account to create, license or hand an email address, no token expiry to
babysit, permissions scoped to this repo, and a clear `[bot]` identity in the UI.

It is also not optional in the way it first appears. The default `GITHUB_TOKEN` cannot be used here,
because events it creates do not trigger other workflows, so a PR it opened would never run
`ci.yml`. Losing that means losing the verification gate, which is the whole point.

Org settings → Developer settings → GitHub Apps → New GitHub App.

- Name: `masto-nyc-sync`
- Homepage: the repo URL is fine
- Uncheck Webhook Active. Nothing needs to call back.
- Repository permissions: Contents read/write, Pull requests read/write, Issues read/write
- Where can this be installed: only this account

Create it, then Install App on `masto.nyc-android` only. Note the App ID, and generate a private
key, which downloads a `.pem`.

- Repository variable `SYNC_APP_ID` = the App ID
- Repository secret `SYNC_APP_PRIVATE_KEY` = the whole contents of the `.pem`, including the
  BEGIN and END lines

**A correction to the original design.** It said to scope the agent's write access to `sync/*`
branches through the token. Neither a PAT nor an App installation token can do that; both scope to
repositories and permission types, with no branch patterns. Branch-level restriction comes from a
ruleset instead, which is step 4. Until that ruleset exists the app can push to `main`, so do step 4
before you enable the cron.

## 2. Secrets

| Secret | Used by | Notes |
| --- | --- | --- |
| `SYNC_APP_PRIVATE_KEY` | sync, repair | The App's `.pem` from step 1 |
| `LLM_API_KEY` | agent only | DeepSeek API key. Never exposed to `ci.yml` |
| `KEYSTORE_FILE` | release | Already set |
| `KEYSTORE_PASSWORD` | release | Already set |

Repository variables:

| Variable | Default | Notes |
| --- | --- | --- |
| `SYNC_APP_ID` | none, required | The App ID from step 1 |
| `DSH_VERSION` | `0.1.0-rc.6` | Pin. dsh is pre-1.0 and ships release candidates |
| `LLM_BASE_URL` | `https://api.deepseek.com` | Set to change inference vendor |
| `LLM_MODEL` | `deepseek-v4-flash` | Must match whatever the base URL expects |

Both LLM variables exist so the vendor is a settings change rather than a code change. To move the
billing relationship to a New York company later, set them to `https://router.huggingface.co/v1`
and `deepseek-ai/DeepSeek-V4-Flash`. Same model, same harness. Check what caching the routed
partner offers before assuming the cost stays the same.

## 3. Repo settings

Settings → General → Pull Requests: turn on **Allow auto-merge**. Without it the clean path opens a
PR that then sits there, and the "zero human action" property is lost.

## 4. Ruleset on `main`

Settings → Rules → Rulesets → New branch ruleset, targeting `main`:

- Restrict deletions
- Block force pushes
- Require a pull request before merging
- Require status checks to pass, selecting the `build` job from `CI`

This is also what stops the bot pushing to `main`, since the token itself can't be limited by
branch.

## 5. Copilot review, and an honest caveat

The design asked for required GitHub Copilot code review, so an OpenAI-family model reviews
DeepSeek's work. Two things to know before relying on it:

It needs a paid Copilot plan that includes code review. This is a recurring cost beyond the DeepSeek
tokens, and the original brief listed DeepSeek as the only recurring AI spend.

More importantly, **Copilot code review leaves comments; it does not submit an approving review that
satisfies "required approvals"**. So you have to pick:

- Require 0 approvals and enable automatic Copilot review. Copilot's comments are advisory, CI is
  the real gate, and the clean path merges with no human involved. This matches the stated goal.
- Require 1 approval. You get a human check on every sync, and the "zero human action" property
  goes away.

The first is what the pipeline assumes. Choose the second if you would rather look at every upstream
merge yourself, which is a reasonable thing to want for the first few months.

## 6. Test it before trusting the cron

Run each of these from the Actions tab and confirm the result, in order. Don't skip to the cron.

Sync with an already-merged tag: `workflow_dispatch` with tag `v2.13.2`. Expect it to stop at
"already an ancestor of main" without opening anything.

Sync with a real tag: `workflow_dispatch` with the newest tag and `force` ticked. Expect a clean
merge, a green build, a PR, and no agent job. Check the Actions log shows the agent job skipped,
which is how you confirm you aren't being billed on the routine path.

Force the agent path: temporarily commit a conflicting change to a file upstream also touches, then
dispatch again. Expect the agent job to run, a PR with a written explanation, and a session artifact
on the run. Read that PR body carefully. It is your only view into what the model actually did.

Force a failure: revoke `LLM_API_KEY` and dispatch. Expect an issue titled "Upstream sync
failed", not a silent skip.

## 7. What to watch in the first month

Whether the clean path really is clean. If most weeks escalate to the agent, the delta is too
entangled with upstream files and the fix is to shrink it, not to spend more tokens.

Whether resolutions replay. The second time a similar conflict appears, rerere should handle it and
the agent should not run. If it runs anyway, check that the agent's PR actually committed something
into `.rerere/`. That directory is the whole replay mechanism, and an empty one means every
conflict is paid for twice.

What the agent actually changed. Read the diffs for the first several agent PRs even if CI is green.
CI proves the app builds and is still branded; it cannot prove the resolution was sensible.

## Cost

The routine path spends nothing. Only conflicting weeks call the model.

`deepseek-v4-flash` is $0.14 per million input tokens and $0.28 per million output, with cached
input at $0.0028. A conflict resolution reading a handful of files is well under a million tokens,
so a conflicting week costs cents. The Copilot plan in step 5, if you enable it, will cost more than
the model does.

## Publishing to Google Play

The first upload cannot be automated. Google's Play Developer API refuses to operate on an app that
has never had a build uploaded through the Console, and fastlane reports that as a confusing
"app not found". Everything below is in order, and steps 1 to 4 are one-time.

### 1. Create the app in Play Console

All apps, Create app. Name it Masto NYC, set default language, App, and Free. The package name is
fixed by the first upload, so it is worth being sure it is `nyc.masto.android` before you upload.

### 2. Clear the declarations

Play blocks publishing to any track, including internal testing, until these are done: privacy
policy URL, app access, ads, content rating questionnaire, target audience, data safety, government
apps, and financial features. Data safety is the slow one, because it asks exactly what the app
collects and transmits.

The privacy policy URL is `https://masto.nyc/privacy-policy`, matching `values/urls.xml`.

### 3. Build a bundle and upload it by hand

Play wants an AAB, not an APK, for a new app.

    RELEASE_TAG=v0.1.0 ./gradlew bundleRelease \
      -Pandroid.injected.signing.store.file=$HOME/keys/masto-nyc-release.jks \
      -Pandroid.injected.signing.store.password=... \
      -Pandroid.injected.signing.key.alias=key0 \
      -Pandroid.injected.signing.key.password=...

`RELEASE_TAG` is what sets the version. Without it the build keeps upstream's `versionCode 189`
and `versionName 2.13.2`, and prints a notice saying so. Uploading that would show your app as
version 2.13.2 in Play and burn 189 as the permanent floor, after which any later tag deriving a
lower code is rejected forever.

Check the build output says `ci_version: v0.1.0 -> versionName 0.1.0, versionCode 1000` before you
upload anything. The output is in `mastodon/build/outputs/bundle/release/`.

Upload it to Internal testing. That upload is what activates the API for this app.

### 4. Service account for the API

Play Console, Setup, API access. Link a Google Cloud project, create a service account, then grant
it access in Play Console. Release manager is enough; it does not need account-level admin.

Download the JSON key and store the entire file contents as the `GOOGLE_SERVICE_ACCOUNT_KEY`
repository secret.

### 5. Track and release status

`build_and_deploy.yml` passes two repository variables through to fastlane:

| Variable | Default | Notes |
| --- | --- | --- |
| `PLAY_TRACK` | `internal` | `internal`, `alpha`, `beta` or `production` |
| `PLAY_RELEASE_STATUS` | `draft` | `draft` or `completed` |

Both defaults are deliberate. Upstream's lane specified no track, which means production, and the
store listing still carries upstream's screenshots.

More importantly, until this app has had one production release Play treats it as a draft app and
the API refuses anything that is not a draft release:

    Only releases with status draft may be created on draft app.

A manual upload to internal testing does not clear that. Once the listing is ready and a production
release exists, set `PLAY_TRACK=production` and `PLAY_RELEASE_STATUS=completed`.

### 6. After that, releases publish themselves

`build_and_deploy.yml` runs on a published GitHub release and pushes to Play, at the same time as
`release-apk.yml` attaches the APK to the release. Both derive the version from the tag.

### Play App Signing breaks App Links, and does it silently

With App Signing on, which is effectively mandatory for new apps, Google re-signs your bundle with
their key. The APK users install is signed by Google, not by `masto-nyc-release.jks`. That means the
fingerprint currently published in `assetlinks.json` is wrong for every Play install, while
remaining correct for APKs from GitHub releases. Nothing errors. Links just stop opening in the app
for anyone who installed from Play.

Copy the SHA-256 fingerprints from Play Console, Release, Setup, App signing into the
`PLAY_APP_SIGNING_SHA256` repository secret. `deploy-assetlinks.yml` publishes them alongside the
release key's own fingerprint, so sideloaded and Play installs both keep working.

Take them from the **app signing key** section, not the upload key. Google re-signs your bundle, so
the upload key never signs anything a user installs. Using it produces a file that looks right and
verifies nothing.

If quantum-ready hybrid signing is enabled, Play shows three fingerprints rather than one: a new
classical key and a PQC key for newer devices, plus the original classical key for older ones.
Register all of them. The secret accepts a list separated by commas, spaces or newlines, and the
workflow validates the format and refuses to deploy if none of them parse.

The PQC key is not a replacement for the classical one. `sha256_cert_fingerprints` is an array, an
entry that matches nothing is inert, and leaving one out breaks App Links for whichever slice of
devices used that key.

### Store listing

`icon.png` and `featureGraphic.png` are current. The eight screenshots are still upstream's and show
the old branding, so retake them before going public. CI does not upload any of these: both fastlane
lanes pass `skip_upload_images` and the workflows set `SUPPLY_SKIP_UPLOAD_METADATA`, so the listing
is managed by hand in the Console for now.
