#!/usr/bin/env bash
# Actual PackageInstaller.Session + PendingIntent callback exercise on an AVD only.
set -Eeuo pipefail
CANDIDATE="${1:?usage: test-android-installer-session.sh <test-signed-thor-apk> <harness-apk>}"
HARNESS="${2:?harness APK is required}"
SERIAL="${ANDROID_SERIAL:-emulator-5554}"
TARGET="com.thor.displaypowertest"
AGENT="org.jestylabs.thorfix.installerharness"
COMPONENT="$AGENT/.HarnessActivity"
case "$SERIAL" in emulator-*) ;; *) echo "Refusing non-emulator serial: $SERIAL" >&2; exit 2 ;; esac
adb_avd() { adb -s "$SERIAL" "$@"; }
adb_avd wait-for-device
adb_avd emu avd name >/dev/null
test -f "$CANDIDATE" && test -f "$HARNESS"

fail_logs() {
  echo "Installer harness failure. Recent relevant emulator logcat:" >&2
  adb_avd logcat -d -s ThorInstallerCI:E AndroidRuntime:E PackageInstaller:W PackageManager:W \
    | tail -n 60 >&2 || true
}
trap fail_logs ERR

# The test target must be the disposable-key app from the earlier smoke, not a
# production installation. CI's emulator is fresh and isolated.
adb_avd shell pm path "$TARGET" | grep -q '^package:'
adb_avd install -r "$HARNESS"
adb_avd shell am start -W -n "$COMPONENT" --es mode init >/dev/null

# On this ephemeral AVD only, grant the test harness permission to request
# installs, while still demanding system UI confirmation for each session.
adb_avd shell appops set "$AGENT" REQUEST_INSTALL_PACKAGES allow

DEST="/sdcard/Android/data/$AGENT/files/candidate.apk"
adb_avd push "$CANDIDATE" "$DEST" >/dev/null

prefs() {
  adb_avd shell run-as "$AGENT" cat shared_prefs/installer_ci.xml 2>/dev/null | tr -d '\r'
}
assert_state() {
  local key="$1" value="$2" result
  result="$(prefs)"
  grep -Fq "name=\"$key\">$value</string>" <<< "$result" || {
    echo "Unexpected $key (expected $value). Saved diagnostics: $result" >&2
    return 1
  }
}
wait_state() {
  local key="$1" value="$2" tries
  for ((tries=0; tries<30; tries++)); do
    if assert_state "$key" "$value" 2>/dev/null; then return 0; fi
    sleep 1
  done
  assert_state "$key" "$value"
}
last_updated() {
  adb_avd shell dumpsys package "$TARGET" | tr -d '\r' \
    | grep -m 1 -E '^[[:space:]]*lastUpdateTime=' || true
}
# Read-only AVD focus evidence. A BACK keypress alone does not prove that the
# system package confirmation was visible or that Android treated it as refusal.
confirmation_focus() {
  local stage="$1"
  echo "Confirmation focus [$stage] (emulator only):"
  adb_avd shell dumpsys window \
    | grep -E 'mCurrentFocus|mFocusedApp' | head -n 5 || true
  adb_avd shell dumpsys activity activities \
    | grep -E 'topResumedActivity|mResumedActivity' | head -n 5 || true
}
baseline="$(last_updated)"
test -n "$baseline" || { echo "Cannot read baseline lastUpdateTime" >&2; exit 1; }

# Session is written, then abandoned before commit. No install callback should
# be generated, and target package timestamp must remain unchanged.
# Repeat on the *same* Android emulator to expose delayed session removal.
# Each invocation resets abandon_result before creating a new session.
for attempt in 1 2 3 4 5; do
  adb_avd shell am start -W -n "$COMPONENT" --es mode abandon >/dev/null
  wait_state abandon_result ABANDONED
  wait_state abandon_phase CONFIRMED_GONE
  test "$(last_updated)" = "$baseline" || {
    echo "Package unexpectedly changed after session abandon #$attempt" >&2; exit 1;
  }
  echo "PASS: actual Android session $attempt/5 written + abandoned before commit"
done

# Real PackageInstaller.commit with a private PendingIntent result. Demand the
# platform-provided confirmation callback, rather than asserting a synthetic
# mocked intent was delivered.
adb_avd shell am start -W -n "$COMPONENT" --es mode commit >/dev/null
wait_state commit_result COMMIT_CALLED
wait_state callback_result PENDING_USER_ACTION
echo "PASS: PackageInstaller delivered PENDING_USER_ACTION with confirmation Intent"

# Start the *system* install confirmation UI, decline it using BACK, and check
# Android's terminal callback. No automatic approval or production installation.
adb_avd shell am start -W -n "$COMPONENT" --es mode confirm >/dev/null
wait_state confirmation_result OPENED_SYSTEM_UI
sleep 2
confirmation_focus "before BACK"
# Do not send BACK into a tutorial, IME or other window while the installer
# happens to be only the focused Activity behind an overlay.
focused_window="$(adb_avd shell dumpsys window | grep -m 1 'mCurrentFocus=' || true)"
case "$focused_window" in
  *com.android.packageinstaller*) ;;
  *) echo "Refusing denial input: PackageInstaller is not the focused window: $focused_window" >&2; exit 1 ;;
esac
adb_avd shell input keyevent KEYCODE_BACK
sleep 1
confirmation_focus "after BACK"
wait_state callback_result USER_ABORTED
test "$(last_updated)" = "$baseline" || {
  echo "Target APK unexpectedly changed after denying installation" >&2; exit 1;
}
echo "PASS: Android confirmation denied; terminal USER_ABORTED; target not updated"
echo "NOT TESTED: app's in-process receiver, exact CancelCommitGate race, or physical daemon handoff"
