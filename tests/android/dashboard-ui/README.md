# Real dashboard UI regression

The runner builds a separate native instrumentation APK and re-signs the exact
unsigned product APK with the same fresh disposable test key. No product test
hooks, manifest changes or production credentials are used.

Only an explicitly named, disposable API 33 emulator without the vendor bridge
is accepted. Wi-Fi/data and automatic release checks are disabled. The UPDATE
action uses synthetic cached metadata; no release is downloaded or installed.
Feature switches, external links, Save Report and Start Trial are never activated.
The Sleep Trial **entry dialog** is opened and dismissed without arming a trial.
Media is paused only in instrumentation for static captures.

```sh
python scripts/test-dashboard-android-ui.py \
  --sdk "$ANDROID_HOME" --java "$JAVA_HOME" \
  --unsigned-apk /outside/checkout/product-unsigned.apk \
  --build /outside/checkout/ui-build --build-only
python scripts/test-dashboard-android-ui.py \
  --sdk "$ANDROID_HOME" --build /outside/checkout/ui-build \
  --serial emulator-5554 --avd owned-disposable-api33 \
  --output /outside/checkout/ui-results
```

Windows uses the same arguments with absolute paths. A dedicated server can be
selected with `--adb-port`. Build directories must be fresh. `--font-scale 200`
selects the focused layout regression; the default covers 100%, 150% and 200%.
Stop/discard the AVD and remove its disposable keys after collecting evidence.

Twelve default acceptance checks measure native view/action bounds, text layouts,
accessibility nodes, seven-control Tab traversal, settings long-press and
Diagnostics activation with Enter/Space. Each expected dialog must actually be
active in the target package. Lower dashboard metrics are reached by scrolling.
All three Diagnostics actions must be visible and have at least 48dp height
(rounding tolerance 0.5dp); Sleep Trial must open its read-only entry dialog.
Action bounds of all seven dashboard controls must be at least 48dp in both axes.

The unmodified product at `acbc3dd` fails touch-target checks and the Diagnostics
button-layout check with large text. The baseline viewport for this regression
has software navigation reducing the available landscape width; its narrower
dialog also clips at 150%, unlike the earlier 1920px-wide audit. Record the actual
viewport from each run instead of inferring it from the physical display size.

This does not test TalkBack speech, quantitative contrast, physical controllers,
real hardware telemetry, report export or trial measurement. Raw JSON/screenshots
and keys remain outside Git and are not published as CI artifacts. CI runs this
last because it disables emulator network transports and replaces only its
disposable product-test installation.
