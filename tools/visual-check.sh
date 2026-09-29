#!/usr/bin/env bash
# Visual checks against a running emulator, for the things unit tests can't see: system bars,
# dialog windows, and anything the platform draws around the app.
#
#   tools/visual-check.sh                 # check against the goldens
#   tools/visual-check.sh --record        # re-record them, then read the diff before committing
#   tools/visual-check.sh --apk path.apk  # use a prebuilt APK instead of assembling debug
#
# Needs an emulator already running and signed in to an account, because every screen worth
# checking is behind a login. adb comes from $ANDROID_SDK_ROOT or $ANDROID_HOME.
#
# Only a cropped region of each screenshot is compared. The full screen would fail on every new
# post in the timeline; the regions are where this fork's changes actually show up, mostly the
# status bar. Full screenshots are still written out for a human to look at.
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"
sdk=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}
adb=${sdk:+$sdk/platform-tools/}adb
api=''  # filled in once a device is there; goldens are per API level
out=build/visual-check
package=nyc.masto.android
record=false
apk=

while [ $# -gt 0 ]; do
	case $1 in
		--record) record=true; shift;;
		--apk) apk=$2; shift 2;;
		*) echo "unknown argument: $1" >&2; exit 2;;
	esac
done

"$adb" get-state >/dev/null 2>&1 || { echo "no device: start an emulator first" >&2; exit 1; }
api=$("$adb" shell getprop ro.build.version.sdk | tr -d '\r')
goldens=tools/visual/goldens/api$api
echo "> device is API $api; goldens in $goldens"
mkdir -p "$out" "$goldens"

if [ -z "$apk" ]; then
	echo "> assembling debug"
	./gradlew assembleDebug -q
	apk=mastodon/build/outputs/apk/debug/mastodon-debug.apk
fi
echo "> installing $apk"
"$adb" install -r "$apk" >/dev/null

# A screenshot of a live phone changes every minute otherwise. Demo mode pins the clock and the
# status bar icons, which is what makes comparing the status bar possible at all.
"$adb" shell settings put global sysui_demo_allowed 1
demo(){ "$adb" shell am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null; }
demo enter
demo clock -e hhmm 1200
demo battery -e level 100 -e plugged false
demo network -e wifi show -e level 4
demo network -e mobile show -e level 4
demo notifications -e visible false
for s in window_animation_scale transition_animation_scale animator_duration_scale; do
	"$adb" shell settings put global $s 0
done

tap(){ # tap the centre of the node with this text or content-desc
	"$adb" shell uiautomator dump /sdcard/ui.xml >/dev/null
	local b
	b=$("$adb" exec-out cat /sdcard/ui.xml | tr '>' '\n' | grep -E "(content-desc|text)=\"$1\"" | head -1 \
		| grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | grep -oE '[0-9]+' | tr '\n' ' ')
	[ -n "$b" ] || { echo "  ! can't find \"$1\" on screen" >&2; return 1; }
	set -- $b
	"$adb" shell input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 ))
	sleep 2
}

screens_checked=0
failures=0

shot(){ # shot <name> <crop x,y,w,h or "full">
	local name=$1
	local crop=$2
	local actual="$out/$name.png"
	local golden="$goldens/$name.png"
	sleep 1
	"$adb" exec-out screencap -p > "$actual"
	screens_checked=$((screens_checked+1))
	# Goldens hold only the region compared, so they stay small and a diff is readable.
	local region="$out/$name-region.png"
	if [ "$crop" = full ]; then
		cp "$actual" "$region"
	else
		python3 tools/visual/compare.py --extract "$crop" "$actual" "$region"
	fi
	if [ "$record" = true ]; then
		cp "$region" "$golden"
		echo "  recorded $name"
		return
	fi
	if [ ! -f "$golden" ]; then
		echo "  ! no golden for $name; run with --record" >&2
		failures=$((failures+1))
		return
	fi
	if python3 tools/visual/compare.py "$region" "$golden" --diff "$out/$name-diff.png"; then
		echo "  ok   $name"
	else
		echo "  FAIL $name  (see $out/$name-diff.png)"
		failures=$((failures+1))
	fi
}

restart(){
	"$adb" shell am force-stop $package
	"$adb" shell am start -n $package/org.joinmastodon.android.MainActivity >/dev/null
	sleep 5
	tap "Don’t allow" >/dev/null 2>&1 || true
}

restart
# Never clears app data: the session on the emulator is the user's, and signing in again is manual.
if "$adb" shell uiautomator dump /sdcard/ui.xml >/dev/null && "$adb" exec-out cat /sdcard/ui.xml | grep -q 'text="Log in"'; then
	echo "> signed out: checking the splash screen only"
	# All fork artwork, with the bars transparent over it.
	shot splash full
	echo "  sign in on the emulator to check the rest"
else
	echo "> signed in: checking the screens this fork changed"
	# Status bar over a normal screen: the fragment paints this strip itself now.
	shot home-status-bar 0,0,1080,120
	tap "My profile" || true
	tap "More options" || true
	tap "View QR code" || true
	# The QR dialog gets its transparent bars from a theme rather than from code. Its particle
	# animation never settles, so only the status bar strip is compared.
	shot qr-dialog-status-bar 0,0,1080,120
	"$adb" shell input keyevent KEYCODE_BACK
	sleep 2

	# Action mode, where the status bar used to be tinted by the window. Reached through a filter
	# draft that is never saved, so nothing is written to the account.
	restart
	if tap "Settings" && tap "@playstoretesting@masto.nyc" && tap "Filters" && tap "Add filter" \
			&& tap "0 muted words or phrases"; then
		for word in alpha beta; do
			tap "Add word" && tap "Word or phrase" && "$adb" shell input text "$word" && tap "Add"
		done
		if tap "More options" && tap "Select"; then
			shot selection-mode-status-bar 0,0,1080,240
		fi
		for _ in 1 2 3 4 5; do "$adb" shell input keyevent KEYCODE_BACK; sleep 1; done
	else
		echo "  ! couldn't reach the filter words screen; skipping action mode"
	fi
fi

demo exit
echo
echo "$screens_checked screens, $failures failing. Images in $out."
[ "$failures" -eq 0 ]
