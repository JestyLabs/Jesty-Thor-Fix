# Failure-mode validation matrix (updater, daemon and handoff)

> Test plan, not a passing test report. Baseline: target SDK 25 / Android 13 Thor. Do not inject faults into privileged vendor services or kill system processes as part of routine testing.

## Matrix

| Failure | Host / mocked fault injection (safe offline) | Supervised physical Thor gate | Required fail-safe / evidence | Status |
| --- | --- | --- | --- | --- |
| **Updater interrupted** | Simulate cancel during download, digest mismatch, invalid signer/version, denied install permission, abandoned session and cancelled confirmation; verify no install request is committed after a cancellation, temporary downloads/sessions are cleaned up | Install **exact pre-approved signed test APK**; test cancel before confirm and permission denial/retry. For interruption during Android package replacement, use controlled device test only with recovery/rollback available | Old app and daemon remain usable until Android confirms replacement; no partially trusted APK; log session ID and result without identifiers; verify installed version/signing certificate after replacement | **Partly host-covered; physical E2E pending** |
| **Daemon unavailable** | Feed `DaemonLaunchModel` ABSENT / STALE_PREVIOUS_BOOT / STALE_UNKNOWN and `Probe.UNREACHABLE`; cover bounded attempts, untrusted socket ownership, no fake HEALTHY response and no kill without verified identity | With both physical panels safely ON and CPU-fix restart disabled, observe recovery from an absent or previously stopped **owned** daemon; never kill vendor `pservice` for the test | No speculative display OFF/ON when state is unknown; bounded restart; peer UID/protocol/version verified before actions; record boot phase and CRTC pair | **Host decision coverage; on-device fault recovery incomplete** |
| **Handoff aborted / compositor does not restart** | Exercise `HandoffRecoveryModel` helper exit `10` with watcher RUNNING vs not RUNNING, plus other exit codes, lost wake lock, timeout and stale epoch. Verify return to gated state or explicit HOLD without a second restart | Supervised test only with a recovery plan, logcat and both panel states recorded. Do not manually issue `ctl.restart`, terminate composer or zygote solely to simulate the fault | Deterministic `RECOVER_IN_PLACE` only when proven safe, otherwise HOLD/FAILED; no uncontrolled second compositor restart; wake lock released when ownership is unambiguous | **Model covered; cross-process behavior pending** |
| **`PServerBinder` absent** | Make bridge lookup return null or transact fail via a test seam / fake. `PServer.startDaemon` must return false and `AutoService` must not assume daemon launch succeeded | Collect read-only `service check`, process state and logs during a naturally occurring incident. No forced vendor-service restart, firmware edits or arbitrary Binder registration for test purposes | Existing healthy daemon stays intact if reachable; otherwise leave actions safely inactive, give understandable diagnosis, do not silently report healthy | **Source handles null/exception; actual vendor root cause open** |

## Baseline and shared capture (private)

- Device build, Android version and installed APK `versionName`/`versionCode` plus signing certificate digest.
- Action timeline: button/boot/update request, OS installer callback, `AutoService` launch, socket probe, daemon PID/UID/protocol and boot/handoff state. Avoid public raw captures.
- **Both** physical DRM CRTC states and whether screen-off or wake repair occurred; do not infer panel state only from UI flags.
- Socket path metadata/ownership where appropriate, expected and actual retry deadlines, wake-lock state, and final usable UI/daemon state.
- Stable acceptance: no unexpected boot loop, framework/compositor restart, hung install, daemon impersonation, or screen action under unknown physical state.

## Acceptance per layer

1. **Host tests** may demonstrate model decisions and deterministic error handling; they do not prove Android process death, Binder registration, PackageInstaller callbacks or DRM results. Add tests around seams before claiming all four cases covered.
2. **Android integration** may exercise callbacks, service start limits and installer-session lifecycle on a test system; vendor bridge/DRM behavior still needs the physical Thor.
3. **Supervised Thor tests** require maintainer approval, exact signed APK hash, confirmed recovery plan and preserved baseline. Stop at unexpected panel state, service loss, repeated restart, or unverified identity.
4. **Release gate** is separate: no stable release or change in target SDK, polling or daemon behavior based only on this matrix.

## Existing code/test entry points

- `tests/HandoffRecoveryModelTest.java` and `src/.../HandoffRecoveryModel.java`
- `tests/DaemonLaunchModelTest.java`, `tests/PreviousSecureDaemonIdentityTest.java` and `src/.../AutoService.java`
- `src/.../PServer.java`, `src/.../SocketClient.java`, `src/.../SecureChannel.java`
- `src/.../AppUpdater.java`, `src/.../UpdateInstallReceiver.java`, `src/.../UpdateReadiness.java` and `tests/UpdateVersionTest.java`
- `docs/ARCHITECTURE.md` and `docs/TARGET-SDK-25-MIGRATION.md`

Record a test result only after attaching sanitized evidence and explicitly distinguishing a simulated result from physical observations.
