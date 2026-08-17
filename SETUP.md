# Setting up the upstream sync pipeline

What you have to do by hand before the weekly sync can run. Nothing here is optional except where
it says so.

## 1. Bot account and token

Create a second GitHub account for the bot and invite it to the org with write access to this repo
only. GitHub's terms allow machine accounts; don't use your own account, because the point is to
have a token you can scope and revoke without touching your access.

From the bot account, create a fine-grained personal access token scoped to
`Five-Borough-Fedi-Project/masto.nyc-android` with:

- Contents: read and write
- Pull requests: read and write
- Issues: read and write

Save it as the `BOT_PAT` repository secret.

**A correction to the original design.** It said to scope the agent's write access to `sync/*`
branches through the token. Fine-grained PATs cannot do that; they scope to repositories and
permission types, with no branch patterns. Branch-level restriction comes from a ruleset instead,
which is step 4. Until that ruleset exists, `BOT_PAT` can push to `main`, so do step 4 before you
enable the cron.

## 2. Secrets

| Secret | Used by | Notes |
| --- | --- | --- |
| `BOT_PAT` | sync, agent | From step 1 |
| `LLM_API_KEY` | agent only | DeepSeek API key. Never exposed to `ci.yml` |
| `KEYSTORE_FILE` | release | Already set |
| `KEYSTORE_PASSWORD` | release | Already set |

Optional repository variables:

| Variable | Default | Notes |
| --- | --- | --- |
| `DSH_VERSION` | `0.5.3` | Pin. dsh is a developer preview |
| `DSH_PERMISSION_MODE` | `auto` | Non-interactive runs need this |
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
