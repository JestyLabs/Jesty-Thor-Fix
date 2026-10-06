# Recovery splash integration prototype

Status: **physical Thor test only**. This branch is stacked on the Phase 3A
second-boot-animation suppression branch. It does not change the app version,
release channel, CPU restart provenance model, display readiness gate, stable
CRTC requirements, or post-composer grace.

## What is proven

The existing Thor work has already established that the boot-scoped CPU-fix
restart is provenance-bound and that the composer restart cascades through
SurfaceFlinger, zygote and system_server. Phase 3A also has device evidence that
the second vendor boot animation can be suppressed.

The repo already uses hidden SurfaceControl APIs from the privileged
`app_process` daemon for display power operations. The transition helper also
survives the framework restart and observes the replacement SurfaceFlinger PID.

## What is still a hypothesis

The new Java SurfaceControl layer has **not** been proven on physical Thor
hardware. In particular, the following require device validation:

- whether the replacement SurfaceFlinger accepts the Builder/Transaction calls
  as soon as its new PID is visible;
- whether the default layer stack maps to the Thor top display at that point;
- whether the active-mode dimensions reported through SurfaceControl are
  already stable;
- whether the existing lockup PNG is present under the rebuilt APK resource
  path used by the runtime Zip lookup;
- whether package/settings service readiness is the best visual removal point.

A failure in any of those items is a splash failure only. It must not become a
CPU-fix, boot, or display-gate failure.

## Feature flag

The prototype is disabled unless an ADB shell user creates this exact empty,
regular, shell-owned marker:

```sh
adb shell 'rm -f /data/local/tmp/jesty-thor-recovery-splash.flag; : > /data/local/tmp/jesty-thor-recovery-splash.flag'
```

The daemon never creates the marker. Removing it rolls the next boot back to
the Phase 3A behavior without reinstalling:

```sh
adb shell rm -f /data/local/tmp/jesty-thor-recovery-splash.flag
```

The gate also requires a boot-scoped restart, successful
`debug.sf.nobootanimation` arming, and a safe current APK CLASSPATH. If any
prerequisite is false, the trace records `RECOVERY_SPLASH_SKIPPED` and the
existing boot path continues.

## Lifecycle

```text
CPU attempt RESTART_REQUESTED already durable
  -> bootanimation suppression armed
  -> recovery-splash feature gate ARMED
  -> existing helper starts
  -> existing ctl.restart composer
  -> helper observes replacement SurfaceFlinger PID
  -> helper launches standalone RecoverySplashProcess
  -> renderer creates a direct SurfaceControl layer and draws
       existing jesty_thor_header_lockup.png
       + "Restoring display..."
  -> helper observes package + settings services
  -> helper sends SIGTERM only to the captured/identity-checked splash PID
  -> if that signal never arrives or fails, renderer self-removes after 8 s
  -> existing helper floor, successor daemon, display gate and reconciliation continue
```

### Arm point

Immediately after successful bootanimation-suppression arming and before the
existing helper is scheduled. Arming is UX state only; it is not persisted into
the CPU attempt store and cannot authorize a restart.

### Show point

Only after the existing helper observes a replacement SurfaceFlinger PID. The
renderer retries layer creation for a bounded 1.8 s because PID visibility is
not proof that the SurfaceFlinger binder endpoint is ready.

### Removal point

First choice: both the existing `package` and `settings` service checks have
succeeded. The helper also issues a second best-effort removal after its broader
framework check.

### Timeout fallback

The renderer has its own 8 s timeout, capped to 10 s even if launched with a
different argument. Surface creation failure exits immediately. The helper
never waits for the renderer and never treats its exit status as a boot result.

## Asset strategy

The first physical candidate reuses
`apk/res/drawable-nodpi/jesty_thor_header_lockup.png`. No new binary asset is
introduced. The renderer searches its own APK for that filename; if decoding
fails it falls back to the full app name, `JESTY THOR FIX`, plus the recovery
message.

## Why no Activity

An Activity is coupled to ActivityManager/WindowManager and the system_server
lifecycle. That is exactly the framework domain being recreated during the
composer handoff. Starting an Activity would therefore add a dependency on the
component that is temporarily unavailable and could be torn down during the
gap.

The prototype instead launches a separate root `app_process` from the already
surviving helper after the new SurfaceFlinger is observed. It talks directly to
SurfaceFlinger through hidden SurfaceControl APIs and has no Activity,
WindowManager, Service or manifest entry.

## Instrumentation

Expected trace sequence when enabled:

```text
BOOTANIM_SUPPRESS_ARMED
RECOVERY_SPLASH_ARMED
HELPER_SCHEDULED
CTL_RESTART_SENT
HELPER_COMPOSER_NEW_PID
HELPER_SF_NEW_PID
RECOVERY_SPLASH_START_REQUESTED
RECOVERY_SPLASH_PROCESS_START
RECOVERY_SPLASH_SHOWN
...
HELPER_SYSTEM_SERVER_NEW_PID
HELPER_PACKAGE_SERVICE_FOUND
HELPER_SETTINGS_SERVICE_FOUND
RECOVERY_SPLASH_REMOVE_SENT
...
CPU_ATTEMPT_APPLIED
...
BOOT_READY
```

If rendering cannot be created, expect `RECOVERY_SPLASH_CREATE_FAILED`.
If removal signaling is lost, expect `RECOVERY_SPLASH_TIMEOUT` followed by
`RECOVERY_SPLASH_RELEASED`.

## Physical Thor test

1. Build/sign the draft PR candidate using the existing signed test-candidate
   path. Do not publish a release.
2. Install it over the existing test build.
3. Enable the marker above and verify it is an empty shell-owned regular file.
4. Cold boot in **BOTH** first. Confirm the first vendor animation is unchanged,
   the second vendor animation remains suppressed, the branded splash appears
   only in the recovery gap, and normal UI follows.
5. Capture the boot trace and daemon log. Confirm one composer replacement and
   the expected ordering above.
6. Confirm the CPU attempt reaches `APPLIED` and the successor daemon reaches
   `BOOT_READY`.
7. Repeat in **TOP**. Confirm bottom-off still occurs only through the existing
   display reconciliation path and sleep/wake repair remains correct.
8. Remove the marker and cold boot again. Confirm the splash path is completely
   absent and Phase 3A behavior is restored.

Useful collection:

```sh
adb shell cat /data/local/tmp/jesty-thor-boot-trace.log
adb shell cat /data/local/tmp/td032.log
adb shell ls -ln /data/local/tmp/jesty-thor-recovery-splash.flag
```

## Stop conditions

Stop physical testing and do not merge if any of these occur:

- the first cold-boot animation is hidden or shortened;
- more than one composer restart occurs;
- `RESTART_REQUESTED -> APPLIED` provenance changes or becomes ambiguous;
- the splash appears without the feature marker or during a runtime CPU toggle;
- the splash starts before replacement SurfaceFlinger is observed;
- the splash targets the wrong panel, wrong orientation, wrong dimensions, or
  remains visible into usable UI;
- the splash survives its 8 s timeout;
- helper failure, wake-lock behavior, display-gate timing, stable CRTC sampling,
  post-composer grace, TOP bottom-off ordering, or wake repair regresses;
- CI/static guards fail.

## Rollback

Fast runtime rollback: remove the feature marker and reboot.

Code rollback: use the Phase 3A branch/candidate directly. This integration is
stacked on that branch and contains no version bump or release change, so no
main/release revert is required for the prototype.
