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
2. records timing/availability for read-only SurfaceControl entry points;
3. creates one temporary font-free probe surface;
4. shows three visual phases;
5. removes the surface with transaction-commit evidence;
6. verifies composer and SurfaceFlinger PIDs did not change.

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

If no phase is TOP-only, abandon layer-stack guessing and move to a
display-specific parent/container investigation. Do not escalate to physical
display mutation merely to make the splash work.
