#!/usr/bin/env bash
# Build a disposable installer test agent, deliberately outside the Thor APK.
set -Eeuo pipefail
KEYSTORE="${1:?usage: build-installer-harness.sh <temporary-keystore> <output-apk>}"
OUTPUT="${2:?output APK path required}"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
test -n "$SDK" || { echo "Android SDK location missing" >&2; exit 2; }
TOOLS="$SDK/build-tools/35.0.0"
JAR="$SDK/platforms/android-34/android.jar"
SOURCE="tests/android/installer-harness"
for f in "$TOOLS/aapt2" "$TOOLS/d8" "$TOOLS/zipalign" "$TOOLS/apksigner" "$JAR" "$KEYSTORE"; do
  test -f "$f" || { echo "Missing build prerequisite: $f" >&2; exit 2; }
done
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/classes" "$WORK/dex" "$(dirname "$OUTPUT")"
javac -source 8 -target 8 -cp "$JAR" -d "$WORK/classes" \
  "$SOURCE/HarnessActivity.java" "$SOURCE/InstallerStatusReceiver.java"
"$TOOLS/d8" --min-api 28 --lib "$JAR" --output "$WORK/dex" \
  "$WORK/classes/org/jestylabs/thorfix/installerharness/"*.class
"$TOOLS/aapt2" link -o "$WORK/base.apk" \
  -I "$JAR" --manifest "$SOURCE/AndroidManifest.xml" \
  --min-sdk-version 28 --target-sdk-version 33
cp "$WORK/base.apk" "$WORK/merged.apk"
(cd "$WORK/dex" && zip -q -0 "$WORK/merged.apk" classes.dex)
"$TOOLS/zipalign" -f 4 "$WORK/merged.apk" "$WORK/aligned.apk"
"$TOOLS/apksigner" sign --ks "$KEYSTORE" --ks-key-alias thor-emulator-test \
  --ks-pass pass:android --key-pass pass:android \
  --out "$OUTPUT" "$WORK/aligned.apk"
"$TOOLS/apksigner" verify --verbose "$OUTPUT"
echo "Built isolated installer harness (disposable CI certificate): $OUTPUT"
