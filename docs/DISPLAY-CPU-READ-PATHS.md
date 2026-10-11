# Display and CPU Fix read-path optimization — independent draft

This is a follow-up **after** the merged PR #41 watcher optimization. It is intentionally separate from #57 (Lid Protection maintenance), #37 (mixed refresh), and #55 (energy/diagnostics).

## Verified architectural baseline

- The smali watcher uses an in-process Binder `IContentProvider.call(GET_system)`, **not** a shell `settings get` every 500 ms.
- The already validated cadence is 500 ms Settings safety samples, 20 ms short event bursts, and 1 s DRM safety. No interval or wake-repair policy is changed in this patch.
- CPU Fix is primarily a boot/restart property and compositor coordination mechanism, not a continuously spinning CPU polling loop.
- Existing short daemon-only CPU samples are not calibrated power measurements and do not support an additional savings claim.

## Scope of this patch

### Display Fix

During the less-frequent user/boot reconciliation and transition verification, read TOP and BOTTOM CRCTs from one `Telemetry.crtcActivePair()` snapshot rather than opening `/sys/kernel/debug/dri/0/state` separately for each CRTC.

This reuses the tested existing parser. It reduces two snapshots to one at the preflight and at final verification, and yields a single top/bottom observation per boundary instead of two potentially interleaved moments.

The wake-repair delayed-read loop, generation invalidation, lock structure, mode confirmation, `DisplayHardware.apply()`, and final hardware validation remain unchanged. A missing or malformed CRTC still produces `?` and the decision model fails closed.

### CPU Fix and dashboard/Q telemetry

`DaemonState.snapshot()` now reads `vendor.display.disable_system_load_check` from Android's in-process `SystemProperties.get(String,String)` via reflection. This avoids starting a `getprop` subprocess for every routine Q sample when the hidden API is accessible.

The return contract `0/1/?` is parsed with the existing `PropertyState.binary`. On reflection/access failure, it falls back to the original `getprop` implementation. **The CPU Fix boot/restart safety checks continue using the original `systemLoadFixObservation()` path.**

The Q snapshot still includes the exact same `system_load_fix` field, along with all other existing metrics and DRM CPU/power readings. The actual process/kernel data is observed afresh; no property-value cache or fabricated measurement is introduced.

## Non-goals

- No changes to the event-generation logic, DRM frequency, asynchronous wake repair, CPU governor/frequency, vendor daemon restart, sleep, PServer, or user settings.
- No removal or lower cadence of telemetry fields.
- No change to Lid Protection. PR #57 is a separate draft; both touch `DisplayActionCoordinator.java` and will need a careful rebase before either merge.
- No claim that read-path savings equal whole-device battery savings. Q optimization only helps when Q is queried; paired DRM reads primarily help during transitions, not steady idle.

## Offline verification

- `DisplayDecisionModelTest`, `DisplayGenerationModelTest`, `WatcherCadenceModelTest` (232 assertions), `PropertyStateTest` PASS.
- `TelemetrySnapshotTest` six host SystemProperties fixture cases PASS: `0`, `1`, empty, missing, invalid and whitespace.
- `DisplayOptimizationSafetyTest` six host scenarios PASS: TOP/BOTTOM normal/reversed/malformed/missing, and fail-closed reconciliation.
- Run `powershell -NoProfile -File scripts/test-display-cpu-read-paths.ps1` on Windows (requires Java/JDK). Full APK build and Android behavior require separate CI/owner validation.

## Requested next physical measurements — DO NOT EXECUTE YET

After owner authorization, compare the same final signed published baseline to an instrumented candidate with matched firmware/mode/brightness/background load. Use the already approved `measure-watcher-load.ps1` script and Q counts:

1. Idle BOTH and TOP with dashboard closed. Do **not** expect a large gain from this patch: the steady 500 ms/1 s watcher remains unchanged.
2. Same bounded Q sample frequency with dashboard open: record daemon/app/settings CPU ticks, `getprop` subprocess observations if available, Q latency and all existing fields, including `system_load_fix`.
3. Normal user toggle and wake repair after sleep: preserve CRTC confirmation, deadlines, errors, compositor identity and latencies before/after.
4. Repeat matched windows, record host/firmware/APK hashes, quantization, absent telemetry and stop conditions. Use no writes or tracing beyond the reviewed allowlist without authorization.

Do not merge/release until APK build, security CI, boot/display/wake and normal CPU Fix regression gates pass, and results are reviewed independently.
