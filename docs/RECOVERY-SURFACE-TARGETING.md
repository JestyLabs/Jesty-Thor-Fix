# Recovery surface targeting investigation

Status: **prototype / no-reboot investigation only**

This document records why the PR #27 early-curtain design is not safe to
promote and defines the next physical probe. It does not change release/main
behavior.

## Proven from the PR #27 cold-boot test

Tested source head:

`9ec333ef4bf86349411c38f61976d120854fab72`

Observed recovery timing:

- replacement SurfaceFlinger observed: ~35.820 s
- `getPhysicalDisplayIds()` returned: ~38.828 s
- `getPhysicalDisplayIds()` duration: **2788 ms**
- early curtain committed: ~38.948 s
- `getDynamicDisplayInfo()` duration: **255 ms**
- branded layer committed: ~39.446 s
- branded layer removed on the real boot-animation exit: ~41.145 s
- branded visible interval: ~1687 ms

Therefore the early curtain did **not** cover the original black recovery gap.
The blocking call moved from a hypothesis to a measured result:
`getPhysicalDisplayIds()` itself blocks for roughly 2.8 seconds immediately
after replacement SurfaceFlinger on the tested Thor.

The branded recovery surface was also physically observed on **both panels**.
That is a separate targeting failure.

CPU restart provenance, boot-animation property restoration, surface cleanup
and normal `BOOT_READY` recovery remained healthy.

## Stable-system routing observation

After recovery, `dumpsys SurfaceFlinger` reported both physical display
entries with:

`layerFilter={layerStack=4294967295 toInternalDisplay=true}`

AOSP defines `UINT32_MAX` as `INVALID_LAYER_STACK`. This means we must not
reinterpret the dump as proof that physical display stack 0 is the correct
TOP-only target.

References:

- AOSP `libs/ui/include/ui/LayerStack.h`
- AOSP `core/java/android/view/SurfaceControl.java`
- AOSP bootanimation `BootAnimation.cpp`

The bootanimation code can mutate a display's layer stack for explicitly
selected multi-display output. Jesty Thor Fix must not borrow that behavior for
this prototype: changing a physical display layer stack is outside the splash
UX authority and is not needed for the next probe.

## Current hypothesis

A raw, unparented `SurfaceControl` is not proven to be TOP-only on the Thor.
Knowing the TOP physical ID is not enough; the PR #27 surface never used that
ID to establish a display-specific parent.

Before another cold boot, prove whether a temporary layer can be routed to one
panel using only **layer-local** state.

## Restart boundary facts

The existing restart investigation constrains which rendering approaches are
even viable during the recovery gap:

- the Qualcomm property is consumed during the composer/core resource lifetime;
- applying a changed value requires a new composer/resource lifetime on the
  tested firmware;
- the vendor composer restart cascades into SurfaceFlinger restart;
- the Thor's `surfaceflinger.rc` restarts zygote when SurfaceFlinger restarts.

Therefore an Activity, WindowManager overlay or other framework-owned window
cannot be the primary mechanism for covering the gap: the framework process
tree itself is being replaced. The recovery visual must live below the app
window stack and must tolerate the replacement SurfaceFlinger lifetime.

This also means a pre-restart SurfaceControl cannot simply be assumed to
survive the restart. Any cold-boot implementation must create its rendering
state against the replacement SurfaceFlinger and prove that its targeting
metadata is available early enough.

## No-reboot targeting probe

Branch:

`work/thor-recovery-top-only-probe`

Entry point:

`com.thor.displaypowertest.RecoverySurfaceTargetProbe`

The launcher accepts no arguments and submits one fixed worker command through
the same vendor bridge used by the existing recovery splash probe.

The worker:

1. validates that the stable physical topology is exactly the measured Thor
   TOP + BOTTOM IDs;
2. requires one DRM snapshot with **TOP CRTC = 1 and BOTTOM CRTC = 1** so a
   visually one-panel result cannot be caused merely by the other panel being
   powered off;
3. records timing/availability for read-only SurfaceControl entry points;
4. creates one temporary font-free probe surface;
5. shows three visual phases;
6. removes the surface with transaction-commit evidence;
7. verifies composer and SurfaceFlinger PIDs did not change.

### Visual phases

The phases are intentionally text-free:

| Phase | Visual | Layer-local routing |
| --- | --- | --- |
| `DEFAULT` | dark red + **1** white bar | no explicit layer-stack assignment |
| `STACK_0` | dark purple + **2** white bars | probe layer only -> stack 0 |
| `STACK_4` | dark teal + **3** white bars | probe layer only -> stack 4 |

The test is useful even if a phase appears nowhere.

For each phase record:

- TOP only
- BOTTOM only
- BOTH
- NONE

### Explicitly forbidden

The probe must not call or introduce:

- display layer-stack mutation;
- display projection mutation;
- display surface replacement;
- display size mutation;
- display power mutation;
- composer/framework restart;
- CPU attempt/provenance mutation;
- font rendering.

Host tests guard these constraints.

## Physical-test sequence

Do **not** arm the recovery-splash prototype marker and do **not** reboot.

Put the Thor in its normal **BOTH** display mode first. The probe now verifies
the measured DRM CRTCs itself and fails open with
`REQUIRES_BOTH_ACTIVE` unless both are active in the same snapshot.

After installing a signed candidate from this branch:

```powershell
adb shell rm -f /data/local/tmp/thor-recovery-splash-prototype

$composerBefore = (adb shell pidof vendor.qti.hardware.display.composer-service).Trim()
$sfBefore = (adb shell pidof surfaceflinger).Trim()

$apk = (adb shell "pm path com.thor.displaypowertest | head -1").Trim().Replace("package:","")
adb shell "CLASSPATH=$apk app_process / com.thor.displaypowertest.RecoverySurfaceTargetProbe"

Start-Sleep -Seconds 7

$composerAfter = (adb shell pidof vendor.qti.hardware.display.composer-service).Trim()
$sfAfter = (adb shell pidof surfaceflinger).Trim()

"composer before=$composerBefore after=$composerAfter"
"SF before=$sfBefore after=$sfAfter"

adb shell "grep 'source=target-probe' /data/local/tmp/jesty-thor-boot-trace.log | tail -n 100"
```

Expected launcher result:

`submitted=1`

Mandatory safety result:

- composer PID unchanged;
- SurfaceFlinger PID unchanged;
- `TARGET_PROBE_REMOVED` observed;
- no stuck probe surface.

## Decision gate

Do not make another cold-boot splash change until this probe produces evidence
for a TOP-only layer-local routing strategy.

Interpret the physical probe as follows:

| Result | Meaning | Next action |
| --- | --- | --- |
| exactly one phase is TOP-only | a layer-local discriminator exists in the stable system | isolate that mechanism in a second no-reboot probe and remove the other candidates |
| stack 0 and/or stack 4 select different physical panels | layer-stack routing is usable after framework recovery | measure whether the selected routing metadata is already valid immediately after replacement SurfaceFlinger, without calling `getPhysicalDisplayIds()` |
| every visible phase appears on BOTH | raw layer-stack assignment cannot separate the panels in the observed topology | stop layer-stack guessing and investigate a TOP-specific parent/container |
| a phase appears on NONE | the candidate stack is not consumed by either active physical display | treat it as negative evidence, not as a hidden TOP-only success |
| composer or SurfaceFlinger PID changes | probe violated the intended runtime boundary | stop; do not use the result for splash design |
| cleanup is not committed | temporary-surface lifetime is not sufficiently bounded | fix cleanup before any further physical experiment |

### If a TOP-only layer-local route exists

The next cold-gap prototype must **not** call `getPhysicalDisplayIds()` before
showing the early visual. PR #27 proved that call can block for ~2.8 seconds
during replacement-SurfaceFlinger startup.

The preferred sequence becomes:

```text
helper observes exact replacement composer + SurfaceFlinger
    -> create temporary layer using only pre-proven static Thor routing data
    -> show early recovery visual
    -> asynchronously/read-only revalidate physical topology when the APIs unblock
    -> if validation matches, continue to branded phase
    -> otherwise detach and fail open
```

The early route must be derived from a physically proven stable-system result
and must remain subordinate to exact successor-PID checks. The later topology
query is validation, not a prerequisite for first pixels.

### If no TOP-only layer-local route exists

Move to a display-specific parent/container investigation. Do not escalate to
`setDisplayLayerStack`, display projection, display-surface replacement or
display-power mutation merely to make the splash work.

Candidate approaches must still satisfy all of these:

- no dependency on Activity/WindowManager survival;
- no assumption that an old SurfaceControl survives SurfaceFlinger death;
- no physical-display configuration mutation;
- no weakening of CPU restart provenance;
- bounded cleanup and fail-open behavior.

