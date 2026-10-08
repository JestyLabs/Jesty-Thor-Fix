#!/usr/bin/env bash
# Disposable emulator-only APK. Bundle the exact production gate source.
set -Eeuo pipefail
OUTPUT="${1:?usage: build-update-gate-emulator-harness.sh <output.apk>}"
KEYSTORE="${2:?temporary test keystore required}"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
test -n "$SDK"
TOOLS="$SDK/build-tools/35.0.0"
JAR="$SDK/platforms/android-34/android.jar"
SOURCE="tests/android/update-gate-harness"
for path in "$TOOLS/aapt2" "$TOOLS/d8" "$TOOLS/apksigner" "$TOOLS/zipalign" "$JAR" "$KEYSTORE"; do
  test -f "$path" || { echo "Missing dependency: $path" >&2; exit 2; }
done
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/classes" "$WORK/dex" "$(dirname "$OUTPUT")"
javac -source 8 -target 8 -cp "$JAR" -d "$WORK/classes" \
  "src/com/thor/displaypowertest/UpdateCommitGate.java" \
  "$SOURCE/GateTestActivity.java"
mapfile -t CLASS_FILES < <(find "$WORK/classes" -name '*.class' -type f)
"$TOOLS/d8" --min-api 28 --lib "$JAR" --output "$WORK/dex" "${CLASS_FILES[@]}"
"$TOOLS/aapt2" link -o "$WORK/base.apk" -I "$JAR" \
  --manifest "$SOURCE/AndroidManifest.xml" \
  --min-sdk-version 28 --target-sdk-version 33
cp "$WORK/base.apk" "$WORK/merged.apk"
(cd "$WORK/dex" && zip -q -0 "$WORK/merged.apk" classes.dex)
"$TOOLS/zipalign" -f 4 "$WORK/merged.apk" "$WORK/aligned.apk"
"$TOOLS/apksigner" sign --ks "$KEYSTORE" --ks-key-alias thor-gate-ci \
  --ks-pass pass:android --key-pass pass:android \
  --out "$OUTPUT" "$WORK/aligned.apk"
"$TOOLS/apksigner" verify --verbose "$OUTPUT"
echo "PASS: isolated gate harness built from production UpdateCommitGate.java"
