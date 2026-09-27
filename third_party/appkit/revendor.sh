#!/usr/bin/env bash
# Re-vendors third_party/appkit onto the appkit version mastodon/build.gradle asks for, with the
# patches in patches/ applied. Run it from the repo root when the build fails with a version
# mismatch, i.e. after an upstream merge bumped appkit:
#
#   third_party/appkit/revendor.sh            # target the version build.gradle asks for
#   third_party/appkit/revendor.sh 1.5.4      # or name one
#
# Then read the diff, build, and commit. See README.md for the wider picture.
set -euo pipefail

repo_root=$(git rev-parse --show-toplevel)
cd "$repo_root"
vendor_dir=third_party/appkit
upstream=https://github.com/grishka/appkit

if [ -n "$(git status --porcelain -- "$vendor_dir")" ]; then
	echo "error: $vendor_dir has uncommitted changes. Commit or stash them first, so the diff this" >&2
	echo "       produces is only the re-vendoring." >&2
	exit 1
fi

target=${1:-$(grep -oE "me\.grishka\.appkit:appkit:[0-9][0-9.]*" mastodon/build.gradle | head -1 | cut -d: -f3)}
if [ -z "$target" ]; then
	echo "error: no appkit version in mastodon/build.gradle, and none given on the command line." >&2
	echo "       If upstream dropped appkit, delete $vendor_dir instead. See README.md." >&2
	exit 1
fi
echo "target appkit version: $target"

clone=$(mktemp -d)
trap 'rm -rf "$clone"' EXIT
echo "cloning $upstream"
git clone -q "$upstream" "$clone"

# appkit publishes no tags, so the version lives in its own build.gradle. Walk that file's history
# newest first and take the first commit that declares the version we want.
commit=""
while read -r c; do
	if git -C "$clone" show "$c:appkit/build.gradle" 2>/dev/null | grep -q "version *= *'$target'"; then
		commit=$c
		break
	fi
done < <(git -C "$clone" log --format=%H -- appkit/build.gradle)

if [ -z "$commit" ]; then
	echo "error: no commit in appkit declares version $target. Versions seen recently:" >&2
	git -C "$clone" log --format=%H -20 -- appkit/build.gradle | while read -r c; do
		git -C "$clone" show "$c:appkit/build.gradle" 2>/dev/null | grep -oE "version *= *'[0-9.]+'" || true
	done | sort -u >&2
	exit 1
fi
echo "appkit $target is commit $(git -C "$clone" log --format='%h %ad %s' --date=short -1 "$commit")"

git -C "$clone" checkout -q "$commit"
if ! git -C "$clone" am "$repo_root/$vendor_dir"/patches/*.patch; then
	echo >&2
	echo "error: the patches don't apply cleanly to appkit $target. The clone is left at" >&2
	echo "       $clone" >&2
	echo "       Resolve there ('git am --continue'), re-export with" >&2
	echo "       'git format-patch -1 -o $repo_root/$vendor_dir/patches', then run this again." >&2
	trap - EXIT
	exit 1
fi

echo "copying sources"
rm -rf "$vendor_dir/src"
cp -r "$clone/appkit/src" "$vendor_dir/src"

# AGP rejects the package attribute in a library manifest; the module declares its namespace in
# build.gradle instead. This is a build difference, not part of the patch.
printf '<manifest xmlns:android="http://schemas.android.com/apk/res/android">\n</manifest>\n' \
	> "$vendor_dir/src/main/AndroidManifest.xml"

short=$(git -C "$clone" rev-parse --short "$commit")
python3 - "$vendor_dir/vendored-version.txt" "$target" "$short" <<'PY'
import sys
path, version, commit = sys.argv[1:4]
out = []
for line in open(path):
    if line.startswith('version='):
        line = f'version={version}\n'
    elif line.startswith('commit='):
        line = f'commit={commit}\n'
    out.append(line)
open(path, 'w').writelines(out)
PY

echo
echo "done: $vendor_dir is now appkit $target ($short) plus $(ls "$vendor_dir"/patches/*.patch | wc -l) patch(es)."
echo "next: review 'git diff --stat $vendor_dir', run './gradlew assembleDebug', and commit."
