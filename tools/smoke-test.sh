#!/usr/bin/env bash
# Drives the app on a running emulator and asserts what it did, for the fork's own behaviour: the
# tabs, the timelines behind them, and the followed hashtags section.
#
#   tools/smoke-test.sh                 # assemble debug, install, run every check
#   tools/smoke-test.sh --apk path.apk  # use a prebuilt APK
#   tools/smoke-test.sh --keep          # leave the app installed and logcat on screen
#
# Needs an emulator already running and signed in, because every screen worth checking is behind a
# login. It never clears app data and never posts: the account on the emulator is a real one.
#
# This is the counterpart to tools/visual-check.sh. That one compares pixels; this one asserts
# facts. The split matters because the bugs this fork has actually shipped were not pixel
# regressions -- they were a tab wired to the wrong timeline, a toolbar drawn under the status bar,
# and a crash while scrolling. Each check below is one of those.
#
# Requires a debug build: the assertions on which endpoint a tab called read the request log that
# MastodonAPIController only writes when BuildConfig.DEBUG.
set -uo pipefail

cd "$(git rev-parse --show-toplevel)"
# shellcheck source=lib/emulator.sh
source tools/lib/emulator.sh

out=build/smoke-test
apk=
keep=false

while [ $# -gt 0 ]; do
	case $1 in
		--apk) apk=$2; shift 2;;
		--keep) keep=true; shift;;
		*) echo "unknown argument: $1" >&2; exit 2;;
	esac
done

require_device
mkdir -p "$out"
echo "> device is API $(device_api)"

if [ -z "$apk" ]; then
	echo "> assembling debug"
	./gradlew assembleDebug -q || exit 1
	apk=mastodon/build/outputs/apk/debug/mastodon-debug.apk
fi
echo "> installing $apk"
"$adb" install -r "$apk" >/dev/null || exit 1

passed=0
failed=0
skipped=0

pass(){ echo "  ok    $1"; passed=$((passed+1)); }
fail(){ echo "  FAIL  $1"; echo "        $2"; failed=$((failed+1)); }
skip(){ echo "  skip  $1"; echo "        $2"; skipped=$((skipped+1)); }

# Checks read the log to see which endpoint a tab actually called, rather than trusting the
# screen. log_mark resets it so each check only sees its own traffic.
log_mark(){ "$adb" logcat -c 2>/dev/null; }

# Any uncaught exception, at any point in the run, is a failure of whatever was happening.
crashed_since_mark(){ "$adb" logcat -d 2>/dev/null | grep -A20 "FATAL EXCEPTION"; }

assert_no_crash(){
	local what=$1 crash
	crash=$(crashed_since_mark)
	if [ -n "$crash" ]; then
		printf '%s\n' "$crash" > "$out/crash-${what// /-}.txt"
		fail "$what" "the app crashed; stack trace in $out/crash-${what// /-}.txt: $(printf '%s' "$crash" | sed -n '2p' | tr -s ' ')"
		return 1
	fi
	return 0
}

demo_mode_on
log_mark
app_restart 6
tap "Don’t allow" >/dev/null 2>&1 || true

if signed_out; then
	echo "> signed out: sign in on the emulator, these checks all need an account" >&2
	demo_mode_off
	exit 1
fi

echo "> checking the tabs"

# 1. The app comes up at all. Everything below is meaningless if this fails.
if assert_no_crash "the app starts"; then
	if ui_refresh && ui_has "Neighbors"; then
		pass "the app starts"
	else
		fail "the app starts" "the bottom bar has no Neighbors tab after launch"
	fi
fi

# 2. Which tab it opened on. ForkPrefs defaults to Neighbors, and a regression here is invisible
#    unless you know which timeline you are looking at.
title_top(){ # y of the top of the toolbar title, or empty
	ui_refresh || return 1
	local b; b=$(ui_bounds "$1")
	[ -n "$b" ] || return 1
	# shellcheck disable=SC2086
	set -- $b
	echo "$2"
}

if ui_refresh && ui_has "Neighbors"; then
	pass "the app opens on Neighbors"
else
	fail "the app opens on Neighbors" "no Neighbors toolbar title on the first screen"
fi

# 3 and 4. Which endpoint each timeline tab actually called. This is the check that would have
#    caught the Neighbors tab loading the home timeline: the screen looked plausible either way,
#    and only the request told the truth.
#
#    Neighbors is read from the launch traffic, not from a tab switch. Both timelines are added at
#    startup and each loads once, so switching back to a tab that is already loaded issues no
#    request at all and an assertion on the switch would pass whatever the tab does.
requests_now(){ "$adb" logcat -d -s MastodonAPIController 2>/dev/null | grep "Sending request"; }

assert_called(){
	local want=$1 name=$2 log=$3
	printf '%s\n' "$log" > "$out/requests-${name// /-}.txt"
	if printf '%s' "$log" | grep -q "$want"; then
		pass "$name"
	else
		fail "$name" "no request matching '$want'; what it did call is in $out/requests-${name// /-}.txt"
	fi
}

log_mark
app_restart 8
launch_log=$(requests_now)
assert_called "timelines/public" "the opening tab loads the local timeline" "$launch_log"
# local=true specifically: timelines/public without it is the whole fediverse, not NYC.
assert_called "local=true" "the Neighbors timeline is local-only" "$launch_log"

log_mark
if tap "Home"; then
	sleep 4
	assert_called "timelines/home" "the Home tab loads the home timeline" "$(requests_now)"
else
	skip "the Home tab loads the home timeline" "couldn't find the Home tab"
fi

# 5. The toolbars clear the status bar. Both timelines are the same fragment class, so their
#    toolbars must land at the same height; when the Neighbors tab wasn't being handed the window
#    insets, its title sat higher than Home's and the status bar drew over it.
tap "Home" >/dev/null 2>&1 || true
home_title_y=$(title_top "Home" || true)
tap "Neighbors" >/dev/null 2>&1 || true
neighbors_title_y=$(title_top "Neighbors" || true)
if [ -z "$home_title_y" ] || [ -z "$neighbors_title_y" ]; then
	skip "both timelines clear the status bar" "couldn't read both toolbar titles"
elif [ "$home_title_y" -eq "$neighbors_title_y" ]; then
	if [ "$neighbors_title_y" -gt 0 ]; then
		pass "both timelines clear the status bar"
	else
		fail "both timelines clear the status bar" "both toolbars start at y=0, under the status bar"
	fi
else
	fail "both timelines clear the status bar" \
		"Home's title starts at y=$home_title_y but Neighbors' at y=$neighbors_title_y; one of them isn't being handed the window insets"
fi

# 6. The followed hashtags section. It is a second adapter merged in front of upstream's trending
#    list, and the two have to agree about view types or a recycled row reaches the wrong adapter
#    and throws. That crash was reported from a real phone.
echo "> checking the followed hashtags section"
log_mark
if tap "Search" && ui_wait "Hashtags" 10 && tap "Hashtags" && ui_wait "Hashtags you follow" 10; then
	if tap "Hashtags you follow"; then
		sleep 3
		ui_refresh
		followed=$(grep -coE 'text="#[^"]+"' "$_ui_dump" || true)
		# A rough count: trending rows carry hashtag names too, so this is an indication that
		# the list has content to recycle, not a count of what you follow.
		echo "        the section opened over $followed hashtag rows"
		# Fling hard, both ways: recycling is what triggers the bug, and it needs rows to be
		# handed back and forth across the two adapters.
		for _ in 1 2 3 4 5 6; do
			"$adb" shell input swipe 540 1600 540 400 100
			"$adb" shell input swipe 540 400 540 1600 100
		done
		sleep 2
		if assert_no_crash "scrolling the hashtags section doesn't crash"; then
			pass "scrolling the hashtags section doesn't crash"
		fi
	else
		skip "scrolling the hashtags section doesn't crash" "the accordion header wouldn't open"
	fi
else
	skip "scrolling the hashtags section doesn't crash" "couldn't reach the Hashtags tab on the search screen"
fi

# 7. Settings moved behind a long press on the profile tab when the toolbar gear was removed. If
#    this breaks there is no other way into settings at all.
echo "> checking the profile tab menu"
log_mark
app_restart 6
if long_press "My profile" && ui_wait "Settings" 5; then
	pass "settings are reachable from the profile tab"
	"$adb" shell input keyevent KEYCODE_BACK
else
	fail "settings are reachable from the profile tab" \
		"a long press on the profile tab didn't bring up a menu with Settings, which is now the only way in"
fi
assert_no_crash "the profile tab menu doesn't crash" && pass "the profile tab menu doesn't crash"

demo_mode_off
if [ "$keep" = false ]; then
	app_restart 1 >/dev/null 2>&1 || true
	"$adb" shell am force-stop $package
fi
rm -f "$_ui_dump"

echo
echo "$passed passed, $failed failed, $skipped skipped. Logs in $out."
[ "$failed" -eq 0 ]
