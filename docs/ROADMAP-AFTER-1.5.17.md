# Thor roadmap after the v1.5.17 pre-release

> **Historical planning document.** For the current active work, recovery-splash status, focus/input investigation, 120 Hz tearing research and Pocket Duo compatibility, see [ROADMAP.md](ROADMAP.md).

**Status (2026-10-05):** v1.5.20 is Latest stable. The combined v1.5.19
candidate failed daemon migration and was rolled back without clearing data.
The corrected, signed v1.5.20 passed an in-place upgrade, BOTH/TOP checks, one
TOP cold boot and Wake Guard checks on the Thor. See its
[validation diary](VALIDATION-1.5.20-PENDING.md). v1.5.18 was incorporated in
this release rather than installed separately. The pre-compositor property
write and an earlier compositor restart remain untested proposals.

The order is: measure, correct safety failures, seek a runtime vendor-state
reload that avoids the second visual boot, then consider moving the existing
CPU restart earlier. Keep each performance behavior change in its own version.
No safety wait is reduced from estimates alone.

## 0. Measure the exact v1.5.17 APK

Use the [validation diary](VALIDATION-1.5.17-PENDING.md) and its read-only
collector. Recheck host tests, signed build, certificate, hashes and the
v1.5.16 review before installation. Under owner supervision, take one
v1.5.16 BOTH baseline boot, upgrade in place, then up to three v1.5.17 BOTH
boots and one TOP boot, reviewing each before continuing. The visible end of
the second Android UI phase is the main timing metric; BOOT_READY is separate.

Stop and preserve evidence on any current-boot HELPER_ABORT, late GATE_RESET,
SurfaceFlinger abort outside the owner's exact early-signature exception,
display mismatch, flash, restart loop or incomplete recovery. The signature
and its limits are defined in the validation diary; it still counts as an
abort for stable promotion. Preserve app data. v1.5.16 cannot replace a running
v1.5.17 daemon through its allowlist: a successful downgrade requires a
supervised cold boot.

Measure gate cadence and grace, CPU property timing, helper service marks,
system_server, boot animation and second BOOT_COMPLETED. The post-restart gate
may use these signals only if the physical evidence shows they are reliable.
Use embedded uptimeMillis from first-phase boot events when the logcat
`-v monotonic` column disagrees; the baseline has a measured +3.55 s
first-phase offset. Keep paired uptime/wall-clock readings for later
tombstone correlation and broadcast history for the receiver delay.
Measure daemon CPU by thread, RSS and display reads in idle-screen-on,
naturally-awake-screen-off and game conditions. A True Bottom Screen Off ON/OFF
frame-time A/B using SurfaceFlinger `--timestats` needs separate approval and
must restore the initial state.

## 1. Investigate avoiding the compositor restart (read-only first)

First trace the property consumer inside the Thor's vendor display stack and
check whether a callable runtime path refreshes the effective state without a
process restart. The initial read-only binary finding and the evidence needed
before any trial are in [CPU Fix restart options](CPU-FIX-RESTART-OPTIONS.md).
If no safe path exists, develop the CPU-only early gate in a separate version:
move the property/restart earlier, keep display actions held, and run the
unchanged full mode/CRTC gate in the successor. Direct Boot is a later study.
The pre-compositor hook stays research-only until timing, fallback, boot-loop
escape and uninstall cleanup can all be proved.

The historical runtime property, panel-cycle and composer-restart experiments
have now been recovered from thread `01a0cbaa-8916-7ee0-8dfb-df13250c180c`.
Their results and the minimal supervised next-session test are consolidated in
[CPU Fix restart options](CPU-FIX-RESTART-OPTIONS.md). Runtime ON/OFF still
needs one confirmed visual compositor/framework restart; the open question is
whether a property write can precede the **first** composer start at boot.

In the corrected v1.5.16 baseline, the first visual finish was 17.899 s,
the app's receiver ran at 29.340 s, the daemon applied the CPU Fix at
42.028 s, and the second visual finish was 55.249 s. Roughly 26 s of the
37.350-second interval between visual finishes involved the current CPU Fix
gate and compositor restart. This supports investigating an earlier property
write; it does not justify shortening the gate grace.

Inspect the compositor's init class/trigger, `ro.boottime` against
`post-fs-data`, vendor scripts that could overwrite the CPU property, and
read-only Magisk/KernelSU presence. Investigate the device's `uname -r` and
matching DRM source before claiming that debugfs reads block modesets.

Read-only Thor observation on 2026-10-04: `qti_display_boot` is a `class main`
one-shot service started by `post-fs-data` at approximately 3.701 s; the vendor
composer starts at approximately 4.074 s. The vendor script sets
`vendor.display.disable_system_load_check=1` only for `subtype_id=1`; this Thor
reports Kalama SOC 603 and `subtype_id=0`. These observations explain why that
vendor branch does not set the property here. They do not establish that a
third-party post-fs-data script can reliably win the startup race. ADB shell
has no `su` and cannot inspect `/data/adb`, so Magisk/KernelSU presence remains
unconfirmed. No persistent system change was made.

If setting the property before composer startup is viable, a future opt-in
must prevent boot loops, remove all persistent scripts when the app is
uninstalled, keep gate verification, and retain the current restart for
runtime toggles or as fallback. `/data/adb` scripts survive app uninstall;
if automatic removal cannot be proven, reject that design. Any persistent
system change requires the owner's explicit decision before implementation.

## 2. v1.5.18 safety correction

**Source status:** the helper observer, `phase_ms` and stalled-phase
replacement were integrated into the signed v1.5.20 release. The normal
handoff passed on the Thor; adversarial recovery remains host-tested only.
The post-restart readiness signal is not included. The source narrows the plan
below: only an abort that proves the compositor never restarted (exit 10, with
the watcher still running) recovers in place. Any other abort may follow a
framework restart that leaves the old daemon's watcher and display callback
stale, so that daemon holds as `HANDOFF FAILED` and is replaced by
`AutoService`. See ARCHITECTURE.md.

- The daemon observes the compositor helper child. On an error exit while the
  daemon remains alive, it runs a safe post-restart gate, marks
  `HANDOFF_RECOVERED`, and releases its transition wake lock.
- Add `phase_ms` to authenticated identity response `I`. `AutoService` treats
  a same-version `STARTING` daemon whose phase has not progressed for about
  90 seconds as stuck, then follows only the guarded replacement path.
- Add a real post-restart readiness signal to the gate **only if** all four
  v1.5.17 boots support it. Otherwise ship recovery without changing the
  gate. The unchanged `sys.boot_completed=1` alone is insufficient.
- Test the helper-abort and stale-phase flows in a pure host model. Do not
  provoke a helper abort on the physical Thor. After a signed build, validate
  one supervised BOTH boot and one TOP boot before considering promotion.

## 3. Timing and resource work

- If the pre-composer property approach succeeds, consider it in a separate
  opt-in release (proposed v1.6.0). Do not shorten the 10-second gate grace
  or eight-second helper floor as part of that change.
- If it fails, study AYN BOOT_COMPLETED receivers and broadcast history.
  Replace the initial 10 seconds with a handler-completion signal plus a
  measured margin only after several BOTH and TOP boots support it. Keep
  the current timeout and safe fallback.
- Close the proposed removal of the eight-second helper minimum (B) with
  measured `HELPER_SYSTEM_SERVER_NEW_PID`, package/settings FOUND and visible
  finish times. The current decision is not to implement B for an unmeasured
  or imperceptible gain.
- After v1.5.18, put watcher/DRM polling reduction in its own version. Try a
  mode observer with a 500–1000 ms safety check, a brief 20 ms response after
  wake/display events, and DRM reads on changes, repair and wake. A `dpms`
  substitute must first match the Thor's physical CRTC states. Test observer
  failure, lost callbacks, TOP/BOTH and sleep/wake before replacing the
  existing checks.

## Process and deferred features

The Windows CI runs both host suites on pushes and PRs. For the v1.5.20
pre-release, the owner replaced the old fixed quota of three BOTH boots and
one TOP boot with targeted supervised tests: exact signed APK, in-place
upgrade, BOTH/TOP and sleep/wake, one TOP boot, correct final CRTCs, and
Wake Guard with a controlled false wake. Additional boots require a concrete
open question. The known early SurfaceFlinger abort occurred before the app
started; it is recorded as an explicit exception to the old zero-aborts gate,
not counted as zero aborts. The owner accepted this explicit exception and
promoted the same signed APK after the targeted checks.
Validation files use explicit status fields; release notes must not call a
stable version a candidate. Keep logs, tombstones, APKs and signing keys out
of Git. Do not increase logcat buffers without the owner's approval.

Cross-check future work against all open items in
[the v1.5.16 code review](CODE-REVIEW-1.5.16.md): stuck states, DRM and
watcher cost, CPU OFF/unset warning, hardcoded display IDs, fork frequency,
migration/TOP upgrade, display callback health, socket permissions,
synchronization and dashboard telemetry. Physical BOTTOM ONLY, dock use and
the updater's end-to-end installation test are deferred by owner decision.
Replacing `LID UNKNOWN` with a Wake Guard OFF label is also deferred. The
next active work is read-only analysis of the vendor property consumer; an
early restart candidate follows only if dynamic reload has no safe path.
