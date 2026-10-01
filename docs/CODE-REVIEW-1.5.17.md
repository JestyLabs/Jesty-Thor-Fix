# v1.5.17 patch review

The supplied patch applies to the v1.5.16 main tree. Host tests and a full
signed Android build passed after the corrections below. Device behavior is
still unverified for this exact APK.

## Reviewed changes

- The fixed-rate gate preserves three equal mode/CRTC observations, the
  original 10/5-second grace periods and the 60-second timeout. READY still
  needs a fresh valid observation. `ProcessWait` preserves bounded command
  timeouts and failure behavior while polling for short-lived child exit.
- One DRM read gives the gate a consistent CRTC pair. Review added a reset
  on any other CRTC header and rejects malformed active flags, so unrelated
  blocks cannot be assigned to the Thor's top or bottom CRTC.
- The helper's old-daemon UID and command-line checks still abort on a live
  mismatch. A PID that is gone or zombie is allowed to proceed; a failed
  `kill` is retried as a state check and aborts if the process is still live.
  The instance file lock remains the final protection against duplicates.
- The new second-boot observer and helper service marks do not gate CPU,
  display or lid actions. The observer was reduced from four subprocess
  probes every 250 ms to once per second to limit diagnostic overhead.
- `BootReceiver` now accepts only `BOOT_COMPLETED`. The smali compiled in the
  full APK build. Trace rotation keeps one previous generation.
- The patch omitted the default `build.ps1` artifact name. It is now
  v1.5.17, and the host suite checks agreement with the manifest/daemon.

## Open risks and follow-ups

- A daemon stuck in `APPLYING CPU FIX` after helper failure still has no
  self-recovery or age limit on `STARTING`. This is the most important open
  reliability issue and needs a separate design and adversarial tests.
- `sys.boot_completed` remains stale across the Android UI restart. The
  new signals only observe it; post-restart display reconciliation still
  uses the existing gate. A TOP cold boot has not been validated.
- The new timing benefit and boot-animation edges are hypotheses until the
  exact APK is observed on the Thor. Diagnostic forks may still perturb
  timings slightly, even at one-second cadence.
- Hardcoded Thor display IDs, steady-state debugfs polling, watcher polling,
  helper shell maintenance and Windows-only host tests/CI remain open.
- No property or compositor boot behavior was changed to avoid the second
  visual Android boot. The eight-second helper floor and initial 10-second
  grace were intentionally retained.

This review supports a **testing pre-release**, with v1.5.16 kept Latest
stable. The [physical plan](VALIDATION-1.5.17-PENDING.md) is the gate for any
promotion.
