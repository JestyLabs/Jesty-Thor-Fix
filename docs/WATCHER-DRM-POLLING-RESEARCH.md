# Watcher / DRM polling reduction — measured prototype

Status: **UNTESTED on hardware. Research branch only. Stable v1.6.0 remains unchanged.**

Research record: `JTF-RR-20261007-WATCHER-POLLING`

## Why this exists

The current v1.6.0 daemon is functionally correct, but its mode watcher calls the hidden Settings provider every **20 ms** (about 50 samples/s). When True Bottom Screen Off is active and the boot hold is clear, `DisplayActionCoordinator` also checks DRM often enough to permit a fresh CRTC read about every 250 ms. The old watcher path read TOP and BOTTOM through two independent debugfs opens.

This is technical debt, not a proven battery/performance bug. The project rule is therefore:

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

- that the old polling consumes enough CPU or power to matter to a user;
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

Callbacks improve latency; they are **not a correctness dependency**. If every callback is lost, the watcher still samples mode within 500 ms and DRM within 1 s.

## Expected request-rate reduction

These are source-derived upper-order figures, not device measurements:

| Path | v1.6.0 idle | prototype idle |
|---|---:|---:|
| Settings mode samples | ~50/s | ~2/s |
| Settings reduction | — | ~96% |
| Watcher DRM cycles | up to ~4/s | ~1/s safety |
| debugfs opens per watcher cycle | 2 | 1 |

During a real display transition the prototype temporarily returns to 20 ms sampling, so response latency is not traded permanently for low idle activity.

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

`scripts/measure-watcher-load.ps1` is read-only and measures one daemon PID over a fixed idle window using `/proc/PID/stat`, context switches and `/proc/PID/io` when readable.

## Candidate identity / version plan

The research APK intentionally keeps:

- package versionName: **1.6.0**
- versionCode: **70**

but uses daemon wire identity:

`1.6.0-watcher-exp1`

This lets an in-place signed test replace stable v1.6.0 through the existing authenticated BOTH-only previous-daemon gate without consuming a production version number.

If the A/B is a physical PASS and the measured reduction is useful, promotion should be its **own release: v1.7.0 / versionCode 71**. Before promotion:
- restore `DaemonIdentity.RUNTIME_ID = VERSION`;
- keep stable `1.6.0` in the guarded previous-daemon allowlist;
- decide whether the once-per-minute research metric log stays or is removed;
- update release notes/changelog;
- run a fresh signed production RC and physical gate.

If the benefit is negligible, close the research PR and ship nothing.

## Measurement plan

### A — stable v1.6.0 baseline

Dashboard closed. Capture separately:

1. BOTH idle, 60 s.
2. TOP true-off idle, 60 s.
3. Optional naturally screen-off idle, 60 s if no interaction is required.

Run:

```powershell
.\scripts\measure-watcher-load.ps1 -Seconds 60 -Label stable-both
```

and equivalent labels for each state.

### B — research candidate

Repeat the exact same states and durations. Do not compare a game/load sample with an idle baseline.

Primary evidence:
- watcher sample rate;
- watcher DRM-read rate;
- daemon CPU ticks/s;
- context-switch rate;
- read-syscall rate when `/proc/PID/io` is readable.

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
