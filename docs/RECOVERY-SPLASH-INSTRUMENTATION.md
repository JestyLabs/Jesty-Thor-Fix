# Recovery splash instrumentation and test plan

Status: prototype/test workstream only. No release, version bump, main-branch behavior change, splash renderer, or CPU restart behavior change.

## Proven baseline

The current boot path already provides the timing/provenance anchors needed around the CPU restart:

- `CPU_GATE_READY` — CPU-only gate has completed its prerequisites.
- `CPU_ATTEMPT_RESTART_REQUESTED` — the one-restart attempt was durably persisted before the restart thread starts.
- `CTL_RESTART_SENT` / `CTL_RESTART_ACK` — actual composer restart command request/ack.
- `HELPER_COMPOSER_NEW_PID` — successor composer observed.
- `HELPER_SF_NEW_PID` — successor SurfaceFlinger observed.
- `HELPER_ZYGOTE_NEW_PID` — successor zygote observed.
- `HELPER_SYSTEM_SERVER_NEW_PID` — successor system_server observed.
- `HELPER_PACKAGE_SERVICE_FOUND` / `HELPER_SETTINGS_SERVICE_FOUND` — framework services observed in the replacement system_server.
- `HELPER_PM_READY` — package path is usable before successor-daemon launch.
- `GATE_BOOT_COMPLETED value=1` — successor daemon observes framework boot completion.
- `BOOT_READY` — display reconciliation has completed and BootSafety is released.

The existing one-restart provenance remains authoritative. Splash code must never write the CPU property, request a composer restart, mark a CPU attempt APPLIED/FAILED, or weaken BootSafety.

## Proposed splash trace schema

Every event keeps the existing `BootTrace` common fields: `elapsed_ms`, `action`, `pid`, `boot_id`, and `source`.

| Action | Meaning | Recommended detail |
| --- | --- | --- |
| `SPLASH_ARMED` | Prototype is eligible for this boot-scoped restart attempt. | `attempt_phase=RESTART_REQUESTED` |
| `SPLASH_WAIT_SF` | Splash path is waiting for replacement SurfaceFlinger. | `old_sf=<pid>;timeout_ms=<measured policy>` |
| `SPLASH_SHOW_REQUESTED` | Show operation has been issued after successor SF was proven. | `sf_pid=<pid>` |
| `SPLASH_SHOWN` | The implementation has positive evidence that the splash is visible/committed. | `sf_pid=<pid>;show_ms=<delta>` |
| `SPLASH_REMOVE_REQUESTED` | Cleanup was issued. Normal removal should use `reason=FRAMEWORK_RECOVERED`. | `reason=FRAMEWORK_RECOVERED|VISIBLE_TIMEOUT|BOOT_READY|FAILURE` |
| `SPLASH_REMOVED` | Positive evidence that the branded layer/window no longer exists. | `visible_ms=<delta>;reason=<same reason>` |
| `SPLASH_TIMEOUT` | A bounded splash-only deadline expired. | `phase=WAIT_SF|SHOW|VISIBLE|REMOVE;age_ms=<delta>` |
| `SPLASH_FAIL_OPEN` | Splash work is abandoned without blocking boot/CPU/display recovery. | `reason=<token>;cleanup_requested=0|1` |

Do not invent production timeout values yet. The pure model takes an injected timeout policy so actual values can be chosen from physical Thor traces.

## User-visible timing measurements

Use the boot-relative `elapsed_ms` domain already shared by daemon/helper traces.

1. Restart requested: `CTL_RESTART_SENT`.
   - Keep `CPU_ATTEMPT_RESTART_REQUESTED` separately as the durable provenance point.
2. Successor SurfaceFlinger detected: `HELPER_SF_NEW_PID`.
3. Splash visible: `SPLASH_SHOWN`.
4. Framework recovered: timestamp of `SPLASH_REMOVE_REQUESTED reason=FRAMEWORK_RECOVERED`.
   - Correlate it with `HELPER_SYSTEM_SERVER_NEW_PID`, package/settings service readiness, and successor `GATE_BOOT_COMPLETED value=1`.
5. Splash removed: `SPLASH_REMOVED`.
6. Boot ready: `BOOT_READY`.

Useful deltas:

- black-before-splash = `SPLASH_SHOWN - CTL_RESTART_SENT`
- SF-to-splash = `SPLASH_SHOWN - HELPER_SF_NEW_PID`
- splash-visible = `SPLASH_REMOVED - SPLASH_SHOWN`
- restart-to-framework = framework-recovered - `CTL_RESTART_SENT`
- framework-to-boot-ready = `BOOT_READY` - framework-recovered

## Host-test coverage added

`RecoverySplashModel` is deliberately pure and has no Android/process/display side effects.

Covered by `RecoverySplashModelTest`:

- feature flag / boot-scope / restart-request arming gate;
- normal state path:
  `ARMED -> WAITING_FOR_SF -> SHOW_REQUESTED -> SHOWN -> REMOVE_REQUESTED -> REMOVED`;
- SurfaceFlinger wait timeout -> `SPLASH_TIMEOUT` + fail-open;
- show acknowledgement timeout -> `SPLASH_TIMEOUT` + fail-open;
- visible lifetime timeout -> cleanup request before fail-open;
- removal timeout -> fail-open;
- explicit show/remove failure -> fail-open;
- CPU restart independence:
  splash transitions leave a `RESTART_REQUESTED` CPU attempt in `WAIT_FOR_RESTART` while the baseline composer is still present, and only a successor composer can advance it to `MARK_APPLIED`.

`scripts/test-boot-lid.ps1` also guards that the pure splash model contains no `CpuFixController`, `ctl.restart`, composer-service restart literal, `setprop`, or `ProcessBuilder`.

## Minimal runtime integration later

Keep runtime work on a separate prototype path/flag.

1. Arm only after the existing CPU attempt has already been durably persisted as `RESTART_REQUESTED`.
2. Record `SPLASH_ARMED`, then enter `SPLASH_WAIT_SF`.
3. Reuse the helper's proven successor-SF detection; do not create a second restart detector.
4. Only after `HELPER_SF_NEW_PID`, request the splash and record `SPLASH_SHOW_REQUESTED`.
5. Record `SPLASH_SHOWN` only on positive renderer/layer evidence, not merely because a command was issued.
6. Remove on independently proven framework recovery. Removal must also be attempted on visible-lifetime timeout, boot-ready, and helper exit.
7. Any splash error/timeout is UX-only: emit `SPLASH_FAIL_OPEN`, keep CPU provenance and BootSafety logic unchanged, and continue boot recovery.
8. Do not make `BOOT_READY`, display reconciliation, wake-lock release, or CPU APPLIED depend on splash success.

## Manual Thor checklist

Run with a signed prototype candidate only; no release.

### BOTH cold boot

- [ ] First vendor boot animation appears normally.
- [ ] Second vendor boot animation remains suppressed.
- [ ] One and only one `CPU_ATTEMPT_RESTART_REQUESTED`.
- [ ] One and only one `CTL_RESTART_SENT`.
- [ ] `HELPER_COMPOSER_NEW_PID` and `HELPER_SF_NEW_PID` appear.
- [ ] Splash appears during the previously black interval.
- [ ] `SPLASH_SHOWN` follows successor SF detection.
- [ ] Splash is removed when framework returns.
- [ ] `SPLASH_REMOVED` appears.
- [ ] `BOOT_READY` appears.
- [ ] No overlay remains after unlock/home is usable.

### TOP cold boot

- [ ] Same restart/splash checks as BOTH.
- [ ] `BOTTOM_OFF_CONFIRMED` still happens only after the existing display gate/reconciliation.
- [ ] Bottom display remains truly off after boot.
- [ ] No splash/window leaks onto the lower panel.

### Reboot twice

For each reboot independently:

- [ ] New kernel `boot_id`.
- [ ] Exactly one CPU restart request.
- [ ] Exactly one actual composer restart request.
- [ ] Splash is removed.
- [ ] No stale overlay/layer/window survives into the next boot.
- [ ] No `CPU_ATTEMPT_RESTART_REQUESTED` retry within the same `boot_id`.

## Physical-only validation

The following cannot be proven realistically by host tests:

- whether a SurfaceFlinger/SurfaceControl layer is actually visible on the Thor;
- exact first committed visible frame and perceived black-gap duration;
- z-order relative to vendor/system surfaces during framework recovery;
- whether a renderer survives/reconnects correctly across the Thor's SurfaceFlinger restart;
- whether the layer appears only on the intended physical display in BOTH/TOP;
- actual cleanup after renderer/helper death or SurfaceFlinger churn;
- absence of a persistent overlay after home/unlock;
- interaction with the Thor firmware's boot animation suppression timing;
- production timeout values.

These stay physical-only and must be evidenced by trace + video/observation.

## Risks and stop conditions

Stop the splash prototype and fall back to the current suppression-only behavior if any of the following occurs:

- a second `CTL_RESTART_SENT` appears in the same `boot_id`;
- CPU attempt provenance no longer reaches the same APPLIED/FAIL_SAFE outcomes as the suppression-only baseline;
- BootSafety/display-gate timing or ordering changes because of splash state;
- splash failure blocks framework recovery, display reconciliation, wake-lock release, or `BOOT_READY`;
- removal cannot be made bounded/best-effort and a persistent overlay is observed;
- splash appears before successor SurfaceFlinger is proven;
- splash appears during the first vendor boot animation;
- TOP mode changes lower-panel reconciliation behavior;
- cleanup depends on system_server code that is itself being restarted.

Until those physical checks pass, the splash remains prototype-only.
