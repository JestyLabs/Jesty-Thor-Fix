# Energy, gameplay and CPU attribution — optional study, NOT RUN

Code76 physical daemon process CPU and quiet Android suspend metrics exist in VALIDATION-EVIDENCE.md. Those are not calibrated system energy, mAh battery runtime, gameplay FPS, frame time or user-perceived latency. Thread-1 sampled CPU ticks do not reveal its Java call stack. Avoid saying TOP is inefficient solely because it uses ~0.32% versus ~0.09% of one CPU core in BOTH.

## Optional A/B/A study, only after real offline attribution

For every matched A/B/A period record:
- exact firmware, source SHA, APK version/hash, daemon identity and toggles;
- BOTH/TOP, actual physical DRM state, screen brightness/refresh and fixed visual workload;
- Wi-Fi/Bluetooth and USB/charging status, battery SoC, fan/temperature and ambient conditions;
- meter model/calibration/sample period, watts or watt-hours with uncertainty; if sysfs-derived, label as estimate/proxy;
- time length, warmup and cool-down, scene repeatability, order and number of trials;
- per-run median/spread/variance and private raw time series with sanitized public summary;
- unchanged 500ms watcher / 1s DRM safety cadence, wake repair and abort criteria.

Improvement is established only if effect exceeds noise and correctness remains unchanged. Do not compare charging snapshots with unplugged ones and call it battery benefit.

## Optional game performance sanity matrix

Record title/emulator version, capped FPS, fixed game scene, resolution, frame pacing distribution (median/p95/p99 milliseconds where instrumentation permits), thermals and input method. Compare stable A/B/A only if users report a regression. Do not add permanent instrumentation/polling to collect this data. Frame-time evidence is distinct from daemon idle CPU.

## Evidence tiers

| Finding | Supported tier | Not established |
| --- | --- | --- |
| CPU medians and suspend windows | Pinned physical code76 | Per-method attribution, Wh/mAh or battery runtime |
| Host updater/boot/IPC policies | Host models and disposable Android fakes | Vendor Binder, native SELinux, physical display or full production updater |
| Android 13 emulator install | Disposable-key sessions | Signed public APK installed on Thor, vendor bridge |
| Published v1.7.1 provenance | Release metadata plus independently rehashed archived APK | Fresh network download or physical install of exact UI-only published APK |
| PR #67 runtime modifications | Host/CI and pending integration evidence | New-device acceptance |

Decision: optional non-blocking protocol; no physical experiment authorized.
