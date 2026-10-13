# Updater Android integration

This disposable Android 13/API 33 harness compiles the production `AppUpdater`,
`UpdateInstallReceiver` and their ownership/gate helpers unchanged. It uses real
Android widgets, package/archive signature inspection, `PackageInstaller.Session`,
mutable `PendingIntent` results and the system install confirmation UI. No daemon,
privileged bridge or product dashboard is bundled.

The fixture APK uses target SDK 25, matching the current product's installer
compatibility path. Both APK certificates are freshly generated test fixtures.
Build output and raw evidence must be outside the checkout; no release signing
material is loaded. Never run on an existing user AVD or a physical device.

## Run

Prerequisites: Python 3.10+, JDK 17, SDK platform 34, build-tools 35.0.0,
platform-tools and a freshly created disposable AVD running API 33. Create/start
the AVD separately; the runner refuses a non-emulator serial, a mismatched AVD
name or another Android API level.

```sh
python scripts/test-updater-android-e2e.py \
  --sdk "$ANDROID_HOME" --java "$JAVA_HOME" \
  --build "$RUNNER_TEMP/updater-e2e-build" --build-only
python scripts/test-updater-android-e2e.py \
  --sdk "$ANDROID_HOME" --build "$RUNNER_TEMP/updater-e2e-build" \
  --serial emulator-5554 --avd pr67-ci-api33 \
  --output "$RUNNER_TEMP/updater-e2e-results"
```

On Windows use the same arguments with absolute SDK/JDK/output paths. A dedicated
ADB server can be selected with `--adb-port`. `--case NAME` selects a focused
scenario and can be repeated. A new build directory is mandatory for every build;
an existing certificate is never reused. Stop and discard your owned AVD after
retaining the results you need.

## Coverage and oracle

- Decline real system consent: installed version unchanged; native session,
  transfer and UI reservation all released.
- Accept real system consent: higher-version APK installed through the updater;
  process replaced, app reopened and residual sessions reconciled.
- Cancel while downloading: no commit or system confirmation; retry remains
  possible after lifecycle cancellation.
- HTTP, checksum, archive compatibility and signer rejection before commit.
- Android rejects a test-only APK after consent: terminal native callback handled,
  installed version preserved and ownership released.
- Replayed terminal callbacks for the former session and an unknown session:
  explicit broadcasts complete and preserve a later cache sentinel.
- Old terminal callbacks during a newer download preserve its cache and active
  transfer/reservation; cancellation subsequently cleans that newer attempt.
- Replayed pending callback: characterize the existing repeated-launch policy
  using an actual platform-provided confirmation Intent; one native session.
- Pending confirmation while backgrounded: no launch until Activity resume.
- Activity recreation during transfer: cancellation/cleanup, then successful
  retry up to native consent in the same process.
- Process killed behind native consent, then reopened: unknown callbacks fail
  closed, stale native session abandoned on startup, fresh retry reaches consent.

Assertions observe atomic app-local snapshots without bringing the Activity to
the foreground. Terminal replay broadcasts have completion acknowledgements, so
the test cannot pass before the receiver actually handles them. Every scenario
installs a clean base fixture and closes residual installer UI from earlier tests.

## Boundaries

The test-only URL handler supplies APK bytes under a trusted-shaped release URL;
release metadata is a validated cache fixture. This proves the cached-release to
native-install path, **not** live HTTP/TLS, redirects, release discovery or outage
behavior. The production transport, endpoint allowlist and signing policy are
unchanged. Duplicate callbacks are deliberately injected broadcasts, not a claim
that Android naturally emitted duplicates.

The current pending-callback policy can launch the same confirmation again. A
characterization PASS is not a deduplication guarantee. Process recovery means
abandoning and retrying a stale installation, not persisting consent across death.
The fixture host always permits installation; dashboard readiness and daemon
handover are not simulated as product acceptance. The source-install permission
is pre-granted on the disposable AVD; the Settings permission-grant journey is
not exercised. The system confirmation of each installation remains mandatory.
No physical daemon handover, vendor service, display/wake behavior, production
certificate rotation, firmware compatibility or full dashboard acceptance is
established by this harness. Retain raw logs and snapshots privately; publish only
sanitized case results.

Current sanitized execution results: [Android updater validation](../../../docs/UPDATER-ANDROID-VALIDATION-2026-10-13.md).
