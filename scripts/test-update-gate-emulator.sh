#!/usr/bin/env bash
# Android 13 AVD-only integration: the real UpdateCommitGate class and a
# populated native PackageInstaller session, without touching physical devices.
set -Eeuo pipefail
HARNESS="${1:?usage: test-update-gate-emulator.sh <disposable-harness.apk>}"
SERIAL="${ANDROID_SERIAL:-emulator-5554}"
PKG="org.jestylabs.thorfix.updategateharness"
COMPONENT="$PKG/.GateTestActivity"
case "$SERIAL" in
  emulator-*) ;;
  *) echo "Refusing non-emulator ADB serial: $SERIAL" >&2; exit 2 ;;
esac
adb_avd() { adb -s "$SERIAL" "$@"; }
adb_avd wait-for-device
adb_avd emu avd name >/dev/null
test -f "$HARNESS"
fail_logs() {
  echo "Recent emulator error diagnostics:" >&2
  adb_avd logcat -d -s ThorGateCI:E AndroidRuntime:E PackageInstaller:W \
    | tail -n 70 >&2 || true
}
trap fail_logs ERR

adb_avd install -r "$HARNESS" >/dev/null
adb_avd shell am start -W -n "$COMPONENT" --es mode init >/dev/null
adb_avd shell appops set "$PKG" REQUEST_INSTALL_PACKAGES allow
adb_avd push "$HARNESS" "/sdcard/Android/data/$PKG/files/candidate.apk" >/dev/null
prefs() {
  adb_avd shell run-as "$PKG" cat shared_prefs/update_gate_ci.xml 2>/dev/null | tr -d '\r'
}
assert_state() {
  local key="$1" wanted="$2" value
  value="$(prefs)"
  grep -Fq "name=\"$key\">$wanted</string>" <<< "$value" || {
    echo "Expected $key=$wanted. Current results: $value" >&2
    return 1
  }
}
wait_state() {
  local key="$1" wanted="$2" tries
  for ((tries=0; tries<60; tries++)); do
    if assert_state "$key" "$wanted" 2>/dev/null; then return 0; fi
    if grep -Fq 'name="result">FAIL</string>' <<< "$(prefs)"; then
      echo "Gate harness reported failure: $(prefs)" >&2
      return 1
    fi
    sleep 1
  done
  assert_state "$key" "$wanted"
}
last_updated() {
  adb_avd shell dumpsys package "$PKG" | tr -d '\r' \
    | grep -m 1 -E '^[[:space:]]*lastUpdateTime=' || true
}
baseline="$(last_updated)"
test -n "$baseline" || { echo "Missing initial package timestamp" >&2; exit 1; }
wait_state result READY
adb_avd shell am start -W -n "$COMPONENT" --es mode run >/dev/null
wait_state result PASS
assert_state race_result PASS
assert_state abandon_result PASS
assert_state commit_boundary_result PASS
test "$(last_updated)" = "$baseline" || {
  echo "Test harness was unexpectedly replaced by installer" >&2
  exit 1
}
echo "PASS: production UpdateCommitGate survived 64 races on Android 13"
echo "PASS: populated PackageInstaller session abandoned after accepted Cancel"
echo "PASS: late Cancel rejected after commit boundary; next attempt re-armed"
echo "PASS: no APK replacement (package update timestamp unchanged)"
echo "NOT TESTED: production AppUpdater UI/network, real post-commit callback or Thor hardware"
