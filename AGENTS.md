# Conventions for agents working in this repo

Read `FORK.md` before changing anything. It describes what this fork changes and why, and most of
the rules below are consequences of it.

This is a fork of `mastodon/mastodon-android` with a deliberately small delta. The goal is that
pulling upstream stays cheap, so the guiding question for any change is not "is this good code" but
"does this add merge surface". Prefer new files in
`mastodon/src/main/java/org/joinmastodon/android/fork/` over edits to upstream files. When an
upstream file has to change, keep the edit to a line or two that reads a value from `ForkConfig`.

Don't delete upstream files, even unreachable ones. Deleting a file upstream still maintains turns
every future change to it into a modify/delete conflict.

Don't touch `versionCode` or `versionName` in `mastodon/build.gradle`. They stay upstream's on
purpose; `ci_version.gradle` overrides them at release time from the git tag.

Mark non-obvious edits to upstream files with a `// masto.nyc fork:` comment.

Release tags are exactly `vX.Y.Z`. Suffixes break the in-app updater silently.

## What you can't do

Agents in this repo cannot merge pull requests, create tags or releases, push to `main`, or touch
signing configuration. The signing keystore is only present in the release workflow. If a task seems
to require one of these, say so rather than attempting it.

## Verifying your own work

`./gradlew assembleDebug` is the minimum bar. `ci.yml` will rebuild from clean, run lint and unit
tests, grep for the parts of the delta that a bad merge silently reverts, and check the built APK
still reports `nyc.masto.android` and `Masto NYC`. Claiming a build passed when it didn't only
wastes a reviewer's time, since CI re-derives it anyway.
