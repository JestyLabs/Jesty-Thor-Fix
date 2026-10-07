# Thor mixed-refresh / "fake 120 Hz" investigation

Status: **ACTIVE RESEARCH — READ-ONLY FIRST**  
Collector/schema: **THOR_REFRESH_INVESTIGATION_V1**  
Started: 2026-10-07

## Thor baseline: 2026-10-07

**PROVEN on the validation Thor, in a user-confirmed visible BOTH session**
(`ro.build.display.id=Thor_V1.0.0.377_20260206_165408_user`):

| Layer | Upper | Lower |
|---|---:|---:|
| Android refresh policy (`min_refresh_rate` / `peak_refresh_rate`) | 60 / 60 Hz, global | 60 / 60 Hz, global |
| Active Android / SurfaceFlinger display mode | 60 Hz | 60 Hz |
| Active DRM CRTC mode | `1080x1920x60cmd`, CRTC 181 active | `1080x1240x60vid`, CRTC 243 active |
| Advertised DRM connector modes | 60 and 120 Hz | 60 and 120 Hz |

The lower connector advertises `1080x1240x120vid` as well as `60vid`. That
establishes a stock-kernel 120-mode advertisement, **not** a measured 120 Hz
panel scanout. This baseline cannot prove or disprove a logical-120 / physical-60
mismatch because the active policy and both active CRTCs were 60 Hz. No refresh
setting, display mode, property, or service was changed for this capture.

The full local evidence bundle includes `dumpsys display`, SurfaceFlinger, DRM
state, connector modes, settings and 22 copied display binaries. The host and
Thor SHA-256 values matched for all 22 binaries. Raw dumps and binaries stay
outside Git. The collector's first Windows run exposed a CRLF-to-Android-shell
parsing error; the corrected collector was rerun, and the successful visible
BOTH bundle was captured separately.

One diagnostic caution: `dumpsys display` reported an override display state
of OFF even while the user confirmed both images and DRM showed both CRTCs
active. That override field alone must not classify a capture as asleep.
The initial read-only pass did not change policy. A later, separately reviewed
120 Hz policy probe is recorded below.

### Controlled policy probe, same Thor and boot

With the user beside the Thor in visible BOTH, two short reversible probes
raised `peak_refresh_rate` from 60 to 120 before raising `min_refresh_rate` to
120. Rollback lowered `min` before `peak`. Both probes verified the original
60/60 settings afterward; the kernel boot ID and composer PID stayed the same.

The first probe was too short to measure an applied mode: SurfaceFlinger did
not receive the 120/120 policy until after the capture and settings rollback.
In the second probe, the SurfaceFlinger policy was 120/120 for about ten seconds
(logcat 12:28:39.059 to 12:28:49.107). During that interval:

- DisplayModeDirector voted for 120/120 on both displays;
- `dumpsys display` and SurfaceFlinger showed the requested 120 Hz policy but
  retained active 60 Hz mode IDs for both displays;
- DRM CRTC 181 remained `1080x1920x60cmd` and CRTC 243 remained
  `1080x1240x60vid`, both active;
- the vendor SDM dump reported `cur:60` and a 16,666,666 ns vsync period for
  both; the post-policy HWC vsync readback was also 16,666,666 ns;
- SurfaceFlinger reported `mode override by backdoor: no`;
- SurfaceFlinger logged **zero mode changes** under the 120 Hz policy.

The user reported a *possible* brief black blink but was unsure; both screens
were normal afterward. Treat this as an unconfirmed visual anomaly and stop
further refresh writes under the agreed safety rule. The probe does **not**
establish a fake physical 120 Hz mode: the active Android/HWC/DRM mode remained
60 Hz. Investigate why a valid 120 Hz policy was not enacted before any repeat
or stronger mode-setting experiment. Raw logs and dumps remain local outside
Git.

Android 13 [SurfaceFlinger reference code](https://android.googlesource.com/platform/frameworks/native/+/refs/heads/android13-qpr3-c-s2-release/services/surfaceflinger/SurfaceFlinger.cpp)
distinguishes a policy update from a completed HWC mode change, and can defer
applying policy on an inactive internal display. This is an architectural clue,
not proof that the Thor's vendor build took that branch. The current evidence
is narrowed by the binary check below. The next work is read-only: determine
why the active upper display did not issue a mode-change request.

### SurfaceFlinger binary check: no HWC mode request in the probe

**PROVEN for this exact firmware binary and the 12:28:39–12:28:49 policy
window.** The copied `/system/bin/surfaceflinger` has ELF Build ID
`a4e0851419d45662b0fd5cd067b585bf`. Its policy-change log string is used
by the routine at `0x125800`: that routine atomically exchanges the per-display
mode-change counter at object offset `+0x2a0` with zero (`0x125878–0x125884`)
and logs the old value (`0x125924`). The mode-initiation routine increments
that same counter at `0x124250–0x124258` (and on its alternate path at
`0x1243ec–0x1243f4`) **before** dispatching the HWC mode request. These
locations were checked with ARM64 disassembly of the copied binary, not inferred
solely from log wording. The matching
[AOSP `DisplayDevice` implementation](https://android.googlesource.com/platform/frameworks/native/+/refs/heads/android13-qpr3-c-s2-release/services/surfaceflinger/DisplayDevice.cpp)
also increments the counter before `setActiveModeWithConstraints`.

At policy rollback, the log reported `0 mode changes were performed under the
previous policy` for **both** displays. Thus no mode-initiation call reached
the HWC request in that 120/120 window, including failed HWC attempts counted
by this path. This moves the immediate question upstream of Qualcomm SDM;
the experiment did not test whether its 120 Hz config would succeed.

The same dump lists both physical displays as `powerMode=On` and both DRM
CRTCs as active, but marks the lower display **inactive** in SurfaceFlinger's
HWC-layer summary. These words refer to different concepts. In the cited
Android 13 source, `setDesiredDisplayModeSpecsInternal` stores the policy for
an inactive internal display and returns before applying it. That is a strong
mechanistic explanation for the lower display remaining at 60 Hz, but the
captured dump does not reveal the exact runtime branch taken on this vendor
build. The upper display is marked **active** and still made no HWC request;
its missing transition is unresolved. Do not treat `DynFPS:false` on the upper
SDM dump or `DynFPS:true` on the lower as the cause of this probe: neither
vendor mode-setting path was reached.

The same exact `surfaceflinger` binary has a pending-mode branch in
`DisplayDevice::setDesiredActiveMode` at `0x1255ac–0x125610`. If its
`mDesiredActiveModeChanged` byte at `DisplayDevice + 0x281` is already set, it
replaces the cached desired mode and returns false. The caller at `0x1af168`
only schedules a new composition when that return is true. This matches the
[AOSP pending-mode behavior](https://android.googlesource.com/platform/frameworks/native/+/refs/heads/android13-qpr3-c-s2-release/services/surfaceflinger/DisplayDevice.cpp)
and is one possible explanation for a policy being accepted without a new HWC
request. The captured dump does **not** expose that byte, so a stale pending
mode is a hypothesis, not the diagnosed cause. The upper-display gate remains
open.

### AYN framework path and the possible black blink

Read-only extraction of this Thor's `/system/framework/services.jar`
(SHA-256 `2D2CAD9BBFC1E856440E2B99BEEFACDCDF927981414D8478D0327F20BDFC0903`)
shows an AYN-specific branch in
`com.android.server.display.DisplayModeDirector.SettingsObserver`. When the
two global refresh settings change, `onChange` tracks whether both `peak` and
`min` notifications have arrived. The framework's `DisplayUtils` selects the
secondary display by nonzero logical ID and internal display type; its exact
`framework.jar` SHA-256 is
`02906B19CF5CCBA529D2023B5337E4678F03F7EFBD8974D4F2742C49C0517773`.
`updateRefreshRateSettingLockedForX6()`
then reads the lower panel's brightness and calls `onBrightnessFade`.

With both notifications present, `onBrightnessFade` sends a command through
`PServerBinder` to write **0** to
`/sys/class/backlight/panel1-backlight/brightness`, waits 50 ms, and continues
the refresh-policy update. The next callback writes `0x0` to
`/sys/class/bypass_ram_class/bypass_ram_device/bypass_ram` for a peak setting
of at least 110 Hz (otherwise `0x1`), waits another 200 ms, then animates the
lower brightness back over 200 ms. This is a framework-requested lower-panel
blank/fade surrounding policy changes; it does not require a successful HWC
mode switch. It is a concrete explanation for the user's possible brief black
blink. The captured log does not prove each privileged write succeeded, so the
visual observation remains qualified.

The decompiled code also explains why a simple `settings put` probe is not a
pure mode-set test on this firmware: it invokes lower-panel brightness and
`bypass_ram` actions in addition to changing Android's desired policy. The
probe still measured **no 120 Hz physical scanout**. Do not repeat it merely
to settle the blink without a separate risk review and a measurement that can
answer the unresolved upper-display request question.

On the final read-only check, the same boot ID remained, `min/peak` were
`60.0/60.0`, `bypass_ram` read `1`, and the Thor was asleep. No app code,
installation, or reboot was involved in this reverse-engineering pass.

This workstream investigates the Thor's mixed-refresh behavior without assuming
that an Android-visible refresh rate is the physical scanout rate of a panel.

No runtime behavior in Jesty Thor Fix is changed by this research branch.

## Question

The Thor has two unlike internal displays. Multiple Android projects report
120 Hz for the lower logical display in some states, while other projects and
Linux-side work treat the physical lower panel as a 60 Hz panel.

The question to prove is:

> When Android reports the lower Thor display at 120 Hz, what is actually
> running at 120 Hz: app/render scheduling, SurfaceFlinger/HWC mode selection,
> the DRM/DSI scanout, or the physical panel itself?

Until those layers are separated, "fake 120" is a useful hypothesis name, not
a conclusion.

## Evidence ladder

Use these labels consistently:

- **PROVEN** — measured on the validation Thor or established directly from its
  pulled firmware/binaries.
- **OBSERVED** — independently reported/implemented by another project or
  public source, useful corroboration but not our device proof.
- **HYPOTHESIS** — explanation that still needs a discriminating measurement.

## What is already proven in this project

**PROVEN**

- The Thor exposes two physical displays. The measured lower logical display is
  ID 4; its physical SurfaceControl ID and CRTC are recorded in
  `ThorHardwareProfile`.
- Physical display state can differ from the AYN logical mode: stock TOP can
  leave the lower CRTC active while True Bottom Screen Off makes it inactive.
- The device is Android 13 / Qualcomm Kalama-family hardware.
- The pulled init tree contains no explicit 60/120 refresh-rate override.
- Kalama's `init.qti.display_boot.sh` enables multiple Qualcomm display
  features but does not enable `vendor.display.enable_qsync_idle` in the
  Kalama branch captured from this Thor. Other SoC branches in the same script
  do enable it.
- `init.qcom.rc` exposes both
  `/sys/module/msm_drm/parameters/dsi_display0` and `dsi_display1` to the
  graphics group, confirming two DSI display parameters are part of the stock
  kernel integration.

Init archive used for the static pass:

`thor-init-investigation.zip`  
SHA-256:
`34BAFC187060CD2EA298C9256D7CAD010D1B608970D16ECC075FB4FD44EFED7B`

The archive contains init/scripts, not the Qualcomm display shared libraries,
so it cannot by itself answer the refresh question.


## Deep call-path narrowing

Static inspection of Android 13 SurfaceFlinger and public Qualcomm HWC/SDM code
has now narrowed the request path further. The detailed trace is in
[Thor 120 Hz mode-switch call path](THOR-120HZ-CALL-PATH.md).

The most important finding is that Android 13 SurfaceFlinger stores a refresh
policy for an inactive internal display but deliberately does not apply its mode
until that display becomes the active internal display. A second gate clears a
pending desired mode if that internal display is no longer active. This is a
strong architectural explanation for why the Thor lower display can show a
120 policy while remaining on its previous 60 Hz HWC/DRM mode.

That does **not** explain the upper panel remaining at 60 during the controlled
120/120 probe. The exact Thor SurfaceFlinger binary and its policy counters now
show that neither panel initiated an HWC mode request in that policy window.
The upper-panel question is narrower: did it cache a desired 120 mode without
scheduling a composition, or did an earlier gate stop it before that point?

Public Qualcomm HWC code also shows that SetActiveConfigWithConstraints can
queue a pending config before it is submitted to the display interface. That
boundary matters only after a future capture proves SurfaceFlinger made the HWC
request; it was not reached in this probe.

A host-only analyzer, scripts/analyze-thor-refresh-capture.ps1, now extracts
those framework/HWC/SDM/DRM markers from an existing capture bundle. It performs
no ADB or device operation.

## Independent Thor implementations

These are **OBSERVED**, not accepted as Jesty Thor Fix hardware proof.

### AYN Thor Root Toolbox

Repository:
https://github.com/jeromegsq-dev/ayn-thor-root-toolbox  
Reviewed main commit:
`705ac093905a3377a1e931165e6faf26c7956e87`

Its shortcut implementation documents the upper display as having 60.000004
and 120.00001 modes and toggles the Android ModeDirector floor/ceiling using:

`settings put system peak_refresh_rate ...`  
`settings put system min_refresh_rate ...`

The same source says the bottom has its own modes and "stayed at 120"
throughout the top-panel setting changes. That is strong evidence that the
Android-visible lower display can remain at a logical 120 while the primary
user refresh-rate vote changes.

It does **not** prove the lower panel physically scans at 120.

### ES-DE Companion / Asgard-derived Auto FPS

Repository:
https://github.com/RobZombie9043/es-de-companion  
Reviewed commit:
`8789c5723e6aa553841f5f0f67840e1c03eff472`

`RefreshRateController` writes the same
`min_refresh_rate` / `peak_refresh_rate` pair through AYN's
`PServerBinder`, and explicitly says `dumpsys display` confirms
DisplayModeDirector observes those system-scope keys.

Its implementation also preserves `min <= peak` during transitions:
peak-first when raising, min-first when lowering. The project records a
blank-display/hard-power-cycle incident during earlier testing as the reason
for this ordering. That is a useful safety warning: this investigation must
not start by writing refresh settings.

### Thor Wayfinder

Repository:
https://github.com/Thor-Wayfinder/thor-wayfinder  
Reviewed commit:
`305d3ad824e200c936fc270d044d9db3ecc800ee`

Its performance profiles and quick panel also write
`min_refresh_rate` / `peak_refresh_rate` to select 60 or 120. The mechanism
is global Android policy input, not a demonstrated per-panel physical mode
write.

### ThorTools

Repository:
https://github.com/castdrian/thortools  
Reviewed commit:
`c1b033b7ebfe6fa54a27f6131e9c4926d51a6237`

Its real diagnostics read each Android `Display.mode.refreshRate` separately.
Its host fixtures model the upper at 120 Hz and lower at 60 Hz. The fixture
shows the expected physical distinction, but a test constant is not a device
measurement.

### HuntersRecomp

Repository:
https://github.com/aabrole/HuntersRecomp  
Reviewed commit:
`7482c7013b14b18e17864521ceb1a079c172499c`

Its physical-device audit records an Android-visible setup with the main panel
at 120 Hz and the second 1080x1240 display also reported at 120 Hz. This
corroborates the logical-120 observation on another Thor.

### DroidBridge Launcher

Repository:
https://github.com/DNAMobileApplications/DroidBridgeLauncherGplayGithub  
Reviewed commit containing the compatibility class:
`fd9a9794984cc5c2116f99548d2c0b947d1f9579`

`DualScreenRefreshCompat` explicitly hard-codes the Thor lower
1240x1080 panel as **60 Hz** and overrides the refresh value for an app running
there rather than trusting the normal Android `Display.Mode`.

This is particularly relevant because it encodes the exact mismatch we are
investigating: Android mode information can be unsuitable as a physical-panel
truth source on a mixed-refresh handheld.

### KettleLinux / Gamescope

Repository:
https://github.com/kettlelinux/kettlelinux  
Reviewed patch:
`packages/gamescope/0022-DRM-offer-generated-lower-refresh-rates-for-an-EDID-.patch`

The patch identifies the Thor lower panel as a **Chipsea CH13726A, DSI video
mode**, says its DRM panel mode list contains only **60 Hz**, and adds generated
lower rates for Linux/Gamescope experimentation.

This is the strongest public clue so far about the physical lower-panel timing,
but it is Linux-side evidence. We still need the stock Android Thor's own
DRM/DSI state before calling the Android mismatch proven.

## Android / SurfaceFlinger architecture clue

Android 13 SurfaceFlinger initializes its scheduler around the active/default
physical display's refresh-rate configs. Later AOSP work made multi-display
pacesetter/follower handling more explicit.

A particularly relevant upstream fix is:

`963da1c0252dab01f65617c9ca08e66ff6596581`  
"SF: Match followers' refresh rate to pacesetter's" (2024)

The commit states that multi-display refresh-rate selection had been flawed:
selection ran per display and follower candidates were then filtered to match
the pacesetter's refresh rate.

Source:
https://android.googlesource.com/platform/frameworks/native/+/963da1c0252dab01f65617c9ca08e66ff6596581

This does **not** prove the Thor has that exact AOSP bug or patch level. It does
prove that multi-display refresh selection and pacesetter/follower coupling are
real SurfaceFlinger concerns, so the Thor's scheduler layer must be measured
instead of treating `Display.Mode` as physical truth.

## Qualcomm SDM clue

Public Qualcomm SDM implementations expose per-built-in-display refresh
configuration and `SetRefreshRate` paths, commonly gated by panel
`dynamic_fps` capability and propagated through the hardware interface.

The Thor's exact vendor SDM build is the source of truth. Public CAF code is
architecture guidance only.

Priority Thor binaries for the next static pass:

- `/vendor/bin/hw/vendor.qti.hardware.display.composer-service`
- `/vendor/lib64/libsdmcore.so`
- `/vendor/lib64/libsdmextension.so`
- `/vendor/lib64/libsdmutils.so`
- `/vendor/lib64/libdisplayconfig*.so`
- `/vendor/lib64/hw/hwcomposer*.so`
- `/system/bin/surfaceflinger`

Priority strings/symbols:

- `SetRefreshRate`, `GetRefreshRate`, `GetConfig`, `SetActiveConfig`
- `dynamic_fps`, `refresh_rate`, `fps`, `vsync`
- `qsync`, `QSyncMode`
- `DisplayBuiltIn`, `DisplayBase`, `HWDisplayAttributes`
- `active_config`, `display_attributes`
- `dsi_display0`, `dsi_display1`

The v1 collector can pull these read-only and generate SHA-256 manifests.

## Current hypotheses

### H1 — logical 120, physical 60

Android/HWC exposes a 120 Hz lower mode so a shared/pacesetter render schedule
can operate at 120, while the lower DSI/panel scanout remains 60 Hz.

Prediction:

- Android `Display.Mode` / SurfaceFlinger says lower ~=120;
- kernel/DRM timing or vblank evidence says lower ~=60.

If observed on the validation Thor, this is the cleanest proof of "fake 120".

### H2 — vendor driver advertises/sets a 120 scanout mode

The stock Android DRM/HWC stack itself exposes a 120 timing for the lower DSI
path despite the panel's nominal 60 Hz specification.

Prediction:

- Android and DRM both report ~=120.

That result would move the question deeper: verify actual vblank cadence and
panel/controller behavior before claiming a real 120 Hz physical panel.

### H3 — mixed logical modes are already correct

Top is ~=120, bottom ~=60 at Android/HWC level, and tearing comes from
multi-display present/vsync synchronization rather than a fake mode.

Prediction:

- Android exposes top ~=120 and bottom ~=60;
- DRM agrees;
- tearing remains when both are active.

Then the investigation moves to SurfaceFlinger scheduler, present fences and
HWC composition timing rather than mode spoofing.

## Evidence matrix

Capture the same read-only bundle in clearly labelled states.

| State | Android settings | Displays | Goal |
|---|---|---|---|
| A | user's current state | BOTH | establish untouched baseline |
| B | known 60 policy | BOTH | only after a separate, approved state-change test |
| C | known 120 policy | BOTH | only after B and explicit review |
| D | 120 policy | TOP | compare scheduler when lower display is not participating |

The initial collector performs **A only**. It never changes policy.

For every capture, preserve:

- `min_refresh_rate`, `peak_refresh_rate`, `user_refresh_rate`;
- `dumpsys display`;
- `dumpsys SurfaceFlinger --display-id`;
- full SurfaceFlinger dump;
- logical display/window state;
- DRM connector/mode/CRTC state;
- `msm_drm` parameters;
- relevant display properties and process/service state;
- exact boot ID, firmware fingerprints and collector version.

## Decision rules

The research helper model uses a one-Hz tolerance around 60/120 and can label
a simple evidence tuple:

- lower logical ~=120 + lower physical ~=60:
  `LOGICAL_120_PHYSICAL_60`;
- top logical ~=120 + lower logical ~=60:
  `MIXED_LOGICAL_120_60`;
- lower logical ~=120 without physical timing:
  `LOGICAL_SHARED_120_PHYSICAL_UNKNOWN`;
- lower logical ~=60 + physical ~=60:
  `BOTTOM_60_CONSISTENT`;
- lower logical ~=120 + physical ~=120:
  `BOTTOM_120_REPORTED_AT_BOTH_LAYERS`.

These labels describe evidence only; none is automatically a bug diagnosis.

## Stop conditions

Stop before any state-changing experiment if:

- physical timing cannot be identified independently of Android mode data;
- the collector needs a write to obtain basic evidence;
- a command would change display mode, refresh policy, power, HWC state or
  SurfaceFlinger state;
- a proposed setting sequence can temporarily create `min > peak`;
- evidence from another repository is being used as if it were proof of this
  Thor's firmware behavior.

## Next step

Continue offline analysis of the upper display's desired/pending mode path in
the exact SurfaceFlinger binary and existing captures. The baseline collector,
binary copy and controlled policy probe are complete. Keep the Thor at 60/60;
the AYN framework's lower-panel brightness fade explains a possible black blink
without proving any 120 Hz scanout. Any further refresh-setting or direct
mode-setting experiment needs a separate safety review and a measurement that
can resolve the upper-display gate.
