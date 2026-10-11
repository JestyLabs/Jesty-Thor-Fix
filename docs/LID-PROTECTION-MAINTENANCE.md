# Lid Protection maintenance boundary (post-1.7.1 draft)

## Scope

Lid Protection remains an **opt-in**, supported feature. This change isolates its runtime implementation behind the existing `LidGuard` static API, used by the daemon and potential smali callers. The UI toggle, persisted preference, daemon argument, IPC `G`/`H` commands, Q field names and display/CPU control paths are unchanged.

### Disabled path

- The TOP display watcher checks the volatile enabled flag before asking the optional feature to defer wake repair. When OFF, no Lid policy methods run from the sample path.
- Q telemetry preserves `lid`, `lid_guard`, `lid_guard_state`, `blocked_wakes`, `last_lid_action` and `external_display`. An OFF guard returns the **cached** external display value without starting `dumpsys display`; `unknown` means not yet observed, and a value after ON→OFF can be stale.
- If a previously started Hall reader receives an event while OFF, it skips the Lid policy/model and does not schedule sleep. An OFF-only daemon never opens a Hall event stream.
- Guard sleep rechecks the enabled flag while holding the shared display-hardware lock.

### Enabled path / constraints

- Original Hall node selection, initial `getevent -S`, blocking event reader, external probe cache interval (~30 seconds), timers, debounce, retry limits, guarded sleep and wake-repair deferral remain in the isolated implementation.
- An already-open Hall stream is **not forcibly closed** at ON→OFF. Its thread may remain blocked in a read, without periodic polling or Lid policy work. Closing vendor input descriptors from another thread cannot be assumed to cancel the read portably. A safe stop/restart lifecycle deserves a separate device-validated change.
- A pending probe or previously queued task may complete after OFF; the refactor prevents **new** probes while disabled. No new privileged commands or faster polling are introduced.
- No claim of measured energy or CPU savings: the change avoids some hot-path calls, optional Hall policy processing and passive Q-triggered external probe spawns. Actual savings depend on query and event frequency.

## Test and release gates

1. Run the existing boot/lid/IPC, watcher, dashboard, and signing-policy host suites. The boot/lid script checks the compatibility facade and disabled-state source contracts.
2. Build/test an unsigned candidate from a clean checkout (Java compilation, dex merge, class names, smali call sites); review diff and CI. The isolated Java test `tests/LidIsolationTest.java` can be executed on a host JVM with Android API and daemon boundary stubs, as in the private offline review; it is not a physical device test.
3. With **separate owner approval**, validate OFF in BOTH/TOP, normal screen off/on and wake repair, CPU Fix, boot handoff, telemetry field set/order and ON with the lid open. Check ON→OFF→ON, Hall node fail-safe and recovery. Stop on any unexpected sleep, wake or display state.
4. Do not modify or merge the #37 mixed-refresh or #55 energy research tracks as part of this change.

## How to measure Lid ON overhead (NOT EXECUTED)

Existing public scoped v1.7.1-rc1 screening with Guard OFF recorded roughly 0.31% of one CPU core in BOTH and 0.36% in TOP during short 64-second windows. Those observations do **not** measure the incremental cost of turning the Guard ON, app CPU, or battery energy.

Use the existing reviewed read-only CPU collector with owner approval. Pin firmware, build hash, BOTH/TOP, brightness, performance profile, network and foreground scene. Keep the dashboard closed for daemon-idle comparisons. Record process identity, boot, per-process CPU ticks/elapsed time, counters, context switches, thread deltas and clocks; missing values are UNKNOWN.

- Compare matched published-build vs draft-build OFF windows to isolate any OFF-path optimization.
- Compare alternating draft OFF→ON→OFF→ON windows with lid **open**, at least three windows per condition after warm-up, preferably 180–300 seconds each. Report median/range, not a single isolated delta. Repeat separately for BOTH and TOP.
- Separately test the cost of Q/dashboard polling: identical bounded Q cadence while OFF/ON, count external-probe spawns over more than 30 seconds. Q CPU and `dumpsys` overhead should not be conflated with idle daemon CPU.
- Separate one-time activation (`getevent -S`, watcher creation, first probe), steady ON (blocked Hall reader; conditional Q probes) and event-triggered work (close/wake/sleep). Do not close the lid during baseline measurements or induce repeated wake events.
- Telemetry from USB/battery is not a calibrated watt-hour measurement. CPU tick sampling quantization and background variance prevent firm claims about small differences without repeats.

The 500 ms Settings safety poll, 20 ms event burst and 1 s DRM safety cadence are unchanged. Reducing those risks wake-repair responsiveness and requires a separate latency/CRTC validation—not speculative optimization in this PR.
