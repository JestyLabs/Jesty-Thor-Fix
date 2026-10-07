# Watcher / DRM polling investigation

Status: **measurement-only candidate preparation**  
Stable baseline: **v1.6.0**  
Release behavior changed by this branch: **no**

## Why this exists

The display watcher is reliable on the tested Thor, but its steady background cost has never been measured directly. That is an efficiency question, not a known gameplay-performance bug.

**No micro-stutter, thermal regression, frame-time regression or battery drain has been demonstrated as being caused by the watcher.** Do not turn an unmeasured implementation cost into a user-facing defect claim.

The rule for this workstream is simple:

> Measure first. Change cadence only if the benefit is real and display/wake correctness stays equal or better.

## Current v1.6.0 behavior — code facts

### Settings mode sampling

`DaemonWatchThread.smali` obtains the hidden Settings provider once, then calls `GET_system dual_screen_display_mode` in a loop with:

```
Thread.sleep(20)
```

So the current implementation can issue roughly **50 Settings-provider calls per second** while the watcher is running.

This cadence is not limited to unknown mode; it is the watcher loop's normal cadence.

### DRM reads

`DisplayActionCoordinator.onWatcherSample()` does **not** read DRM on every 20 ms mode sample.

In a stable, non-urgent state it returns until at least 250 ms has passed since the previous hardware read. When a hardware check is due it currently calls:

- `Telemetry.topCrtcActive()`
- `Telemetry.bottomCrtcActive()`

Those are two separate opens/parses of the DRM debugfs state. The coordinator can therefore perform up to:

- 4 physical checks/s
- **8 individual DRM state opens/s**

in the stable fix-enabled path.

Urgent wake repair is intentionally allowed to bypass the steady-state throttle.

### Visible dashboard

`TelemetryPoller` sends one authenticated `Q` request per second **only while the dashboard Activity is visible**. The daemon snapshot reads TOP and BOTTOM again for user-facing telemetry.

That dashboard cost is not part of the closed-app "set and forget" background path.

## What is actually unknown

The code-path frequency is known. The following are **UNTESTED**:

- daemon process CPU time attributable to the watcher in stable idle;
- whether the Settings-provider calls materially affect idle residency;
- whether reducing them produces a measurable system-power difference;
- whether current polling has any measurable frame-time effect during games;
- whether all AYN mode transitions emit a dependable callback that can replace fast polling.

No public claim should convert those unknowns into "micro-stutter" or similar.

## Measurement candidate

The diagnostic candidate adds counters only; it does not change watcher decisions or timing.

Counters:

- `watch_mode_samples` — Settings watcher samples;
- `watch_drm_open_attempts` — daemon DRM-state open attempts;
- `watch_display_events` — DisplayManager callback events;
- `watch_elapsed_ms` — wall time since daemon start;
- `watch_process_cpu_ms` — daemon-wide process CPU time since daemon start.

The fields are added to the authenticated `Q` snapshot and logged as `WATCHER_COST` whenever the visible dashboard requests telemetry.

A useful background A/B measurement is therefore:

1. install/start the diagnostic candidate and capture one `WATCHER_COST` line;
2. close/swipe away the dashboard while leaving the background daemon running;
3. leave a fixed state untouched for the test interval;
4. reopen the dashboard and capture the next line;
5. subtract counters and process CPU time.

The short dashboard-open endpoints are outside the long measurement window and can be kept identical between runs.

## Test matrix

Run each condition with the same brightness, refresh mode and charger state.

### A. Stable BOTH

- True Bottom Screen Off enabled;
- BOTH mode;
- dashboard closed;
- 10 minutes idle.

### B. Stable TOP / true off

- True Bottom Screen Off enabled;
- TOP mode;
- lower CRTC confirmed inactive;
- dashboard closed;
- 10 minutes idle.

### C. Normal game load

- same game / scene / emulator settings;
- dashboard closed;
- 15–20 minute window;
- record daemon CPU delta and, if available, independent frame-time data.

This test is specifically to look for evidence. Do not infer micro-stutter from polling frequency alone.

### D. Transition correctness

Exercise:

- BOTH -> TOP;
- TOP -> BOTH;
- TOP sleep -> wake;
- known lower-panel false-wake repair;
- lid guard disabled;
- lid guard enabled, if separately safe.

Record mode-change timestamp, DisplayManager callback timing, CRTC convergence, and repair completion.

## Candidate design if measurement justifies change

### 1. Event-driven mode notification

Android exposes a per-setting URI and ContentService observer registration. A root `app_process` daemon should be able to prototype a hidden `IContentService` / `IContentObserver` registration for:

`content://settings/system/dual_screen_display_mode`

This is a **HYPOTHESIS until physically proven on the Thor**. The candidate must prove:

- the callback fires for every AYN TOP/BOTH change;
- it survives normal sleep/wake;
- it is re-registered after framework/system_server replacement;
- no permission/AttributionSource edge case appears on the Thor firmware.

The existing Settings call remains the authoritative read; an observer notification is only a reason to read immediately.

### 2. Adaptive safety polling

Pure policy preparation is in `WatcherCadenceModel`.

Proposed starting values:

- unknown mode: 20 ms fast poll;
- pending wake repair: 20 ms fast poll;
- display/mode event: 1.5 s fast burst;
- known stable state: 1000 ms safety poll.

These are starting test values, not release promises.

### 3. DRM only when useful

Candidate policy:

- immediate read after an observed mode/display event;
- current fast behavior while repair is pending;
- 250 ms during a short transition burst;
- 1000 ms stable safety check;
- use `Telemetry.crtcActivePair()` where both CRTC values are needed so one DRM open provides a consistent pair.

The existing `crtcActivePair()` implementation is already used by the boot gate, so this optimization can be evaluated without inventing a new parser.

## Acceptance gates

A production change must satisfy all of these:

- no missed BOTH/TOP transitions in the supervised transition set;
- final CRTC state identical to v1.6.0;
- wake-repair behavior and its 400/700 ms timing semantics preserved;
- watcher health never becomes falsely STALLED;
- no extra display power writes;
- no extra compositor/framework restart;
- no new boot behavior;
- a repeatable reduction in watcher process CPU and/or I/O compared with v1.6.0.

If the measured baseline cost is already negligible, **NO-GO**: keep v1.6.0 behavior.

## Version plan

- measurement / instrumentation candidate: no stable version bump; signed PR test candidate only;
- small proven cadence + paired-DRM optimization with unchanged behavior: candidate for **v1.6.1**;
- replacing the polling watcher with a new observer lifecycle across boot/framework recovery: treat as a larger architecture change and prefer **v1.7.0** unless review proves it is genuinely patch-sized.

No release number is committed until the A/B evidence exists.
