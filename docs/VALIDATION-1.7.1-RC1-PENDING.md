# v1.7.1-rc1 acceptance

This candidate carries the updater cancel/commit gate and event-only bridge/helper
diagnostics already integrated into main, with package/runtime identity
`1.7.1-rc1` (version code 72). The exact v1.7.0 daemon is admitted through the
existing authenticated, healthy-watcher, BOTH/active-CRTC handover gate.

Lid Guard is maintained without new behavior. The display/CPU controls and watcher
cadence are unchanged. App event history and energy trials remain in PR #55;
mixed-refresh investigation remains in PR #37.

## Gates before publication

- Run boot/lid/IPC, dashboard/cancel-race, bridge classification, collector,
  signing-policy and public-content host suites; build the complete APK.
- Verify the release certificate, alignment, version, SHA-256 and APK content.
- On the supervised Thor, verify identity/firmware, battery, boot-ready state,
  existing daemon identity/health and both active CRTCs before replacement.
- Install the exact signed candidate in place, preserve settings, open the app
  and observe the existing bounded v1.7.0-to-candidate handover in BOTH. Confirm
  a single healthy successor and retained matching CPU startup proof.
- With owner confirmation, check ordinary BOTH/TOP transitions and one normal
  sleep/wake cycle per mode. Keep refresh, performance profile and fixes fixed;
  do not enable or fault-inject Lid Guard.
- Compare awake BOTH and TOP daemon CPU observations with the exact v1.7.0
  under matched conditions. Label short samples as screening; do not infer energy
  savings or gameplay improvements.
- The production in-app update/cancel path remains a separate physical gate;
  ADB package replacement alone does not validate it. Android emulator results
  support cancel/session behavior without proving vendor handover.

Stop at unexpected compositor restarts, panel recovery failures, lost daemon
identity/protocol, failed handover or unexpected CPU proof replay. Preserve raw
captures privately. Publication requires explicit owner acceptance of the exact
candidate and any remaining stated limits; stable v1.7.0 remains Latest.

## Status

The seven Windows host suites passed on 2026-10-11: boot/lid/IPC (including bridge
classification and the new v1.7.0 migration rejection cases), dashboard/cancel
gate, watcher runtime, watcher measurement, read-only collector fixtures,
release policy and public content. The content check was rerun successfully from
the repository after an initial invocation outside Git failed before scanning.

The local candidate compiled and passed alignment, signature verification and
certificate equality against v1.7.0. Its manifest reports `1.7.1-rc1` / code 72.
It contains no PR #55 diagnostics or energy-trial implementation. Local candidate
SHA-256: `3fc183d978723ecff4e7b6963af09a2ecaee72606d0b5a7e5dec3f181616e1bf`.

On 2026-10-11, this exact local APK was installed over the exact official v1.7.0
on the supervised firmware .377 Thor over wireless ADB. The app's existing
bounded migration replaced the older daemon with one root successor reporting
`READY 1.7.1-rc1`. Current-boot/current-process trace reached `BOOT_READY` and
kept the early CPU attempt progress. The compositor and system_server PIDs,
kernel boot and CPU property were unchanged across the handover. Display Fix
and CPU Fix remained on; Lid Guard remained off. Dashboard rendering was checked.

The initial snapshot at 15 seconds preceded completion of the existing migration
wait; it was not treated as acceptance. Later read-only evidence confirmed the
successor. Historical Android crash logs were excluded by the pre-install log
time bound rather than attributed to this install.

One awake Home-screen sample per mode on v1.7.0 measured 19 daemon CPU ticks:
0.296% of one core in BOTH over 64.10 seconds and 0.297% in TOP over 63.98 seconds.
The candidate BOTH sample measured 20 ticks, 0.313% over 63.83 seconds; candidate
TOP measured 23 ticks, 0.362% over 63.56 seconds. Relative to the baseline, the
observed absolute differences are +0.017 and +0.065 percentage points of one
core. At 100 ticks/second, a single tick is 10 ms. A single sample cannot separate
event cost from background variation. The owner closed the lid for about five
seconds and reopened it once in TOP and once in BOTH, reporting normal recovery
in both cases. The TOP report is visual evidence; the owner changed to BOTH
before a separate TOP recovery dump. Read-only BOTH endpoints before/after the
second cycle confirmed both CRTCs active, the same daemon/boot/compositor/
system_server, CPU property retained and Lid Guard off. These samples do not
measure app CPU, energy, tearing,
or statistical equivalence. Only endpoint agreement and process continuity are
verified. A private PowerShell wrapper scope error was corrected and checked
offline before measurement; its failed preparation output is preserved.

All applicable PR CI checks passed. No release has been published. The exact
protected main signing/publication flow remains a subsequent gate. If it produces
different signed bytes, validate that exact final artifact on the Thor before
publication; a local-candidate pass does not transfer automatically.
Raw evidence and signing data remain local.
