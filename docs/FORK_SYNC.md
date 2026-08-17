# Upstream sync pipeline

Every Monday a workflow merges the newest upstream release tag into our branding delta, builds it,
and opens a PR. When the merge is clean, no AI runs and nothing is spent. When it conflicts, a
DeepSeek Harness agent resolves it and explains itself in the PR body. CI rebuilds from scratch
either way, so nothing merges on an agent's say-so.

## Why this merges instead of rebasing

The original design called for rebasing our branding commits onto each new upstream tag. That works
for a patch series that gets regenerated and force-pushed, which is how distro packaging and kernel
topic branches work. It does not work here, for three reasons.

Our delta isn't a linear stack. `main` contains 161 merge commits, most inherited from upstream and
one of our own. Rebasing across them either flattens history or stops on every one.

We publish releases. `v0.0.1` points at a commit in `main`'s history. Rebasing `main` weekly would
orphan every release tag and break anyone who cloned the repo.

Merging gets the same result with none of that. `git rerere` works with merges, so a conflict
resolved once still replays for free next week, which was the real motivation for rebasing.

The tradeoff is a messier graph. That's a fair price for tags that keep pointing at real commits.

## What runs, and when

The deterministic job picks the newest upstream `vX.Y.Z` tag, checks whether it is already an
ancestor of `main`, and stops if it is. Otherwise it merges the tag into a branch cut from `main`,
builds `assembleDebug`, pushes, opens a PR and enables auto-merge.

The agent job runs only when that merge conflicts or the build fails afterwards. It recreates the
same failed merge, hands the situation to `dsh` with `.dsh/task-sync.md` as the prompt, and archives
the session as a workflow artifact. If the agent doesn't finish, the workflow opens an issue instead
of failing quietly.

`ci.yml` runs on every PR and is the merge gate. It rebuilds from a clean checkout, runs unit tests
and lint, then greps for the specific pieces of our delta that an upstream merge could silently
revert, and checks the built APK really reports `nyc.masto.android` and `Masto NYC`. It has no
access to the DeepSeek key and never reads the agent's claims.

## Where the containment actually is

Not in the sandbox setting. The agent runs on a throwaway runner with a GitHub App token that is
minted for that run, expires in an hour, and cannot push to `main`, merge, or create releases. The signing keystore is not present in that job at
all; it only exists in the release workflow, which no agent touches.

`git rerere`'s cache is persisted through `actions/cache`. Without that it would be pointless,
because the runner is destroyed after every run.

## Known gaps

`dsh` is a developer preview. Its CLI reference documents `--profile`, `--patch` and the config
dump flags, but not model selection, a turn budget, or a trajectory-log path. So:

- The model is set in `cordis.patch.yml` in the shape the plugin docs imply. It is unverified. If
  the key is wrong, dsh falls back to its default model silently, which changes cost and quality
  without erroring. Run `dsh --profile sync --dump-config` after any version bump and check.
- There is no turn budget. The workflow bounds the run with `timeout-minutes: 45`, which limits
  wall clock and therefore spend, but not the number of turns.
- The session artifact paths (`$DSH_HOME/sessions`, `$DSH_HOME/logs`) are inferred. The upload step
  is set to warn rather than fail if they are empty, so a wrong guess shows up as an empty artifact
  rather than a failed run.

The dsh version is pinned through the `DSH_VERSION` repo variable so a preview release cannot change
behaviour underneath you.
