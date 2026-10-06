# Branded recovery splash — coordination plan

Status: research / prototype only. No release, no version bump, no main-branch behavior change, and no merge to production behavior before physical Thor validation.

## Goal

Replace the visible black interval exposed by the physically validated second-boot-animation suppression with a short branded recovery splash, while leaving the proven CPU restart, provenance, BootSafety, display gate, reconciliation ordering and fail-safe behavior unchanged.

Target user experience:

```text
first vendor boot animation
    -> brief transition while successor SurfaceFlinger starts
    -> branded Jesty Thor Fix recovery splash
       "Finishing startup..."
       "This is expected during startup."
    -> Android/home
```

The splash is disposable UX. CPU/display correctness always wins.

## Physically proven baseline

On Thor hardware:

- first vendor boot animation remains normal;
- second vendor boot animation can be suppressed;
- suppression exposes an approximately four-second visible black interval;
- the CPU-fix restart still occurs exactly once;
- replacement composer, SurfaceFlinger, zygote and system_server appear;
- the boot-scoped CPU attempt still reaches APPLIED;
- the existing post-composer display gate and 5-second grace remain intact;
- BOOT_READY completes normally.

Important handoff observation:

```text
baseline SurfaceFlinger = 2301
transitional pidof      = "2301 7081"
```

Therefore raw multi-PID `pidof` output is not valid successor proof. The splash may show only after an exact non-baseline successor is resolved.

## Workstream status

| Workstream | Purpose | Status |
| --- | --- | --- |
| A | Runtime renderer / SurfaceControl feasibility | ACTIVE |
| B | Lifecycle, ownership, gating, removal | DESIGN ACCEPTED |
| C | Visual asset and copy | DESIGN ACCEPTED |
| D | Instrumentation and host-test coverage | IMPLEMENTED ON DRAFT PR #16; CI GREEN BEFORE latest refinements |
| E | Final integration | MUST WAIT FOR A mechanism; may plan/review only |

## B — accepted lifecycle/ownership design

### Ownership

Preferred split:

```text
existing restart helper
    |
    | proves replacement composer
    | proves exact replacement SurfaceFlinger
    |
    +--> launches one dedicated bounded recovery-splash child
             |
             +-- owns renderer/layer
             +-- owns splash lifecycle/timers
             +-- observes post-successor bootanim.exit
             +-- removes itself
             +-- owns absolute self-destruct TTL

existing helper continues the already-proven handoff
successor daemon continues the already-proven recovery/display path
```

The helper owns orchestration only. Do not turn the sensitive shell handoff into a mini-framework.

Splash state is runtime-only and must never be persisted.

### Eligibility

`SPLASH_ARMED` means only:

- research feature enabled;
- boot-scoped CPU path;
- durable CPU attempt already at `RESTART_REQUESTED`.

It does not mean rendering is safe.

### Safe show condition

Show only after:

1. replacement composer is proven relative to baseline; and
2. exactly one replacement SurfaceFlinger is proven relative to baseline.

Do not use raw `pidof` text as proof.

The first runtime implementation should use the pure `SuccessorPidModel` semantics:

- baseline only -> wait;
- baseline + one new PID -> resolve that PID;
- new-only -> resolve that PID;
- malformed/zero -> invalid;
- more than one distinct non-baseline PID -> ambiguous -> splash fail-open.

If implementation testing shows that a stability sample is required, add it without weakening this rule.

### Normal removal

For the first prototype, normal removal uses one signal only:

```text
successor SF proven
    -> service.bootanim.exit observed at 0
    -> later service.bootanim.exit observed at 1
    -> SPLASH_BOOTANIM_EXIT
    -> remove splash
```

A bare `service.bootanim.exit == 1` is invalid because it can be stale from the first boot.

HOME/launcher visibility is telemetry only during the first prototype.

`BOOT_READY` is emergency cleanup only, not the intended normal removal trigger.

### Starting safety timeouts

Prototype starting values:

- successor-SF / renderer-launch budget: 1500 ms;
- show acknowledgement: 750 ms;
- maximum visible lifetime: 6000 ms;
- removal grace: 500 ms;
- independent renderer self-destruct TTL: 8000 ms;
- cheap bootanim property polling: 250 ms;
- HOME telemetry, if collected: 500-1000 ms.

These are research starting values. Physical traces decide any later production values.

No minimum splash duration.

### Fail-open

Every splash failure must:

1. log the failure;
2. remove anything already created;
3. continue the existing recovery path unchanged.

Splash logic must never:

- call `ctl.restart`;
- alter `CpuBootAttemptModel` or `CpuBootAttemptStore`;
- write the CPU-fix property;
- release BootSafety;
- release the CPU transition wake lock;
- call `DisplayActionCoordinator`;
- change panel power/CRTC state;
- enable Wake Guard;
- unblock display reconciliation;
- decide CPU APPLIED/FAILED;
- gate BOOT_READY.

## C — accepted visual spec

Primary prototype asset:

- static 1920x1080 flattened PNG;
- near-black/dark background;
- compact full **Jesty Thor Fix** lockup;
- no animation;
- no progress bar;
- no runtime font dependency.

Copy:

```text
Jesty Thor Fix

Finishing startup...

This is expected during startup.
```

Fallback hierarchy:

1. primary branded flattened PNG;
2. ultra-simple/monochrome static variant if needed;
3. existing black interval if rendering fails.

Never escalate a cosmetic failure into boot/recovery risk.

## D — instrumentation/test contract

Canonical lifecycle traces:

- `SPLASH_ARMED`
- `SPLASH_WAIT_SF`
- `SPLASH_SHOW_REQUESTED`
- `SPLASH_SHOWN`
- `SPLASH_BOOTANIM_EXIT`
- `SPLASH_HOME_VISIBLE` — telemetry only initially
- `SPLASH_REMOVE_REQUESTED`
- `SPLASH_REMOVED`
- `SPLASH_TIMEOUT`
- `SPLASH_FAIL_OPEN`

Semantics:

- `SPLASH_SHOW_REQUESTED != SPLASH_SHOWN`
- `SPLASH_REMOVE_REQUESTED != SPLASH_REMOVED`

UX timing anchor:

`T0 = CTL_RESTART_SENT`

Do not use `CPU_ATTEMPT_RESTART_REQUESTED` as perceived-black-time T0; it remains the durable provenance boundary.

Pure host-test models:

- `RecoverySplashModel`;
- `SuccessorPidModel`.

The models must stay free of Android/process/display/restart side effects.

## A — runtime renderer acceptance criteria

A is the current technical blocker.

It must establish, with evidence rather than assumption:

1. a renderer can be launched after exact successor SurfaceFlinger exists;
2. it can create a top-display-only visible surface/layer without normal Activity/WindowManager lifecycle;
3. `SPLASH_SHOWN` can be positively acknowledged;
4. it can remove the surface deterministically;
5. renderer death removes/reclaims the layer, or another bounded cleanup path exists;
6. the renderer can enforce its own absolute TTL;
7. it does not alter compositor restart behavior, CPU provenance, panel state, display gate or recovery timing materially.

Preferred implementation shape is one bounded `app_process` child using the smallest viable SurfaceControl-backed mechanism, if Workstream A proves that path.

If A disproves that mechanism, do not force E to implement it; update this plan with the evidence and choose the next smallest safe backend.

## E — integration instructions

E should not independently invent renderer behavior while A is unresolved.

Until A reports a viable backend, E may only:

- consume B/C/D contracts;
- inspect current restart/helper insertion points;
- prepare a minimal integration diff plan;
- identify conflicts between A/B/C/D;
- keep feature gating research-only;
- keep current helper/restart/display code unchanged.

Once A proves a backend, E should:

1. rebase/stack on the current Phase 3A suppression candidate plus the accepted D instrumentation/model work;
2. harden exact successor SurfaceFlinger resolution first;
3. wire research-only eligibility after durable `RESTART_REQUESTED`;
4. launch the bounded renderer only after exact successor composer + SF proof;
5. pass/use the agreed static asset;
6. observe post-successor `service.bootanim.exit 0 -> 1`;
7. remove the renderer on that edge;
8. enforce 6 s visible removal and 8 s independent TTL;
9. record all D traces;
10. preserve the existing helper handoff and successor-daemon/display-gate path;
11. produce a signed test candidate only after host CI is green;
12. do not merge/release before physical BOTH then TOP validation.

## Physical validation order

### Stage 1 — BOTH, single cold boot

Require:

- first vendor animation normal;
- no second vendor animation;
- exactly one `CPU_ATTEMPT_RESTART_REQUESTED`;
- exactly one `CTL_RESTART_SENT`;
- exact replacement composer/SF;
- splash shown only after successor SF;
- splash visually covers most of the old black interval;
- post-successor bootanim `0 -> 1` observed;
- splash removed;
- no overlay on recovered home;
- CPU attempt APPLIED;
- existing display gate/grace/BOOT_READY unchanged.

### Stage 2 — repeat BOTH

Prove repeatability and no stale layer/state.

### Stage 3 — TOP

In addition:

- splash targets top display only;
- bottom panel is never activated/changed by splash;
- `BOTTOM_OFF_CONFIRMED` retains existing ordering;
- sleep/wake remains normal after boot.

### Stage 4 — regression remainder

Run broader regression later without changing already-proven safety semantics.

## Stop conditions

Stop and revert to the known suppression + black interval behavior if:

- second compositor restart appears;
- extra SF/zygote/system_server cycle is caused by splash;
- CPU provenance changes;
- `debug.sf.nobootanimation` restore semantics change;
- BootSafety becomes ready earlier;
- display reconciliation moves before its existing gate;
- display grace is shortened/removed;
- wrong panel or lower panel is affected;
- green flash/stuck-black behavior appears;
- splash survives its TTL;
- splash remains over recovered Android;
- renderer materially delays framework recovery;
- exact successor SF cannot be established;
- normal cleanup depends on system_server surviving the restart;
- bootanim `0 -> 1` does not correlate reliably with visual recovery.

## Priority

```text
CPU restart correctness
    > BootSafety / display correctness
    > framework recovery
    > splash UX
```

This priority is non-negotiable during prototype work.
