#!/usr/bin/env bash
# Shared emulator plumbing for tools/smoke-test.sh and tools/visual-check.sh. Source it, don't run
# it. adb comes from $ANDROID_SDK_ROOT or $ANDROID_HOME, or whatever is on PATH.

package=${package:-nyc.masto.android}
activity=$package/org.joinmastodon.android.MainActivity
_sdk=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}
adb=${_sdk:+$_sdk/platform-tools/}adb
_ui_dump=${TMPDIR:-/tmp}/masto-ui-$$.xml

require_device(){
	"$adb" get-state >/dev/null 2>&1 || { echo "no device: start an emulator first" >&2; exit 1; }
}

device_api(){ "$adb" shell getprop ro.build.version.sdk | tr -d '\r'; }

# Pulls the current window into a file the other helpers read. Everything below works off one
# dump, so a screen is only inspected once per refresh.
ui_refresh(){
	"$adb" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || return 1
	"$adb" exec-out cat /sdcard/ui.xml > "$_ui_dump"
}

# bounds of the first node whose text or content-desc matches $1, as "x1 y1 x2 y2". $1 goes to
# grep -E, so an exact string works and so does a pattern, which is how a screen naming the
# signed-in account is reached without hardcoding which account that is.
ui_bounds(){
	tr '>' '\n' < "$_ui_dump" \
		| grep -E "(content-desc|text)=\"$1\"" | head -1 \
		| grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | grep -oE '[0-9]+' | tr '\n' ' '
}

ui_has(){ grep -qE "(content-desc|text)=\"$1\"" "$_ui_dump"; }

# Waits for a node with this text to appear. ui_has is valid afterwards.
ui_wait(){
	local want=$1 timeout=${2:-10} i=0
	while [ "$i" -lt "$timeout" ]; do
		ui_refresh && ui_has "$want" && return 0
		i=$((i+1)); sleep 1
	done
	return 1
}

tap(){
	ui_refresh || return 1
	local b; b=$(ui_bounds "$1")
	[ -n "$b" ] || { echo "  ! can't find \"$1\" on screen" >&2; return 1; }
	# shellcheck disable=SC2086
	set -- $b
	"$adb" shell input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 ))
	sleep 2
}

long_press(){
	ui_refresh || return 1
	local b; b=$(ui_bounds "$1")
	[ -n "$b" ] || { echo "  ! can't find \"$1\" on screen" >&2; return 1; }
	# shellcheck disable=SC2086
	set -- $b
	local x=$(( ($1+$3)/2 )) y=$(( ($2+$4)/2 ))
	"$adb" shell input swipe $x $y $x $y 800
	sleep 2
}

app_restart(){
	"$adb" shell am force-stop $package
	"$adb" shell am start -n $activity >/dev/null
	sleep "${1:-5}"
}

# Pins the clock and the status bar icons so a screenshot of a live phone stops changing.
demo_mode_on(){
	"$adb" shell settings put global sysui_demo_allowed 1
	local demo=(am broadcast -a com.android.systemui.demo -e command)
	"$adb" shell "${demo[@]}" enter >/dev/null
	"$adb" shell "${demo[@]}" clock -e hhmm 1200 >/dev/null
	"$adb" shell "${demo[@]}" battery -e level 100 -e plugged false >/dev/null
	"$adb" shell "${demo[@]}" network -e wifi show -e level 4 >/dev/null
	"$adb" shell "${demo[@]}" network -e mobile show -e level 4 >/dev/null
	"$adb" shell "${demo[@]}" notifications -e visible false >/dev/null
	for s in window_animation_scale transition_animation_scale animator_duration_scale; do
		"$adb" shell settings put global $s 0
	done
}

demo_mode_off(){
	"$adb" shell am broadcast -a com.android.systemui.demo -e command exit >/dev/null
}

signed_out(){ ui_refresh && ui_has "Log in"; }

# Light or dark. Both scripts restore what they found, since this is a real device setting.
night_mode(){ "$adb" shell cmd uimode night "$1" >/dev/null; sleep 3; }
night_mode_now(){ "$adb" shell cmd uimode night | tr -d '\r' | sed 's/.*: *//'; }

# Rotation needs auto-rotate off, or the app springs back.
rotate(){
	"$adb" shell settings put system accelerometer_rotation 0
	"$adb" shell settings put system user_rotation "$1"
	sleep 3
}
rotate_restore(){
	"$adb" shell settings put system user_rotation 0
	"$adb" shell settings put system accelerometer_rotation 1
}

# "<width> <height>" of the display, which the crop regions are derived from so landscape and a
# different device don't need separate hardcoded numbers.
screen_size(){ "$adb" shell wm size | tr -d '\r' | sed 's/.*: *//' | tr 'x' ' '; }
