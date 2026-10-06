# Recovery splash instrumentation and test plan

Status: prototype/test workstream only. No release, version bump, main-branch behavior change, splash renderer, CPU restart behavior change, BootSafety change, or display-gate change.

## Proven baseline

The current boot path already provides the timing/provenance anchors needed around the CPU restart:

- `CPU_GATE_READY` — CPU-only gate has completed its prerequisites.
- `CPU_ATTEMPT_RESTART_REQUESTED` — the one-restart attempt was durably persisted before the restart thread starts.
- `CTL_RESTART_SENT` / `CTL_RESTART_ACK` — actual composer restart command request/ack.
- `HELPER_COMPOSER_NEW_PID` — replacement composer observed.
- `HELPER_SF_NEW_PID` — replacement SurfaceFlinger observation.
- `HELPER_ZYGOTE_NEW_PID` — replacement zygote observed.
- `HELPER_SYSTEM_SERVER_NEW_PID` — replacement system_server observed.
- `HELPER_PACKAGE_SERVICE_FOUND` / `HELPER_SETTINGS_SERVICE_FOUND` — framework services observed in the replacement system_server.
- `HELPER_PM_READY` — package path is usable before successor-daemon launch.
- `CPU_ATTEMPT_APPLIED` — CPU restart provenance completed successfully.
- `GATE_*`, `GATE_STABLE_SAMPLE`, `GATE_GRACE_*`, `RECONCILE_DISPLAY` — existing display safety.
- `BOOT_READY` — display reconciliation has completed and BootSafety is released.

The existing one-restart provenance remains authoritative. Splash code must never write the CPU property, request a composer restart, mark a CPU attempt APPLIED/FAILED, release BootSafety, alter the display gate, or make boot completion conditional on splash success.

## Successor SurfaceFlinger: hardened evidence requirement

Physical Phase 3A tracing exposed an important handoff detail:

```text
baseline SurfaceFlinger = 2301
pidof surfaceflinger    = "2301 7081"
```

During the handoff, the raw observation can temporarily contain both old and new PIDs. Therefore:

> The raw `pidof` string is not itself proof of a successor.

The instrumentation branch now includes pure `SuccessorPidModel` coverage. Given the captured baseline PID, the runtime integration must:

- treat baseline-only as still waiting;
- resolve `baseline + exactly one non-baseline PID` to that successor;
- resolve a new-only sample to that successor;
- reject malformed/zero PID samples;
- treat more than one distinct non-baseline PID as ambiguous and fail open for splash presentation.

The known physical sample `2301 7081` must resolve to exactly `7081`.

The first runtime integration should use this exact-successor rule for SurfaceFlinger before emitting the authoritative successor event used by splash gating. It may also require a subsequent stability sample if implementation testing shows that necessary, but it must never use the raw multi-PID string as the splash show gate.

## First-prototype lifecycle contract

The first prototype is deliberately narrower than a generic framework-recovery overlay.

### Eligibility / arming

`SPLASH_ARMED` means only:

- research splash feature enabled;
- boot-scoped CPU path;
- durable CPU phase already `RESTART_REQUESTED`.

It does **not** mean drawing is safe yet.

### Safe SHOW gate

Do not request rendering until both are proven:

1. replacement composer is identified relative to its baseline; and
2. exactly one replacement SurfaceFlinger PID is identified relative to its baseline.

Then:

```text
SPLASH_SHOW_REQUESTED
SPLASH_SHOWN
```

`SPLASH_SHOWN` requires positive renderer/layer evidence. A command returning success is not sufficient by itself.

### Primary REMOVE gate

For the first prototype, normal removal is only:

```text
successor SurfaceFlinger proven
    -> observe service.bootanim.exit == 0
    -> later observe service.bootanim.exit == 1
    -> SPLASH_BOOTANIM_EXIT
    -> SPLASH_REMOVE_REQUESTED reason=BOOTANIM_EXIT
```

A bare `service.bootanim.exit == 1` is not valid because it may be stale from the first boot.

The model method is intentionally named `bootAnimationExitEdge(...)` so the runtime caller must provide evidence for the post-successor `0 -> 1` transition rather than a vague "framework recovered" guess.

### HOME / launcher

HOME visibility is **telemetry only** in the first prototype.

Record it to compare timing with the physical return of Android, but do not use it to remove the splash yet. Avoid repeated expensive `dumpsys` calls in the critical helper path.

### BOOT_READY

`BOOT_READY` is not the intended normal removal trigger. It remains an emergency belt-and-suspenders cleanup opportunity only.

The splash should normally disappear as soon as Android has useful UI to present, while the existing app-specific display gate may continue to `BOOT_READY` later.

## Proposed splash trace schema

Every event keeps the existing `BootTrace` common fields: `elapsed_ms`, `action`, `pid`, `boot_id`, and `source`.

| Action | Exact meaning | Recommended detail |
| --- | --- | --- |
| `SPLASH_ARMED` | Prototype is eligible for this boot-scoped restart attempt. | `attempt_phase=RESTART_REQUESTED` |
| `SPLASH_WAIT_SF` | Waiting for exact replacement SurfaceFlinger. | `old_sf=<pid>;timeout_ms=<policy>` |
| `SPLASH_SHOW_REQUESTED` | Renderer/layer show operation was issued after exact successor composer + SF proof. | `sf_pid=<pid>` |
| `SPLASH_SHOWN` | Positive evidence splash is visible/committed. | `sf_pid=<pid>;show_ms=<delta>` |
| `SPLASH_BOOTANIM_EXIT` | Post-successor `service.bootanim.exit` transition `0 -> 1` observed. | `from=0;to=1` |
| `SPLASH_HOME_VISIBLE` | Candidate HOME/launcher visibility telemetry. | `value=0|1`; never controls first prototype |
| `SPLASH_REMOVE_REQUESTED` | Cleanup issued. | `reason=BOOTANIM_EXIT|VISIBLE_TIMEOUT|BOOT_READY|FAILURE` |
| `SPLASH_REMOVED` | Positive evidence branded layer/window no longer exists. | `visible_ms=<delta>;reason=<same reason>` |
| `SPLASH_TIMEOUT` | A splash-only deadline expired. | `phase=WAIT_SF|SHOW|VISIBLE|REMOVE;age_ms=<delta>` |
| `SPLASH_FAIL_OPEN` | Splash abandoned without affecting boot/CPU/display recovery. | `reason=<token>;cleanup_requested=0|1` |

Semantic requirements:

- `SPLASH_SHOW_REQUESTED != SPLASH_SHOWN`
- `SPLASH_REMOVE_REQUESTED != SPLASH_REMOVED`
- splash traces never feed CPU-attempt interpretation;
- splash state is runtime-only and never persisted.

## Prototype timeout policy

The pure model keeps policy injected; these are **starting prototype values**, not release constants:

| Deadline | Starting value |
| --- | ---: |
| successor-SF / renderer launch budget | 1500 ms |
| renderer launch -> positive SHOWN ack | 750 ms |
| maximum visible lifetime | 6000 ms |
| remove request -> force-exit grace | 500 ms |
| renderer independent self-destruct TTL | 8000 ms |
| cheap `service.bootanim.exit` polling | 250 ms |
| HOME telemetry sampling, if enabled | 500-1000 ms |

The 6 s supervisor limit and 8 s renderer TTL are independent safeguards. Physical Thor traces remain authoritative before any production/default policy is promoted.

Do not add a minimum splash duration. If Android becomes useful quickly, remove the splash quickly.

## User-visible timing measurements

Use the boot-relative `elapsed_ms` domain already shared by daemon/helper traces.

- `T0`: `CTL_RESTART_SENT` — perceived-recovery timing anchor.
- durable restart authority: `CPU_ATTEMPT_RESTART_REQUESTED` — separate provenance anchor.
- successor SF: exact resolved replacement PID, not raw `pidof`.
- splash visible: `SPLASH_SHOWN`.
- normal framework visual-return proxy: `SPLASH_BOOTANIM_EXIT`.
- splash removed: `SPLASH_REMOVED`.
- final app/display readiness: `BOOT_READY`.

Useful deltas:

- black-before-splash = `SPLASH_SHOWN - CTL_RESTART_SENT`
- SF-to-splash = `SPLASH_SHOWN - exact successor SF`
- splash-visible = `SPLASH_REMOVED - SPLASH_SHOWN`
- restart-to-bootanim-exit = `SPLASH_BOOTANIM_EXIT - CTL_RESTART_SENT`
- bootanim-exit-to-boot-ready = `BOOT_READY - SPLASH_BOOTANIM_EXIT`

Also correlate telemetry-only `SPLASH_HOME_VISIBLE`, new system_server, package/settings readiness, and launcher resume timing against physical video/observation.

## Host-test coverage

`RecoverySplashModel` remains pure: no Android classes, subprocesses, property writes or display APIs.

Covered by `RecoverySplashModelTest`:

- feature flag / boot scope / restart-request arming gate;
- normal lifecycle;
- normal removal specifically through `bootAnimationExitEdge(...)`;
- SF wait timeout;
- show acknowledgement timeout;
- maximum visible lifetime;
- removal timeout;
- explicit show/remove failure;
- fail-open behavior;
- visible timeout requests cleanup before fail-open;
- CPU restart independence.

`SuccessorPidModelTest` covers:

- baseline-only -> waiting;
- physical-style `old new` sample -> exact new PID;
- new-only -> exact successor;
- duplicate successor token -> still one successor;
- two distinct non-baseline PIDs -> ambiguous;
- empty observation -> waiting;
- malformed/zero PID -> invalid;
- invalid baseline -> invalid.

Static/source guards must keep both pure models free of restart/process/display side effects.

## Runtime ownership contract from Workstream B

The agreed architecture is:

```text
existing helper
    |
    | proves exact successor composer + exact successor SurfaceFlinger
    |
    +--> launches dedicated bounded recovery-splash child
             |
             +-- owns SurfaceControl layer
             +-- owns splash lifecycle/timers
             +-- watches post-successor bootanim.exit 0 -> 1
             +-- owns 6 s visible hard-removal policy
             +-- owns 8 s absolute self-destruct TTL
             +-- removes/exits independently

existing helper continues the already-proven recovery handoff unchanged
```

Refinement: the helper owns orchestration only. Do not turn the sensitive shell handoff into a second framework. Prefer the dedicated renderer child to own the detailed splash state machine, layer, polling and self-destruction.

The successor daemon may perform best-effort emergency cleanup, but it does not own normal splash timing and splash success never gates the daemon.

## Manual Thor checklist

Run with a signed prototype candidate only; no release.

### BOTH cold boot

- [ ] First vendor boot animation appears normally.
- [ ] Second vendor boot animation remains suppressed.
- [ ] Exactly one `CPU_ATTEMPT_RESTART_REQUESTED`.
- [ ] Exactly one `CTL_RESTART_SENT`.
- [ ] Exact replacement composer identified.
- [ ] Exact replacement SurfaceFlinger identified even if raw observation briefly contains old + new.
- [ ] `SPLASH_SHOW_REQUESTED` only after both exact successor proofs.
- [ ] `SPLASH_SHOWN` positively confirmed.
- [ ] Post-successor `service.bootanim.exit` is observed at 0 before 1.
- [ ] `SPLASH_BOOTANIM_EXIT` then `SPLASH_REMOVE_REQUESTED reason=BOOTANIM_EXIT`.
- [ ] `SPLASH_REMOVED`.
- [ ] `BOOT_READY` later completes normally.
- [ ] No overlay remains on home/unlock.
- [ ] HOME visibility telemetry does not control lifecycle.

### TOP cold boot

- [ ] Same restart/splash checks as BOTH.
- [ ] `BOTTOM_OFF_CONFIRMED` still occurs only through the existing display gate/reconciliation.
- [ ] Bottom display remains truly off after boot.
- [ ] Splash never leaks onto or changes the lower panel.

### Reboot twice

For each reboot independently:

- [ ] new kernel `boot_id`;
- [ ] exactly one restart request;
- [ ] exact successor resolution;
- [ ] splash appears and removes;
- [ ] no stale overlay/layer/window survives;
- [ ] no same-boot CPU restart retry.

## Physical-only validation

Host tests cannot prove:

- actual SurfaceControl visibility on Thor;
- first committed frame / residual black interval;
- z-order;
- correct top-display targeting in BOTH/TOP;
- renderer/layer cleanup after real process death or SurfaceFlinger churn;
- no persistent overlay;
- exact `bootanim.exit 0 -> 1` alignment with perceived Android visual return;
- production timeout values;
- absence of flash at splash removal.

These remain physical acceptance criteria.

## Stop conditions

Stop the splash prototype and retain suppression + black interval if any of these occur:

- a second `CTL_RESTART_SENT` in the same boot;
- another unexpected composer/SF/zygote/system_server cycle caused by splash work;
- CPU provenance outcome changes;
- BootSafety/display-gate timing or ordering changes;
- splash delays framework recovery or `BOOT_READY`;
- splash appears before exact successor SF proof;
- raw multi-PID `pidof` output is accepted as the successor without resolution;
- wrong panel/lower-panel behavior changes;
- a layer persists after recovery or beyond renderer TTL;
- normal cleanup depends on system_server code being restarted;
- `debug.sf.nobootanimation` restoration semantics change;
- `bootanim.exit` does not provide a reliable post-successor `0 -> 1` edge.

Priority remains:

```text
CPU restart correctness
    > BootSafety / display correctness
    > framework recovery
    > splash UX
```
