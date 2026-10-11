# APP DIAGNOSTICS — bounded, on-demand app observability

Scope: **PR #55 (diagnostics-only candidate)**. Passive battery/sleep-energy measurements are intentionally deferred to a separate, stacked research PR. Nothing in this PR estimates watts or physical panel energy.

## Product behavior

- **APP DIAGNOSTICS** previews a sanitized report and allows **Save report** through Android's system document picker. No automatic upload, clipboard export, background stream or device change.
- The app retains at most 64 allowlisted Activity/foreground-telemetry events in its private preferences. The one journal includes typed mode, CRTC and daemon-status observations from the *existing* authenticated Q responses. Consecutive stable samples are required for observed mode/CRTC transitions. Identical samples are not appended. Observations do not prove actual panel power, refresh or cause.
- On demand, Android 11+/API 30+ `ApplicationExitInfo` is queried for at most eight *app-package* exit reasons. It does not cover the privileged root daemon. `USER_REQUESTED`, empty, unsupported and unavailable outcomes are explicitly distinguished.
- The report schema remains `JESTY_THOR_DIAGNOSTICS_V1` (bounded to 24,000 characters); all timestamps are converted to ages and raw process names, serials, paths, stack traces, PIDs/UIDs, boot IDs and system logs are excluded.
- A resumed Activity never infers missing events from its time spent paused. Stored history can lose its final asynchronous write if Android terminates the process immediately after an event.

## Deliberate separation from sleep-energy experiments

The diagnostics-only UI **does not** expose a Sleep trial action, capture battery/clock endpoints at Activity pause/resume, or depend on `PassiveSleepMonitor`/`PassiveSleepTrial`. The original opt-in implementation, worksheet, offline estimator and prior rejected overnight result are preserved on the independent research branch based on this diagnostics branch. That experiment is not an instrument-calibrated energy measurement; its earlier USB-confounded endpoint correctly yielded UNKNOWN Wh/W. No energy savings are claimed.

The app diagnostic report therefore contains app-event, telemetry and exit information only. The export format and its safety limits remain stable; the optional `passive_sleep` record is exclusively a concern of the research branch.

## Safety and validation

- No new `Q` requests, polling cadence, daemon commands, privileged binary invocations, display writes, wake/sleep scheduler, CPU property change or service restart.
- Existing `tests/AppDiagnosticReportTest.java` covers allowlists, event retention, typed transitions, unknown values, export bounds and relative timestamps. `scripts/test-app-diagnostics.ps1` compiles/runs this pure-Java test and verifies the source is not issuing privileged commands or dumping raw exit fields.
- CI builds the unsigned Android APK and runs the ordinary boot/lid/dashboard/security and emulator suites.
- Partial supervised Thor validation on 2026-10-09: diagnostics preview, readable sanitized report, file-picker save/cancel and a controlled app-only force-stop followed by a `USER_REQUESTED` exit record. See `APP-DIAGNOSTICS-VALIDATION-20261009.md`. This was on an earlier combined research candidate and does not substitute for final diagnostics-only APK acceptance.
- Still required: final diagnostics-only build, recreation while picker is open, unavailable picker, unsupported/empty exit history, crash/LMK reason interpretation, Android foreground/background lifecycle and policy review.

**Release gate:** remain draft. No merge, signed release, physical device activity or ADB without owner approval; the final APK must pass its own acceptance.
