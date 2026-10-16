#!/usr/bin/env bash
# Generic-Android integration smoke for Jesty Thor Fix.
# Deliberately refuses physical devices. Never calls a vendor Binder, DRM, or CPU command.
set -Eeuo pipefail

APK="${1:?usage: test-android-emulator-smoke.sh <test-signed-apk> <version>}"
EXPECTED_VERSION="${2:?expected app version required}"
SERIAL="${ANDROID_SERIAL:-emulator-5554}"
PACKAGE="com.thor.displaypowertest"
ACTIVITY="$PACKAGE/.MainActivity"

case "$SERIAL" in
  emulator-*) ;;
  *) echo "ERROR: refusing a non-emulator ADB serial: $SERIAL" >&2; exit 2 ;;
esac
if [[ ! -f "$APK" ]]; then
  echo "ERROR: APK not found: $APK" >&2
  exit 2
fi

adb_device() { adb -s "$SERIAL" "$@"; }
diagnostics() {
  echo "Emulator lifecycle smoke FAILED; bounded AndroidRuntime/app log follows:" >&2
  adb_device logcat -d -s AndroidRuntime:E ThorDisplayAuto:W ThorDisplayUi:W ThorDisplayUpdate:W     | tail -n 70 >&2 || true
}
trap diagnostics ERR

adb_device wait-for-device
# Verify the ADB target is an emulator, not just a serial named like one.
adb_device emu avd name >/dev/null

bridge="$(adb_device shell service check PServerBinder 2>&1 | tr -d '\r')"
if ! grep -Eqi 'not found' <<< "$bridge"; then
  echo "ERROR: unexpected PServerBinder check; this test needs a bridge-free emulator: $bridge" >&2
  exit 1
fi

check_installed() {
  local paths version
  paths="$(adb_device shell pm path "$PACKAGE" | tr -d '\r')"
  if ! grep -q '^package:' <<< "$paths"; then
    echo "ERROR: app package is not installed" >&2
    return 1
  fi
  version="$(adb_device shell dumpsys package "$PACKAGE" | tr -d '\r')"
  if ! grep -Fq "versionName=$EXPECTED_VERSION" <<< "$version"; then
    echo "ERROR: installed app version differs from $EXPECTED_VERSION" >&2
    return 1
  fi
}

launch_and_check() {
  local phase="$1" output pid
  output="$(adb_device shell am start -W -n "$ACTIVITY" 2>&1 | tr -d '\r')"
  echo "[$phase] $output"
  if grep -Eqi '(^Error:|Exception|unable to resolve)' <<< "$output"; then
    echo "ERROR: activity launch rejected during $phase" >&2
    return 1
  fi
  sleep 3
  pid="$(adb_device shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' || true)"
  if [[ -z "$pid" ]]; then
    echo "ERROR: app process disappeared after $phase" >&2
    return 1
  fi
  echo "[$phase] app process alive; no vendor Binder required"
}

adb_device logcat -c
echo "Installing test-key-signed APK on isolated Android emulator"
adb_device install -r "$APK"
check_installed
launch_and_check "initial install"

# MainActivity lifecycle without forcing changes to system display properties.
adb_device shell input keyevent KEYCODE_HOME
launch_and_check "resume after Home"

# Android package replacement of the running process with the same test APK.
# This is NOT the in-app PackageInstaller flow and does NOT test privileged handoff.
adb_device install -r "$APK"
check_installed
launch_and_check "replace while running"

# A process-lifecycle check only; force-stop is limited to the emulator test app.
adb_device shell am force-stop "$PACKAGE"
launch_and_check "relaunch after force-stop"

echo "PASS: emulator install, Activity lifecycle, same-key package replacement, and relaunch"
echo "NOT TESTED: Thor-specific PServerBinder, daemon, CPU Fix, CRTC, PackageInstaller callback, or physical handoff"
