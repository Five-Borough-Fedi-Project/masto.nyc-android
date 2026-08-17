# Recorded conflict resolutions

`git rerere` writes resolved conflicts here so the same conflict resolves itself next time. The sync
workflow copies this directory into `.git/rr-cache` before merging, and copies new resolutions back
out after the agent succeeds.

It is committed rather than kept in `actions/cache` because GitHub deletes cache entries not
accessed for seven days, and the sync cron is weekly. The cache would have survived only by luck,
and the failure mode is invisible: the agent just runs again and bills you.

Resolving a conflict by hand? Do it with `git config rerere.enabled true`, then copy
`.git/rr-cache/.` into here and commit it, so the next sync gets it for free.
