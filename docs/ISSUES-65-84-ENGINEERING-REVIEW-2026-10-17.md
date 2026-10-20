**20 October 2026 status update:** [v1.7.4 release record](VALIDATION-1.7.4.md) supersedes current-status statements below. New live captures led to display-stall containment; physical acceptance is pending. #84 is closed as delivered software mitigation at the maintainer's request; physical follow-up remains in #95/S6. Earlier closure/automation attribution and integration gates are historical, not proof of the later incident's cause. The maintainer explicitly authorized release after software gates, with physical checks afterwards.

**Historical review, superseded for current disposition.** PR #82 subsequently integrated TOP cancellation hardening and issue #84 was closed. For issue #65, see [the completed brightness investigation](BOTTOM-BRIGHT-FLASH-AFTER-LID-WAKE-20261011.md). The dated statuses and source descriptions below describe 17 October.

# Open display investigations — evidence review (2026-10-17)

**Status:** bounded desk review of the published source and issue records, **not** a device test, reproduction, root-cause finding or shipping fix. No Thor was available for this review. Both issues remain OPEN. The public stable version is [v1.7.3](https://github.com/JestyLabs/Jesty-Thor-Fix/releases/tag/v1.7.3); its successful release and emulator checks do not prove either symptom fixed.

## Triage

| Issue | Recorded symptom | Engineering priority | Evidence strength | Release disposition |
| --- | --- | --- | --- | --- |
| [#84](https://github.com/JestyLabs/Jesty-Thor-Fix/issues/84) | Persistent black presentation after prolonged TOP sleep/wake, and later BOTH attempt; manual reboot restored presentation | **P1 investigation** (can deny use of both screens) | Owner observation and verified installed v1.7.1/code76; **no failed-transition capture** | Open; **no fix confirmed in v1.7.3** |
| [#65](https://github.com/JestyLabs/Jesty-Thor-Fix/issues/65) | Brief BOTTOM-only over-bright flash after waking in BOTH, returning to previous visual state without intervention | **P3 investigation** (transient and self-recovering as observed) | Two owner observations (RC2 cycle and 2026-10-16 AYN button wake); no brightness measurement or event correlation | Open; **no fix confirmed in v1.7.3** |

Severity is a maintainer triage proposal, not measured frequency. Do not combine these symptoms, claim a common mechanism, or claim that the v1.7.3 no-reboot service handover repairs either one.

## #65 — BOTTOM flash in BOTH

**Observed:** RC2 physical observation on firmware `.377` in BOTH (120 Hz selected, Display Fix/CPU Fix ON, Guard OFF, USB connected); then a separate 2026-10-16 owner observation waking from BOTH with the **AYN hardware button**, **BOTTOM only** noticeably brighter for approximately one second (estimate) before both displays returned to their previous appearance. The second observation did **not** establish the exact version, refresh, toggles, USB or automation state. No TOP -> BOTH edge was reported for the second event.

**Relevant code and questions:**
- `DisplayActionCoordinator.onWatcherSample` calls `DisplayDecisionModel.reconcile`. When mode is BOTH and the upper CRTC is active but the lower CRTC reads inactive, the reconciler *may request* a lower-panel ON transition (`WATCH_BOTH`).
- `DisplayHardware.apply(true,...)` calls vendor `SurfaceControl.setDisplayPowerMode(...,2)` and updates `display.power.state`. **This is a power command, not an explicit brightness command**; a transient backlight/firmware brightness default during panel wake is a **hypothesis**, not demonstrated by this code.
- The current `DaemonState.snapshot` reports logical mode/CRTC/action/generation/watcher states but **does not measure panel luminance, backlight level or the transient video output**. A `WATCH_BOTH ON` line near wake must not be attributed as the flash cause without a synchronized observation.

**Offline work possible now:** characterize the existing `DisplayDecisionModel` transitions (both 1/1 => no ON; both 1/0 => ON only with actions enabled/unheld; unknown/asleep => no ON), check no speculative brightness setting was added, and review event ordering with bounded synthetic traces.

**Later device capture only with separate approval:** first record the **normal, unforced** wake when it occurs, preserving monotonic timestamps, current runtime/firmware, display mode, selected refresh, Display Fix/CPU Fix/Guard states, USB/automation state, relevant display-power commands and any available *read-only* brightness/backlight information. Note whether the lower display visibly illuminates **before or after** the first confirmed watcher ON action. Do not induce repeated bright flashes simply to collect samples.

**Fix gate:** reproduce a causal temporal chain (panel power vs firmware/vendor brightness restoration) or build a meaningful failing regression; then propose the smallest safe change. A blanket delay, force-off or brightness override could cause a worse dark-panel problem and is not authorized by this evidence.

## #84 — persistent TOP sleep black screens

**Observed 14 October:** owner-reported long sleep in TOP on published v1.7.1/code76, Guard OFF, external sleep automation active. Upper screen first displayed an image, later both displayed black; Android Power menu briefly made upper UI visible **before reboot**, activation of BOTTOM returned both screens black; reboot restored presentation. The retrieved app/display capture was made *after reboot*, not at failure.

**Related but different 15 October observation:** first hardware mode-button press after TOP wake did not enable BOTTOM within the expected interval; second press reached normal BOTH without reboot; external automation was reportedly disabled at that time. The two episodes must not be equated.

**Code paths to inspect if attributable traces appear:**
- `DisplayActionCoordinator.onWatcherSample`: TOP wake repair uses `markWakePending()`, the bounded scheduler, and a later lower-panel OFF attempt; mode changes invalidate prior generations.
- `DisplayActionCoordinator.applyConfirmed`: requires matching mode and known CRTCs, waits up to 1200 ms for CRTC agreement. **CRTC active/inactive alone is not proof of illuminated pixels.**
- `LidProtectionRuntime.onWakeEvent/trySleep`: Guard requires an enabled, valid lid state and checks external-display policy. Guard OFF in the recorded incident makes an *active Guard-induced sleep* less likely, not proof against all OS/vendor or external-automation sleep requests.
- External sleep automation, compositor/SurfaceFlinger, Android interactive state, vendor panel transitions and stale mode events are **competing hypotheses**. Their mere presence or temporal proximity does not establish causality.

**No current justification for a runtime patch:** the pre-reboot transition cannot be reconstructed from post-reboot healthy screenshots, old traces or short sleep tests; PR #82 is a draft sanitized incident record, not an integrated fix.

**Offline work possible now:** audit transition guards and stale-event cancellation, check existing tests for no ON in unknown/sleeping state and held boot, check passive diagnostics export privacy and monotonic ordering. An emulator without Thor's vendor display service cannot confirm physical blackout recovery.

**When a Thor becomes available and the owner separately approves:** prepare a *supervised* capture with known settings, baseline normal sleep/wake first, **no artificial repeated blackout loops**. Contrast automation ON/OFF only with documented exact configuration; merely disabling an automation app may leave prior settings applied. Capture paired monotonic timeline: PowerManager interactive/wakefulness + actual sleep source, mode property changes, top/bottom CRTCs, `display.power.state`, watcher/repair generation and action, compositor process/SurfaceFlinger and hardware button events. If image disappears, **stop the test**, preserve read-only logs if possible, use pre-agreed recovery; do not repeatedly force the bottom panel ON or kill system services.

**Fix gate:** a bounded root-cause mechanism or defensible failing test, minimal correction, source-to-APK provenance, emulator regression and separately authorized physical acceptance for the affected failure before declaring fixed.

## Cross-issue constraints

- Maintain two independent trackers; reference each issue rather than duplicating raw private diagnostics in Git.
- Record exact installed APK/version and any later upgrade. A successful v1.7.3 release or CI suite is **not** physical evidence of either failure path.
- Do not create a 'brightness fix' by changing `SurfaceControl` power state or an 'anti-blackout fix' by suppressing sleep without attributable evidence.
- The present PR changes the **release publication preflight** and engineering documentation only; runtime, safety gates, vendor code, APK bytes and signing keys are unchanged.

**References:** [#65](https://github.com/JestyLabs/Jesty-Thor-Fix/issues/65), [#84](https://github.com/JestyLabs/Jesty-Thor-Fix/issues/84), [documentation draft #82](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/82), `DisplayActionCoordinator.java`, `DisplayDecisionModel.java`, `DisplayHardware.java`, `LidProtectionRuntime.java`, `DaemonState.java`.
