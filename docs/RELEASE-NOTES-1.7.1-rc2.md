# Jesty Thor Fix v1.7.1-rc2 — diagnostics and read-path candidate

**PRE-RELEASE · owner-supervised testing build**. The prior v1.7.1-rc1 APK was published before the diagnostics PR #55 was merged; it **does not** contain this completed interface. This is a separately versioned candidate, not a replacement of the rc1 asset. **v1.7.0 remains the recommended stable release** until exact-build Thor checks and owner approval are complete.

## What's new since v1.7.1-rc1

- A discreet, accessible **cogwheel** at the bottom-right of the dashboard opens on-demand App diagnostics.
- The in-app view groups the last reported display/CPU state, Android app exit reasons and newest-first event history in readable language. App exit records concern the Android app, **not** the privileged daemon, and an Activity closing is not proof of a crash.
- **Save report** still exports the same sanitized machine-readable `JESTY_THOR_DIAGNOSTICS_V1` structure (not the human-friendly preview).
- A single bounded private journal stores up to 64 allowlisted app/telemetry events. Diagnostics reuse the dashboard's **existing once-per-second foreground Q replies** and do not introduce new root daemon queries, display polling, services, or background monitoring.
- VersionCode **73**, versionName `1.7.1-rc2`, and a narrowly gated upgrade handoff from an existing `1.7.1-rc1` daemon in BOTH with both CRTCs active and authenticated healthy watcher.
- Carries forward the rc1 updater cancellation and boot/CPU handoff changes.

## Display and CPU telemetry read-path optimization (PR #58)

- The coordinator reuses the established paired TOP/BOTTOM DRM CRTC parser at boot, user-triggered reconciliation and final display confirmation instead of opening the same debugfs state file twice. This reduces redundant I/O; it is **not** a claim that the physical panel state is an atomic hardware snapshot.
- On-demand Q/dashboard `system_load_fix` reads the property via in-process Android `SystemProperties` when available. If Android blocks reflection, the original `getprop` path remains as fallback. Values continue to be interpreted as `0/1/?`.
- The actual CPU Fix boot/restart safety logic and wake-repair cadence are unchanged. No new display/CPU tuning, automatic governor adjustment or background telemetry polling is introduced.
- Savings in CPU, Q latency, battery and gaming frame pacing are **unmeasured** until controlled Thor comparisons. This is experimental behavior included specifically for candidate validation.

## Safety / known limits

No True Bottom Screen Off, CPU Fix boot/restart or Lid Protection control policy is intentionally modified. In-app diagnostics perform bounded Java work and private preference writes when the dashboard is open: **incremental CPU cost is not guaranteed to be zero**. Quantitative CPU, battery and frametime equivalence is unknown until measured on the Thor.

**Not included:** PR #57 Lid maintenance, PR #59 passive sleep-energy experiments, PR #37 120 Hz research or speculative polling cadence changes. PR #58 read-path changes are included in this RC2 candidate.

**Validation state:** Offline CI/CodeQL and Android emulator results must be verified on this exact RC source SHA. The signed RC APK has **not yet been tested on physical Thor hardware** when these notes were written. No battery-savings claim or stable promotion is made.

## Installation & reports

Install only the **exact release-signed APK** identified by the CI candidate SHA-256, versionCode 73 and the established signer certificate. Prefer in-place installation over the existing package to keep preferences and history. Keep a verified stable rollback asset and do not clear app data to workaround a failed upgrade. Validate BOTH/TOP and wake before considering stable promotion.

See [RC owner/Codex handoff](docs/CODEX-HANDOFF-1.7.1-rc2.md), [diagnostics guide](docs/APP-DIAGNOSTICS.md) and [acceptance checklist](docs/APP-DIAGNOSTICS-RC-VALIDATION.md).