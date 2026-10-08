# Android emulator lifecycle smoke (independent research)

Status: **test harness, not a release/hardware acceptance gate.**

The mainline app is built by the existing Windows/Apktool pipeline. This
independent integration test reuses **the same unsigned CI APK** and signs a copy
with a fresh, disposable emulator-only certificate. No production signing key,
protected GitHub Environment, vendor service, physical device, or root daemon
is involved. The script refuses an ADB target that is not an emulator.

## What the automated test observes

1. On Android 13 (API 33) with **no `PServerBinder`**, install the test-signed
   APK and check the installed version.
2. Launch the actual `MainActivity` and confirm its process remains alive.
3. Send it Home, return to the dashboard, and check it is still alive.
4. Replace the APK with the **same APK signed by the same disposable key**
   while the app is running; verify package/version and relaunch.
5. Force-stop only the test app on the emulator; relaunch and verify that the
   Activity is alive again.

Failures stop the job and print a bounded subset of relevant emulator logs.
The test uses no privileged display, CPU, vendor-service or boot commands.

## What this **cannot** prove

- Correct `PServerBinder`/pservice registration, UID validation, daemon startup,
  DRM/CRTC state, screen-off/wake repairs or CPU Fix on a real AYN Thor.
- `PackageInstaller`'s user-confirmation flow, cancel/commit atomicity, callback
  recovery, or replacement during an *actual* Android package-installer session.
  The emulator script uses `adb install -r`, which is a different install path.
- Persistence of app preferences across a signed-key migration or interactions
  with a previous *release-signed* daemon.
- Correct physical-boot behavior or measured performance/battery consumption.
- That a process remaining alive means every dashboard action succeeded.

Do not install the emulator-test APK over the user's real Thor release.
Do not publish it as a test release or enable any real device in this script.

## Follow-on work

- Complement the existing PR #49 host tests with **separate Android integration**
  coverage of `PackageInstaller.Session` callbacks, cancellation and denied
  confirmation, without copying another project's implementation.
- Add a fake-bridge integration seam for daemon-absence/retry policies; preserve
  the production IPC security model and avoid a simulated successful vendor
  service masquerading as real device evidence.
- Validate the signed production update and daemon handoff with owner permission,
  exact APK hash, same-certificate upgrade, both CRTCs confirmed, and a rollback
  plan, using `docs/FAILURE-VALIDATION-MATRIX.md`.
- Only consider making this job mandatory once reliability and runtime cost have
  been measured in multiple CI runs.

