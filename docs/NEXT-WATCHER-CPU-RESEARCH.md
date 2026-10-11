# Next Codex handoff: joint #57/#58 validation and watcher cost investigation

**Status:** plan only, NOT executed. Do not interpret this document as authorization to run ADB, modify the Thor, merge, or publish. This is a follow-up to the already integrated v1.7.0 watcher baseline (PR #41).

## 0. Verify current source of truth

1. Refresh GitHub `main`, [PR #57](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/57), and [PR #58](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/58); inspect all new changes, open CodeQL/review alerts, and latest CI results before work. Reconcile any release/version drift.
2. Review the final #57 change, especially asynchronous Hall state, guard OFF→ON, sleep/wake repair ordering, locking and relative-path executable hardening. **Do not waive CodeQL warnings merely because the overall action reports success.**
3. Review #58's paired DRM reads and SystemProperties fallback. Ensure no snapshot field or wire format was lost. Check that the CPU Fix boot/restart safety decision still uses the original property reading.
4. Run all existing host suites, including `scripts/test-boot-lid.ps1`, `scripts/test-display-cpu-read-paths.ps1`, watcher/boot/IPC/dashboard/installer tests. Run the existing build, analyzer and security workflows. Log exact commands, hashes, branch heads and failures.
5. Create a **temporary integration branch from current main** combining the reviewed #57 + #58 diffs; resolve the overlapping `DisplayActionCoordinator.java` hunk conservatively. Do **not** merge the PRs independently or combine unrelated PR #55/#37 code. Review the integrated diff and rerun everything offline.
6. Where the current host suites do not cover a failure (for example deferred wake cancellation after ON→OFF→ON, Hall event during a queued sleep, or partial hardware confirmation), add a regression test before changing the implementation.

## 1. Physical acceptance matrix — approval gate

Do not install, use ADB, instrument tracing, or make any physical change without **separate, explicit owner authorization**. Before asking, present exact candidate APK/hash/certificate, approved allowlist/commands, session and rollback plan, expected preconditions, and stop conditions.

On the authorized same-firmware Thor, compare **published baseline** and the integration candidate with matched screen mode/brightness/scene, wake state and background load:

- Lid OFF by default: BOTH→TOP→BOTH, true lower-screen OFF/restore, display property/CRTC confirmation, normal sleep/wake and CPU Fix boot evidence.
- Lid ON with lid open: ensure no spontaneous sleep, no display-repair delay and normal telemetry; controlled Hall close/open and ON→OFF→ON only after explicit owner approval (maintenance-only regression, not new feature testing).
- Compare Q telemetry keys/values/types between versions, including `system_load_fix`, `lid_guard`, `external_display`, counter and display states. A cached external value may be stale with Lid OFF; preserve the distinction between data compatibility and sampling semantics.
- Record every mismatch, failed start, unexpected wake, compositor/system_server restart, or loss of root daemon identity. Stop rather than retrying automatically.

## 2. Measure overhead — distinguish three sources

Reuse **existing approved** read-only measurement scripts wherever applicable. Do not assume a tool is safe because its filename looks familiar; review its command allowlist and file hashes. Prefer 3–5 repeated 180–300 s windows per condition, alternating the conditions to reduce time/order bias, with warm-up intervals and raw measurements privately retained.

**A. Baseline idle CPU:** published build vs integrated draft, Lid OFF, dashboard closed; separately BOTH and TOP. Record daemon and watcher-thread CPU ticks, wakeups/context switches, read/process counts and scheduler/watcher counters, quantization, process uptime, thread names/IDs and any anomalous events. This distinguishes real steady-idle savings from reduced work on occasional transitions.

**B. Q/dashboard instrumentation cost:** same builds, same fixed on-demand Q frequency, separately dashboard closed and open. Record Q latencies and counts, `getprop` subprocess spawn counts, `dumpsys display` spawn counts, daemon + app CPU. Verify data equivalence, note that #58's improvement is expected primarily under Q load.

**C. Lid optional feature delta:** on the same integrated candidate, alternate OFF/ON with lid open; compare activation cost vs steady ON (blocked Hall reader), with and without Q dashboard. Do not conflate Hall event work, display toggles, or UI CPU with steady-state overhead.

Report **raw per-window ticks, duration, median/range, absolute percentage points of one CPU core, effect size vs quantization**, and whether battery/thermal measurements are meaningful; do not present percentage differences from near-zero counts as verified energy savings. If too small to distinguish from noise, say so.

## 3. Next optimization workstream — watcher periodic CPU (RESEARCH ONLY)

The 1.7.0 baseline already uses an in-process Binder Settings provider, with ~500 ms safety sampling, ~20 ms post-event burst, ~1 s DRM safety checks. **Do not increase/decrease these values in #57/#58 without new evidence**; a lower poll rate can delay TOP-only lower-panel repair.

1. Establish a CPU attribution profile by thread, call type and command: time spent in Settings Binder GET, generation checks, DRM read/parse, property reads, repair scheduling and telemetry. Use available existing counters first.
2. Reconstruct coverage of normal notifications vs safety polling, including events lost across sleep/wake, boot, provider restart and TOP/BOTH transitions. Inventory which checks are essential for recovery if callbacks fail.
3. Investigate bounded optimizations: avoid identical expensive snapshots when generation/state is unchanged; event-trigger coalescing; read-only caching with clear invalidation; scheduling of diagnostic-only work outside the watcher critical path. Require explicit conditions and preserve fail-closed semantics.
4. Build a host event trace/replay of slow/missing callbacks and reactivation races. Compare candidate against existing wake-repair deadlines and single-writer hardware ordering. Never infer that fewer calls equals acceptable latency.
5. Deliver a separate, **new draft research branch/PR** only if a candidate shows a plausible, measurable benefit and host tests pass; keep #57/#58 narrow. Physical benchmark and runtime deployment require a distinct approval.

**Critical acceptance:** preserve correctness of True Bottom Screen Off, wake repair, CPU Fix safety, BootSafety, daemon identity, all Q telemetry. No forced frequency, DSI/DRM writes, kernel tweaks, firmware alteration or merge/release.

## 4. Existing PR coordination after diagnostics merge

PR #55 diagnostics was merged into `main`; passive sleep-energy experiments
were separated into draft [#59](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/59).
RC2 includes the diagnostics and (after successful review) candidate read-path
changes from #58. Do **not** treat any RC2 result as proof of Lid ON energy
savings.

PR #57 remains a separate Lid Protection maintenance draft with Hall race
and sleep/wake physical validation gates. If #57 is integrated later,
reconcile its `DisplayActionCoordinator` changes against the already-merged
#58; do not accidentally revert the paired DRM reads. Keep #37 120 Hz
research independent.

## Deliverables for the Codex handoff

A single private bundle (outside Git/PR) containing tested hashes, exact test/CI results, dependency/merge-conflict resolution, a comparison table of OFF baseline vs ON overhead, Q parity, optional next watcher hypothesis with falsifiable test, and a sanitized public summary. Keep raw dumps, APK signing secrets, private paths and device logs off GitHub.
