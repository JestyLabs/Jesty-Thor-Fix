# Watcher / DRM polling reduction — measurement prototype

Status: **Measured idle CPU benefit; INCONCLUSIVE for production promotion. Published v1.6.0 remains the stable recommendation.**

Continuation results: [2026-10-08 validation](WATCHER-VALIDATION-2026-10-08.md).

Research record: `JTF-RR-20261007-WATCHER-POLLING`

## Why this exists

The current v1.6.0 daemon is functionally correct, but its mode watcher calls the hidden Settings provider every **20 ms** (about 50 samples/s). When True Bottom Screen Off is active and the boot hold is clear, `DisplayActionCoordinator` also checks DRM often enough to permit a fresh CRTC read about every 250 ms. The old watcher path read TOP and BOTTOM through two independent debugfs opens.

This is technical debt, not a proven battery/performance bug. If there is a meaningful cost, the most plausible first-order effect is unnecessary background wakeups/idle work; a frame-time or micro-stutter effect would require separate evidence. The project rule is therefore:

> do not ship a polling reduction unless a measured A/B shows a useful reduction and the physical display behavior stays equivalent.

## Current-source findings

**PROVEN from v1.6.0 source**

- `DaemonWatchThread.smali` loops through Settings provider `GET_system dual_screen_display_mode` and then sleeps 20 ms.
- `DisplayEventManager` already registers an `IDisplayManagerCallback`.
- Relevant TOP/BOTTOM display callbacks already reach `DisplayEventCallback`.
- `WatcherSupervisor` calls a watcher stalled only after 5 s without a valid sample.
- `Telemetry.crtcActivePair()` already exists and reads both Thor CRTCs from one coherent DRM state open.
- Boot display readiness already uses a separate 500 ms `BootGateModel` cadence, so this prototype does not replace that gate.

**NOT PROVEN**

- that the measured daemon CPU reduction improves battery life or gameplay;
- that every AYN mode change emits a useful DisplayManager callback;
- that debugfs polling causes compositor jank;
- that this prototype is release-safe before physical transition/wake testing.

## Design

This prototype deliberately avoids adding another hidden Settings observer/Binder contract.

Instead it reuses the DisplayManager callback that the daemon already depends on:

1. **Idle Settings safety poll: 500 ms.**
2. A relevant display callback wakes the sleeping watcher immediately.
3. That event opens a **1.6 s burst** at the existing **20 ms** cadence.
4. A discovered mode change also opens/extends the same burst.
5. Wake-repair scheduling wakes/extends the burst.
6. DRM is read only when:
   - the mode changed;
   - a relevant display event is still unconsumed;
   - wake repair is due;
   - stable TOP wake detection requires physical confirmation; or
   - the **1 s DRM safety interval** expires.
7. Watcher CRTC reads use one `crtcActivePair()` snapshot instead of separate TOP/BOTTOM file opens.
8. Dashboard `Q` telemetry also uses one paired DRM snapshot.

Callbacks can improve latency; they are **not a correctness dependency**. If every callback is lost, the scheduled safety waits remain 500 ms for mode and 1 s for DRM, plus processing/scheduling time. Hardware testing on firmware .377 observed callbacks on a wake path but none on the initial physical mode edge; acceleration is not guaranteed.


## ContentObserver fallback — not first prototype

Android also exposes an exact per-setting observer path through `Settings.System.getUriFor(name)` and hidden `IContentService.registerContentObserver(...)`. That gives a plausible second event source for:

`dual_screen_display_mode`

This branch intentionally **does not add that hidden Binder contract yet**. The daemon already has a working DisplayManager callback, and the 500 ms Settings safety poll keeps correctness independent of callbacks. Adding another hidden service contract before measuring the simpler design would increase lifecycle and framework-restart risk without proven benefit.

If physical testing shows the existing DisplayManager callback does not reliably accelerate AYN mode changes, a later research candidate may test a ContentObserver while preserving the same safety poll.

AOSP references:
- Android Settings source / `getUriFor`: https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/provider/Settings.java
- `IContentService.registerContentObserver`: https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/content/IContentService.aidl

## Expected request-rate reduction

These are source-derived upper-order figures, not device measurements:

| Path | v1.6.0 idle | prototype idle |
|---|---:|---:|
| Settings mode samples | ~50/s | ~2/s |
| Settings reduction | — | ~96% |
| Watcher DRM cycles | up to ~4/s | ~1/s safety |
| debugfs opens per watcher cycle | 2 | 1 |

After detecting a display transition, the prototype temporarily returns to 20 ms sampling. Initial detection without a callback can still wait for the 500 ms safety poll; the burst does not remove that tradeoff.

## Instrumentation

The research runtime adds counters to authenticated `Q` output and emits one `WATCHER_METRICS` log line roughly once per minute:

- watcher samples;
- relevant display events;
- mode changes;
- wake-repair pulses;
- watcher DRM reads;
- idle waits;
- burst waits;
- current burst state/cadence.

`scripts/measure-watcher-load.ps1` is read-only and measures the Thor daemon over a fixed idle window using `/proc/PID/stat`, context switches and `/proc/PID/io` when readable. When `com.android.providers.settings` has one stable PID, it also captures the same deltas for SettingsProvider so the A/B does not ignore server-side Binder work.

## Candidate identity / version plan

The research APK intentionally keeps:

- package versionName: **1.6.0**
- versionCode: **70**

but uses daemon wire identity:

`1.6.0-watcher-exp2`

Exp1 was replaced after an inherited CPU provenance import defect surfaced
during the authorized in-place handover. Exp2 preserves matching completed
proof without replaying its earlier phase; see the continuation report.

This lets an in-place signed test replace stable v1.6.0 through the existing authenticated BOTH-only previous-daemon gate without consuming a production version number.

If the A/B is a physical PASS and the measured reduction is useful, promotion should be its **own release: v1.7.0 / versionCode 71**. Before promotion:
- restore `DaemonIdentity.RUNTIME_ID = VERSION`;
- keep stable `1.6.0` in the guarded previous-daemon allowlist;
- decide whether the once-per-minute research metric log stays or is removed;
- update release notes/changelog;
- run a fresh signed production RC and physical gate.

If the benefit is negligible, close the research PR and ship nothing.

## 2026-10-08 host review: measurement correctness

The source review found a defect in the **read-only A/B measurement harness**, not a proven runtime fault in the experimental watcher. `Invoke-AdbText` takes one `[string[]] $Arguments` parameter but multiple callers used positional array splatting. The shell/command tokens could be bound as independent function arguments. All such calls now use `Invoke-AdbText -Arguments @(...)`. The host test contract rejects a regression.

Two process-identity protections were also added:

- Snapshot `/proc/PID/stat` **field 22 (`starttime`)**, in addition to PID, to reject recycled daemon processes during a measurement and mark SettingsProvider results unavailable on PID reuse.
- Snapshot `/proc/PID/task/TID/stat` **field 22** and exclude reused thread IDs instead of combining unrelated CPU/context-switch counters.

This makes process deltas more trustworthy; **neither change establishes a real-device performance improvement**. The measurement script still needs one read-only end-to-end run with `adb` and device owner supervision to verify remote shell compatibility and the output fields. The existing CI parser/host contract does not emulate Android's `/proc` behavior.

**Interpretation caveat:** the final printed `WATCHER_METRICS` entry is simply the latest logcat match. It may precede the actual sampling window and is cumulative since daemon startup. Do not treat it as a per-window A/B delta; the actual comparison should use the bounded `/proc` deltas and, if available, timestamp-matched Q counter differences.

**Gating:** keep this PR draft. The safe host-only fix does not authorize an APK installation, daemon handover, restart or reboot.

## Measurement plan

### A — stable v1.6.0 baseline

Dashboard closed. Capture separately:

1. BOTH idle, 60 s.
2. TOP true-off idle, 60 s.
3. Optional naturally screen-off idle, 60 s if no interaction is required.

Run:

```powershell
.\scripts\measure-watcher-load.ps1 -Serial '<confirmed-thor-serial>' -Seconds 60 -Label stable-both -OutputPath 'C:\Temp\stable-both.json'
```

and equivalent labels for each state.

### B — research candidate

Repeat the exact same states and durations. Do not compare a game/load sample with an idle baseline.

Primary evidence:
- watcher sample rate;
- watcher DRM-read rate;
- daemon CPU ticks/s;
- daemon context-switch and read-syscall rate;
- SettingsProvider CPU/context-switch/read deltas when its PID is available and stable.

## Functional physical gate

Do **not** start with a reboot.

After the signed candidate hands over safely in BOTH:

1. BOTH idle: final TOP/BOTTOM CRTCs = 1/1.
2. BOTH -> TOP: lower physical CRTC reaches 0; no visible response regression.
3. TOP -> BOTH: lower physical CRTC returns to 1.
4. TOP sleep/wake: wake repair ends `OFF_OK`; lower CRTC returns to 0.
5. Repeat TOP wake once with the dashboard closed.
6. Leave TOP idle for at least 10 minutes: no spontaneous lower-panel wake and watcher remains RUNNING.
7. Only after those pass, one supervised cold boot may validate startup integration.

No BOTTOM ONLY or dock claim is added by this work.

## Acceptance gate

Promote only if **all** are true:

- host CI and CodeQL green;
- Settings idle samples fall by at least 90% versus the fixed 20 ms design;
- watcher DRM reads fall by at least 70% in steady idle;
- daemon CPU/context-switch measurements do not regress and show a useful reduction or clearly reduced wake/read activity;
- BOTH/TOP transitions remain correct;
- TOP sleep/wake repair remains correct;
- safety fallback is represented by host tests (500 ms mode / 1 s DRM even with no events);
- no watcher STALLED/FAILED state;
- no new CPU-fix/composer restart;
- no release until a signed exact candidate passes the physical gate.

## Stop conditions

Stop and keep v1.6.0 behavior if any of these occur:

- missed or visibly delayed TOP/BOTH transition;
- lower panel remains physically on after TOP wake;
- event storms keep the daemon permanently in burst;
- callback loss causes stale state beyond the documented safety interval;
- DRM reads increase under normal idle;
- CPU/context switching does not improve enough to justify extra complexity;
- any interaction with the v1.6.0 early CPU restart provenance path.

## Rollback note for the research APK

Because package versionCode remains 70, the stable v1.6.0 APK can be reinstalled in place. The running experimental root daemon has a research-only wire identity that the published v1.6.0 daemon does not know how to replace, so after reinstalling stable use one normal reboot to guarantee the research daemon is gone. Do not manually kill arbitrary `app_process` PIDs.
