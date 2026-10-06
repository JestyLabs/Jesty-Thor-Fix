# Current roadmap

Last updated: 2026-10-06.

This file tracks active research. Stable release behaviour stays unchanged until a change is physically validated on hardware.

## Active: cleaner CPU Fix recovery

The CPU Fix still needs one Qualcomm composer/framework restart so the vendor display stack consumes the property.

Already proven on the Thor:

- the restart provenance/fail-safe path survives the tested crash window;
- moving the restart earlier reduces visible startup time;
- the second AYN boot animation can be suppressed without adding another restart;
- the remaining black recovery gap is suitable for a short branded recovery splash.

Current splash prototype status:

- no-reboot probing now reaches the correct 1080×1920 top display;
- the first renderer failed because raw `app_process` text drawing aborts in Android Typeface initialization;
- the prototype was changed to a font-free bitmap/primitive renderer;
- current research head `6f6bf5354f393bcf90c46acb52048d9a2043e5f1` passed CI and CodeQL.

Next step: sign that exact candidate, install it in place, run the splash probe **without rebooting**, and only spend a cold reboot if show/remove/cleanup all pass.

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

- reduce watcher / DRM polling only after a safe event-driven replacement is measured;
- physical BOTTOM ONLY and dock validation;
- updater end-to-end install validation;
- minor dashboard wording / unavailable-state cleanup;
- continue removing Thor-specific assumptions behind explicit hardware profiles.

Historical planning and timing notes remain in [ROADMAP-AFTER-1.5.17.md](ROADMAP-AFTER-1.5.17.md).
