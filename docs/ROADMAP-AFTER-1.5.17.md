# Thor roadmap after the v1.5.17 pre-release

**Status:** plan only. No v1.5.17 Thor installation, boot result, or later
behavior change is claimed here. v1.5.16 remains Latest stable.

The order is: measure, correct safety failures, investigate the largest boot
gain, then consider shorter waits. Keep each performance behavior change in
its own version. No safety wait is reduced from estimates alone.

## 0. Measure the exact v1.5.17 APK

Use the [validation diary](VALIDATION-1.5.17-PENDING.md) and its read-only
collector. Recheck host tests, signed build, certificate, hashes and the
v1.5.16 review before installation. Under owner supervision, take one
v1.5.16 BOTH baseline boot, upgrade in place, then up to three v1.5.17 BOTH
boots and one TOP boot, reviewing each before continuing. The visible end of
the second Android UI phase is the main timing metric; BOOT_READY is separate.

Stop and preserve evidence on any current-boot HELPER_ABORT, late GATE_RESET,
SurfaceFlinger abort, display mismatch, flash, restart loop or incomplete
recovery. Preserve app data. v1.5.16 cannot replace a running v1.5.17 daemon
through its allowlist: a successful downgrade requires a supervised cold boot.

Measure gate cadence and grace, CPU property timing, helper service marks,
system_server, boot animation and second BOOT_COMPLETED. The post-restart gate
may use these signals only if the physical evidence shows they are reliable.
Measure daemon CPU by thread, RSS and display reads in idle-screen-on,
naturally-awake-screen-off and game conditions. A True Bottom Screen Off ON/OFF
frame-time A/B using SurfaceFlinger `--timestats` needs separate approval and
must restore the initial state.

## 1. Investigate avoiding the compositor restart (read-only first)

Inspect the compositor's init class/trigger, `ro.boottime` against
`post-fs-data`, vendor scripts that could overwrite the CPU property, and
read-only Magisk/KernelSU presence. Investigate the device's `uname -r` and
matching DRM source before claiming that debugfs reads block modesets.

If setting the property before composer startup is viable, a future opt-in
must prevent boot loops, remove all persistent scripts when the app is
uninstalled, keep gate verification, and retain the current restart for
runtime toggles or as fallback. `/data/adb` scripts survive app uninstall;
if automatic removal cannot be proven, reject that design. Any persistent
system change requires the owner's explicit decision before implementation.

## 2. v1.5.18 safety correction

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

The Windows CI runs both host suites on pushes and PRs. A release is stable
only after the exact signed APK has an in-place upgrade, at least three
supervised BOTH boots, one TOP boot, correct final CRTCs and zero aborts.
Validation files use explicit status fields; release notes must not call a
stable version a candidate. Keep logs, tombstones, APKs and signing keys out
of Git. Do not increase logcat buffers without the owner's approval.

Cross-check future work against all open items in
[the v1.5.16 code review](CODE-REVIEW-1.5.16.md): stuck states, DRM and
watcher cost, CPU OFF/unset warning, hardcoded display IDs, fork frequency,
migration/TOP upgrade, display callback health, socket permissions,
synchronization and dashboard telemetry. Physical BOTTOM ONLY, dock use and
replacing `LID UNKNOWN` with a Wake Guard OFF label remain deferred.
