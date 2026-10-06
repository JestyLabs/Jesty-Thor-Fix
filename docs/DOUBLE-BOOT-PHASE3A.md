# Phase 3A research — eliminate the visible second boot animation

Status: research branch. No release/version bump. No production behavior change is implied by this document.

## Goal

Keep the already validated boot-scoped CPU-fix restart/provenance/display-gate behavior while removing, or at least minimizing, the **second visible AYN/Android boot animation** caused by the compositor restart.

The validated v1.5.20/early-CPU path is currently:

```text
normal cold boot
  -> first boot animation finishes
  -> CPU-only gate
  -> vendor.display.disable_system_load_check = 1
  -> ctl.restart vendor.qti.hardware.display.composer
  -> vendor composer restarts
  -> SurfaceFlinger restarts
  -> zygote/system_server restart
  -> second boot animation is shown
  -> successor daemon proves new composer PID
  -> full display gate + 5 s post-composer grace
  -> display reconciliation
```

The first target is deliberately narrower than "remove the restart":

```text
keep the known-safe restart
  -> suppress only the second boot animation
  -> preserve all provenance, BootSafety and display-gate invariants
```

## What the Thor trace already proves

The physical Phase 2 trace shows that one `ctl.restart` of
`vendor.qti.hardware.display.composer` cascades through the graphics/framework stack:

- composer gets a new PID;
- SurfaceFlinger gets a new PID;
- zygote gets a new PID;
- system_server gets a new PID.

The existing helper already records those transitions as
`HELPER_COMPOSER_NEW_PID`, `HELPER_SF_NEW_PID`,
`HELPER_ZYGOTE_NEW_PID` and `HELPER_SYSTEM_SERVER_NEW_PID`.

Therefore the visible second animation does not require a full kernel reboot. It is a
userspace/framework recovery effect after SurfaceFlinger is recreated.

## AOSP Android 13 behavior

Android 13 SurfaceFlinger creates `StartPropertySetThread` during initialization.
That thread performs:

```text
service.bootanim.exit = 0
service.bootanim.progress = 0
ctl.start bootanim
```

References:

- Android 13 SurfaceFlinger tree:
  https://android.googlesource.com/platform/frameworks/native/+/refs/heads/android13-release/services/surfaceflinger/
- Android 13 `StartPropertySetThread.cpp`:
  https://android.googlesource.com/platform/frameworks/native/+/refs/heads/android13-s2-release/services/surfaceflinger/StartPropertySetThread.cpp

Android's bootanimation binary has also long supported
`debug.sf.nobootanimation`: when the value is greater than zero,
`bootAnimationDisabled()` returns true and the animation object is not started.

Reference:
https://android.googlesource.com/platform/frameworks/base/+/d22b37f5fb77/cmds/bootanimation/BootAnimationUtil.cpp

A later AOSP optimization explicitly documents that
`debug.sf.nobootanimation` causes bootanim to exit early, and moved the check into
SurfaceFlinger as well to avoid even launching the process. That newer SurfaceFlinger-side
optimization must **not** be assumed to exist on the Thor's Android 13 build.

Reference:
https://android.googlesource.com/platform/frameworks/native/+/9bc251964d

## Working hypothesis

The cleanest first experiment is a **transient, boot-scoped**
`debug.sf.nobootanimation=1` around the already-required composer restart:

```text
first boot animation already finished
  -> arm debug.sf.nobootanimation=1
  -> ctl.restart composer
  -> new SurfaceFlinger still asks init to start bootanim
  -> bootanimation process observes nobootanimation=1 and exits without drawing
  -> framework recovery continues
  -> restore the previous debug.sf.nobootanimation value
  -> successor daemon / existing display gate continue unchanged
```

This is preferable to immediately racing `service.bootanim.exit=1` because it uses an
existing bootanimation opt-out before the animation object is created.

## Safety constraints for the experiment

1. Never suppress the **first** cold-boot animation.
   The suppression may only be armed after the CPU-only boot gate has already reached the
   boot-scoped restart path.
2. Never weaken or shorten the full display gate, stable CRTC samples or 10 s / 5 s grace.
3. Never change the one-restart provenance model.
4. Restrict the first experiment to a boot-coordinator restart; runtime CPU-fix toggles keep
   current behavior.
5. Save and restore the previous `debug.sf.nobootanimation` value.
6. Treat suppression failure as a UX-only failure: fall back to the existing visible second
   animation rather than failing the CPU fix.
7. Trace arm/restore/failure events so a physical test can prove what happened.
8. Do not use `debug.sf.boot_animation` as the primary mechanism: that SurfaceFlinger-side
   switch was added after Android 13 and may not exist in the Thor firmware.

## Phase 3A test ladder

### A. Read-only/device confirmation

Collect:

```text
ro.build.fingerprint
debug.sf.nobootanimation
init.svc.bootanim
service.bootanim.exit
service.bootanim.progress
bootanimation binary strings
SurfaceFlinger/BootAnimation/init logcat around the restart
```

Use `scripts/capture-double-boot.ps1`.

### B. Transient nobootanimation experiment

On a signed research candidate:

```text
cold boot BOTH
  -> one composer restart
  -> no second visible animation
  -> new composer/SF/zygote/system_server PIDs
  -> APPLIED
  -> unchanged post-restart display gate
  -> BOOT_READY
```

Then repeat in TOP and confirm `BOTTOM_OFF_CONFIRMED` still occurs only after display
reconciliation.

### C. Fallback only if needed

If the Thor bootanimation binary ignores `debug.sf.nobootanimation`, investigate a bounded
post-SurfaceFlinger strategy:

```text
new SurfaceFlinger detected
  -> observe init.svc.bootanim
  -> service.bootanim.exit=1 and/or ctl.stop bootanim
```

This is second choice because it is a race after bootanim has already been launched and may
permit a visible frame.

## Phase 3B after Phase 3A

Only after the visible-animation path is understood:

- composer-only reinitialization;
- ResourceImpl lifetime/recreation;
- vendor binder/API surface;
- early property injection;
- firmware/init-level fix.

Direct memory patching of the cached ResourceImpl boolean remains a research-only last resort
because it is BuildId/offset dependent and may bypass related initialization state.

## Phase 3C

Only after restart/suppression research:

- LOCKED_BOOT_COMPLETED;
- Direct Boot;
- earlier CPU-only prelude.

Starting the app earlier can move the restart earlier, but by itself does not explain or remove
the second boot animation.
