# Lid Protection maintenance boundary (post-1.7.1 draft)

## Scope

Lid Protection remains an **opt-in**, supported feature. This change isolates its runtime implementation behind the existing `LidGuard` static API, used by the daemon and potential smali callers. The UI toggle, persisted preference, daemon argument, IPC `G`/`H` commands, Q field names and display/CPU control paths are unchanged.

### Disabled path

- The TOP display watcher checks the volatile enabled flag before asking the optional feature to defer wake repair. When OFF, no Lid policy methods run from the sample path.
- Q telemetry preserves `lid`, `lid_guard`, `lid_guard_state`, `blocked_wakes`, `last_lid_action` and `external_display`. An OFF guard returns **`unknown`** for external display after invalidating the prior ON-session cache; it never launches a new `dumpsys display` from Q telemetry. Existing IPC keys and value encoding are retained, but OFF no longer claims a live external-display reading.
- If a previously started Hall reader receives an event while OFF, it skips the Lid policy/model and does not schedule sleep. ON/OFF changes and Hall model updates use the same monitor to avoid resurrecting an old CLOSED observation. An OFF-only daemon never opens a Hall event stream.
- Guard sleep rechecks enabled state and the **same authorized Hall observation epoch** at the display-hardware command boundary. Queued sleep tasks carry an ON-session generation so tasks from a previous activation cannot act after OFF→ON.
- OFF or Hall-reader failure invalidates prior Hall state without resetting the three-attempt anti-loop budget. If normal TOP wake repair had already been deferred, it is rescheduled rather than silently discarded.

### Enabled path / constraints

- Original Hall node selection, initial `getevent -S`, blocking event reader, external probe cache interval (~30 seconds), timers, debounce, retry limits, guarded sleep and wake-repair deferral remain in the isolated implementation.
- An already-open Hall stream is **not forcibly closed** at ON→OFF. Its thread may remain blocked in a read, without periodic polling or Lid policy work. Closing vendor input descriptors from another thread cannot be assumed to cancel the read portably. A safe stop/restart lifecycle deserves a separate device-validated change.
- A probe already in flight may complete after OFF, but its results are discarded using the activation epoch (including the synchronous probe on the sleep path). OFF invalidates pending/cache state, and only the current ON session may update external-display telemetry. Queued sleep tasks from an old activation return without issuing a command. An already-issued sleep command cannot be retroactively cancelled; physical lid-race acceptance remains mandatory. No new privileged commands or faster polling are introduced.
- No claim of measured energy or CPU savings: the change avoids some hot-path calls, optional Hall policy processing and passive Q-triggered external probe spawns. Actual savings depend on query and event frequency.

## New RC2 physical baseline — 2026-10-11 (NOT this draft)

Source: owner's private corrected RC2 validation package; do not add its raw
logs, screenshots, device/process identities, or direct file paths to GitHub.
RC2 build commit `17bac625...`, firmware .377, Lid Guard **OFF**.
The #57 refactor was **not installed**, so these data are comparator
observations, not validation of #57.

- **Corrected RC2:** two BOTH→TOP→BOTH sequences, TOP lid-driven wake 2/2,
  BOTH lid-driven wake 2/2 by later per-cycle evidence; endpoint CRTC state
  recovered and boot/composer/daemon were unchanged.
- **Transient anomaly:** BOTTOM reportedly appeared unusually bright on the
  second BOTH lid-open cycle and then recovered. Brightness and duration
  were not instrumented. #62 records a candidate ON-during-sleep condition
  in the independent Display Fix reconciliation, not a Lid Guard action.
  A Guard-OFF state on the original RC2 does **not** establish that the
  #57 disabled path prevents that behavior.
- **Daemon CPU with Guard OFF (per-core %):** 3 × nominal 180 s samples
  per condition. Median/range: BOTH app-closed 0.2992% (0.2937–0.3042),
  TOP app-closed 0.3158% (0.3157–0.3428), BOTH app-open 2.1619%
  (2.1513–2.1842), TOP app-open 2.3238% (2.3097–2.3668).
  App-open/closed is a separate foreground variable, **not** Guard ON/OFF.
  Same-day prior 64-second samples are not matched duration or repeat count:
  no reduction percentage can be claimed.
- **Lid ON and app-only force-stop:** private artifacts from a later phase
  exist, but the last consolidated public-safe handoff did not establish
  completed #57 acceptance. The Lid-ON actions also ran on the original
  RC2 implementation; do not attribute behavior to this refactor.
- **No physical power result:** USB/external-powered endpoints and CPU ticks
  cannot show a battery-energy saving or panel-rail effect.

After merging `main` into this draft branch, the coordinator's #58
**single paired DRM snapshot** code is retained; the only additional
coordination changes are the #57 conditional Lid guard deferral and
Hall-epoch sleep authorization. CI needs to be green for the new exact head.

**Acceptance gap:** verify the #57 APK separately, with approved exact-build
signature and scoped Lid ON/OFF state/epoch tests; avoid asking the owner
to repeat these RC2 baseline cycles just to reestablish already-proven
stock-build behavior. Compare guard telemetry OFF to expected
`external_display=unknown` rather than older RC2's live-probe semantics.

## Test and release gates

1. Run the existing boot/lid/IPC, watcher, dashboard, and signing-policy host suites. The boot/lid script checks the compatibility facade and disabled-state source contracts.
2. Build/test an unsigned candidate from a clean checkout (Java compilation, dex merge, class names, smali call sites); review diff and CI. The isolated Java test `tests/LidIsolationTest.java` can be executed on a host JVM with Android API and daemon boundary stubs, as in the private offline review; it is not a physical device test.
3. With **separate owner approval**, validate OFF in BOTH/TOP, normal screen off/on and wake repair, CPU Fix, boot handoff, telemetry field set/order and ON with the lid open. Check ON→OFF→ON, Hall node fail-safe and recovery. Stop on any unexpected sleep, wake or display state.
4. Do not modify or merge #37 mixed-refresh, #58 display/CPU read-path optimizations, or #59 passive energy research as part of this change. The #55 diagnostics are already integrated in `main` and must be retained.

## Merge blocker — active safety gate

The branch includes host characterization of stale Hall observations, OFF/ON
activation generations, duplicate sleep-budget protection and bounded Q work.
GitHub CI/CodeQL must pass on the **exact current head** after integration with
the latest main. Passing CI does **not** prove that a real SW_LID event can be
processed quickly enough between the authorization check and the one-shot
`/system/bin/input keyevent 223` command.

Do **not merge** until a separate owner-approved Thor acceptance demonstrates
with exact-build APK and rollback ready:

- Guard OFF during BOTH/TOP idle and sleep/wake, True Bottom Screen Off and CPU
  Fix regression; no extra OFF-state Q-triggered `dumpsys` subprocesses.
- Guard ON with lid open: no spontaneous sleep; normal wake repair.
- ON→OFF→ON across a Hall CLOSED/OPEN transition; no old queued sleep command,
  stale `external_display` result or lost deferred wake repair.
- Unexpected Hall read failure/unavailable device and unknown external display:
  fail safe; never assume CLOSED and never issue a speculative sleep.
- Observe CPU and Q overhead in matched conditions; savings remain unmeasured.
- STOP on unexpected sleep, binder/service instability, panel mode change,
  compositor restart or mismatched hardware state.

No ADB, installer, device automation, process kill or merge/release is
authorized by this documentation.

## How to measure Lid ON overhead (NOT EXECUTED)

Previous v1.7.1-rc1 screening with Guard OFF recorded roughly 0.31% of one CPU core in BOTH and 0.36% in TOP during short 64-second windows. Those observations do **not** measure the incremental cost of turning the Guard ON, app CPU, or battery energy.

Use the existing reviewed read-only CPU collector with owner approval. Pin firmware, build hash, BOTH/TOP, brightness, performance profile, network and foreground scene. Keep the dashboard closed for daemon-idle comparisons. Record process identity, boot, per-process CPU ticks/elapsed time, counters, context switches, thread deltas and clocks; missing values are UNKNOWN.

- Compare matched published-build vs draft-build OFF windows to isolate any OFF-path optimization.
- Compare alternating draft OFF→ON→OFF→ON windows with lid **open**, at least three windows per condition after warm-up, preferably 180–300 seconds each. Report median/range, not a single isolated delta. Repeat separately for BOTH and TOP.
- Separately test the cost of Q/dashboard polling: identical bounded Q cadence while OFF/ON, count external-probe spawns over more than 30 seconds. Q CPU and `dumpsys` overhead should not be conflated with idle daemon CPU.
- Separate one-time activation (`getevent -S`, watcher creation, first probe), steady ON (blocked Hall reader; conditional Q probes) and event-triggered work (close/wake/sleep). Do not close the lid during baseline measurements or induce repeated wake events.
- Telemetry from USB/battery is not a calibrated watt-hour measurement. CPU tick sampling quantization and background variance prevent firm claims about small differences without repeats.

The 500 ms Settings safety poll, 20 ms event burst and 1 s DRM safety cadence are unchanged. Reducing those risks wake-repair responsiveness and requires a separate latency/CRTC validation—not speculative optimization in this PR.
