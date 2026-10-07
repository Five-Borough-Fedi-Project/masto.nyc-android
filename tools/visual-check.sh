#!/usr/bin/env bash
# Visual checks against a running emulator, for the things unit tests can't see: system bars,
# dialog windows, and anything the platform draws around the app.
#
# The counterpart is tools/smoke-test.sh, which asserts facts rather than comparing pixels. Both
# drive the emulator through tools/lib/emulator.sh.
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
# shellcheck source=lib/emulator.sh
source tools/lib/emulator.sh

out=build/visual-check  # goldens are per API level, see below
record=false
apk=

while [ $# -gt 0 ]; do
	case $1 in
		--record) record=true; shift;;
		--apk) apk=$2; shift 2;;
		*) echo "unknown argument: $1" >&2; exit 2;;
	esac
done

require_device
api=$(device_api)
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
demo_mode_on

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

# Crop regions are derived from the display rather than hardcoded, so landscape and a different
# AVD don't each need their own numbers.
read -r SW SH <<<"$(screen_size)"
bar_strip="0,0,$SW,120"
top_strip="0,0,$SW,240"

# Whatever the device was set to before, restored at the end: these are real device settings.
night_was=$(night_mode_now)
restore(){
	night_mode "$night_was" >/dev/null 2>&1 || true
	rotate_restore >/dev/null 2>&1 || true
	demo_mode_off >/dev/null 2>&1 || true
}
trap restore EXIT

# Every screen this fork changed, walked once per theme. The suffix keeps the two sets of goldens
# apart; the theme is in the name because a bar that is right in light and wrong in dark is a real
# and easy regression, and nothing was looking for it.
check_screens(){
	local suffix=$1

	app_restart
	tap "Don’t allow" >/dev/null 2>&1 || true

	# The tab the app opens on. Its toolbar paints the strip behind the status bar itself now, and
	# Neighbors is a fork tab that nothing else checks.
	shot "neighbors-status-bar$suffix" "$bar_strip"

	# The fork's own tab icons, the pigeon and the buildings, with the selected-tab treatment.
	# Only the left two tabs: the notifications badge further along is live and would fail this
	# whenever a notification happened to be waiting.
	shot "fork-tab-icons$suffix" "0,$((SH-220)),440,220"

	if tap "Home"; then
		shot "home-status-bar$suffix" "$bar_strip"
	else
		echo "  ! couldn't find the Home tab; skipping home-status-bar$suffix"
	fi

	# The followed hashtags section, open. A merged adapter in front of upstream's trending list,
	# and the accordion header is the fork's own.
	if tap "Search" && ui_wait "Hashtags" 10 && tap "Hashtags" \
			&& ui_wait "Hashtags you follow" 10 && tap "Hashtags you follow"; then
		# Down to the accordion header and no further. Below it are the hashtags the signed-in
		# account follows and then upstream's trending list, with a live "N people are talking"
		# on every row; pinning those would fail on a golden recorded yesterday. What the rows
		# say is the smoke test's job. The chevron carries the one thing worth pinning here,
		# which is that the section is open.
		shot "followed-hashtags$suffix" "0,0,$SW,640"
	else
		echo "  ! couldn't reach the hashtags accordion; skipping followed-hashtags$suffix"
	fi

	# The sheet behind a long press on the profile tab, which is the only way into settings since
	# the toolbar gear was removed.
	app_restart
	if long_press "My profile" && ui_wait "Settings" 5; then
		# Cropped from just above the Settings row down to the bottom of the screen, rather than a
		# fixed height. A fixed height has to guess where the sheet starts, and guessing a little
		# high catches a strip of the timeline behind it, which changes whenever a post arrives.
		# That slipped through twice before failing on a later run.
		read -r _ settings_top _ _ <<<"$(ui_bounds "Settings")"
		if [ -n "${settings_top:-}" ]; then
			crop_top=$((settings_top-48))
			shot "profile-tab-menu$suffix" "0,$crop_top,$SW,$((SH-crop_top))"
		else
			echo "  ! couldn't locate the Settings row; skipping profile-tab-menu$suffix"
		fi
		"$adb" shell input keyevent KEYCODE_BACK
		sleep 1
	else
		echo "  ! couldn't open the profile tab menu; skipping profile-tab-menu$suffix"
	fi

	# The QR dialog gets its transparent bars from a theme rather than from code. Its particle
	# animation never settles, so only the status bar strip is compared.
	app_restart
	if tap "My profile" && tap "More options" && tap "View QR code"; then
		shot "qr-dialog-status-bar$suffix" "$bar_strip"
		"$adb" shell input keyevent KEYCODE_BACK
		sleep 2
	else
		echo "  ! couldn't reach the QR dialog; skipping qr-dialog$suffix"
	fi

	# Landscape, which is what the orientation work was about: the app used to lock itself to
	# portrait, and Android 16 ignores that lock on large screens. The bottom bar is the honest
	# thing to compare here -- the QR dialog is the screen that drove the orientation work, but
	# its particle animation never settles and a strip of it is not reproducible between runs.
	# QrCodeLayoutTest measures that screen in landscape instead, where nothing is animating.
	app_restart
	rotate 1
	# wm size keeps reporting the physical size after a rotation, so landscape dimensions are the
	# two values swapped rather than something to read back off the device.
	lw=$(( SW > SH ? SW : SH ))
	lh=$(( SW > SH ? SH : SW ))
	shot "tab-bar-landscape$suffix" "0,$((lh-180)),$lw,180"
	rotate 0
	sleep 2

	# Action mode, where the status bar used to be tinted by the window. Reached through a filter
	# draft that is never saved, so nothing is written to the account.
	#
	# Settings is behind the profile tab's long press now, not the home toolbar. This walk was
	# still tapping a toolbar "Settings" that has not existed since that change, so it had been
	# skipping silently -- which is why a skip is loud here.
	app_restart
	# Filters live under the account, so the account row has to be tapped on the way. Matched as a
	# pattern rather than by name, so this doesn't depend on which account is signed in.
	if long_press "My profile" && tap "Settings" && tap "@[^@\"]+@[^\"]+" && tap "Filters" \
			&& tap "Add filter" && tap "0 muted words or phrases"; then
		for word in alpha beta; do
			tap "Add word" && tap "Word or phrase" && "$adb" shell input text "$word" && tap "Add"
		done
		if tap "More options" && tap "Select"; then
			shot "selection-mode-status-bar$suffix" "$top_strip"
		fi
		for _ in 1 2 3 4 5 6; do "$adb" shell input keyevent KEYCODE_BACK; sleep 1; done
	else
		echo "  ! couldn't reach the filter words screen; skipping action mode$suffix"
	fi
}

app_restart
tap "Don’t allow" >/dev/null 2>&1 || true
# Never clears app data: the session on the emulator is the user's, and signing in again is manual.
if signed_out; then
	echo "> signed out: checking the splash screen only"
	# All fork artwork, with the bars transparent over it.
	shot splash full
	echo "  sign in on the emulator to check the rest"
else
	echo "> light theme"
	night_mode no
	check_screens ""
	echo "> dark theme"
	night_mode yes
	check_screens "-dark"
fi

echo
echo "$screens_checked screens, $failures failing. Images in $out."
[ "$failures" -eq 0 ]
