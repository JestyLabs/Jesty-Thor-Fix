# Current roadmap

Last updated: 2026-10-07.

Stable release behaviour stays unchanged until a change is physically validated on hardware.

## Active: quantify and reduce watcher / DRM background work

The current watcher is functional and has not been shown to cause gameplay stutter, thermal trouble or a user-visible performance regression. The remaining concern is narrower: its **background cost has not yet been quantified directly**, so the project should measure it before deciding whether a more event-driven design is worth the risk.

Current v1.6.0 implementation facts:

- the hidden Settings-provider watcher samples `dual_screen_display_mode` every **20 ms** (up to ~50 provider calls/s);
- while True Bottom Screen Off is enabled and the display is in a normal stable state, `DisplayActionCoordinator` throttles physical CRTC checks to at most once every **250 ms**;
- those steady coordinator checks currently read TOP and BOTTOM separately, so that is up to **8 individual DRM debugfs opens/s** from the coordinator;
- the visible dashboard adds its own once-per-second telemetry query while the Activity is open; that is not part of the closed-app background path;
- urgent wake-repair logic intentionally bypasses the steady throttle when fast confirmation is needed.

These are code-path counts, **not evidence of a performance problem**. No micro-stutter claim should be made without measurement.

### Investigation plan

1. instrument a test candidate with counters for Settings samples, DRM opens and display callbacks;
2. capture daemon process CPU time over fixed idle and gaming windows with the dashboard closed;
3. record transition latency for BOTH↔TOP, sleep/wake and the known bottom-screen wake-repair case;
4. prototype an adaptive policy:
   - event/display callback starts a short fast burst;
   - unknown/transition state uses a short fast cadence;
   - known stable state falls back to a **500–1000 ms safety poll**;
   - DRM is read on state changes / pending repair and at a slower safety cadence rather than on every mode sample;
5. compare the candidate against v1.6.0 on the same Thor before changing release behavior.

Go criterion: measurable reduction in daemon CPU/wakeups or I/O with no regression in display-state correctness or repair latency.

No-go criterion: if the current cost is already negligible, or an event-driven replacement misses transitions, keep the existing implementation.

See [Watcher / DRM polling investigation](WATCHER-POLLING-INVESTIGATION.md) once the research branch lands.

## Closed: CPU Fix startup recovery

v1.6.0 closed the late-second-recovery problem for the supported stock Thor path.

What is proven:

- `vendor.display.disable_system_load_check` is consumed by Qualcomm `ResourceImpl::Init()` and cached by the running composer;
- a late property change therefore needs a replacement composer before the fix becomes effective;
- same-process TOP/BOTH recreation does not reload that state;
- no supported same-process ResourceImpl reload path was found on the tested firmware;
- the inspected stock Thor init tree exposes no safe app-controlled hook early enough to set the property before the first composer starts;
- the stock `pservice -> /data/boot_start.sh` path starts slightly **after** the first composer, so it cannot provide a zero-restart first-composer proof;
- v1.6.0 uses that stock pservice path to perform the one required recovery during the natural startup window, with durable restart provenance and no later second CPU-fix recovery.

Therefore the current one-restart design is the best **safe app-only stock-firmware** solution established by the evidence. Eliminating the restart entirely would require one of:

- AYN/Qualcomm firmware changing the early vendor init behavior for this hardware;
- modifying/overlaying immutable vendor/init content;
- or new evidence of a trusted privileged mechanism that executes before the first composer.

The app intentionally does not spoof hardware subtype, patch `/vendor`, or install speculative system modifications to chase a zero-restart boot.

Historical splash / second-boot experiments are archived in [recovery-splash research](archive/RECOVERY-SPLASH-RESEARCH.md).

## Active research: TOP-mode focus / input

Some dual-screen cases may lose focus to the inactive lower display.

Jesty Thor Fix already powers the lower **physical** display off in TOP mode, but Android can still keep a logical display object around. We therefore do not claim this bug fixed yet.

Test plan:

1. reproduce the focus loss on stock TOP mode;
2. repeat with True Bottom Screen Off enabled;
3. compare focused display / input routing before and after;
4. only advertise a fix if the A/B result is repeatable.

## Next: 120 Hz screen tearing / mixed refresh

The upper Thor panel supports 120 Hz while the lower panel is physically 60 Hz. AYN exposes a 120 Hz system mode for the dual-screen setup, so the first question is **where the lower display is made to look or behave like 120 Hz**.

Read-only investigation should compare 60/60, 120/60 and the AYN 120 Hz dual-screen mode across:

- SurfaceFlinger display configs and vsync periods;
- Qualcomm HWC / SDM composition state;
- DRM / CRTC timing and vblank evidence;
- present fences and GPU composition.

Do not tune vsync offsets blindly. First prove whether the mismatch is in Android scheduling, Qualcomm HWC/SDM, DRM/DSI timing, or the panel configuration.

## Next: Retroid Pocket Duo compatibility

Goal: make the codebase ready to support more than one dual-screen handheld without weakening Thor safety.

Pocket Duo support is **not claimed yet**. Compatibility must be tested feature by feature:

- True Bottom Screen Off;
- display / CPU telemetry;
- wake repair;
- lid / hall behaviour;
- focus / input routing;
- whether the Thor-specific CPU Fix is needed at all.

We are looking for Pocket Duo owners willing to run read-only probes and supervised test builds.

See [Retroid SDM comparison](RETROID-SDM-COMPARISON.md) for the Qualcomm background.

## Deferred but still open

- physical BOTTOM ONLY and dock validation;
- updater end-to-end install validation;
- minor dashboard wording / unavailable-state cleanup;
- continue removing Thor-specific assumptions behind explicit hardware profiles.

Historical planning and timing notes remain in [ROADMAP-AFTER-1.5.17.md](ROADMAP-AFTER-1.5.17.md).
