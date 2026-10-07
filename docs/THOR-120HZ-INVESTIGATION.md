# Thor mixed-refresh / "fake 120 Hz" investigation

Status: **ACTIVE RESEARCH - 60/60 RESTORED; FURTHER TESTS PAUSED**

**2026-10-07 detailed follow-up:** [event ordering, cached-mode provenance, default-display-centric completion and Qualcomm's deferred-apply boundary](THOR-120HZ-EVENT-ORDER-AND-MODE-PROVENANCE.md).

**2026-10-07 offline invalid-mode review:** [SF physical-display identity guard, per-display mode IDs, trace/ATRACE caveats, and exact-binary follow-up](THOR-120HZ-INVALID-MODE-OFFLINE-REVIEW.md). This review uses public Android 13 code and the previously documented results; it does not claim to have independently opened the user's local binaries or raw trace.
Collector/schema: **THOR_REFRESH_INVESTIGATION_V2**  
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
why the active upper display did not issue a mode-change request in that first
sustained probe. A later trace captured a different outcome below.

### Supervised atrace probe: mode switch reached HWC, two brief black blinks

On the same boot, with the user watching the Thor in BOTH, one further bounded
60 -> 120 -> 60 policy test ran with an on-device 60/60 rollback and a host
rollback in `finally`. The user reported **two brief black blinks in total**;
the first was identified on the lower screen. The visual timing of the other
blink was not captured precisely. This is a confirmed visual disruption report,
not a claim that either panel was damaged. No reboot occurred, the composer PID
remained `2319`, the boot ID remained
`cecf25bc-4750-4933-89c9-9acd29d89499`, and both CRTCs were active at the
final 60/60 check.

This capture differs materially from the earlier 12:28 probe:

| Evidence | New capture |
|---|---|
| 120/120 policy | 23:42:17.246 upper; 23:42:17.248 lower |
| `setDesiredActiveMode` | atrace at uptime 76261.168 s |
| HWC request | `SetActiveConfigWithConstraints`, `SubmitDisplayConfig` for both paths at 76261.173-76261.180 s |
| HWC target markers | `ActiveModeFPS_HWC` = 120 for both physical display IDs |
| SF effective active mode | upper `activeModeId=1` (120 Hz); lower `activeModeId=1` (60 Hz in its reversed mode table) in the in-window dump |
| Vsync evidence | vendor `VsyncPeriod=8333333` ns and SF `onComposerHalVsync(8333333)` from ~76261.200 s until rollback |
| Lower display error | `Trying to initiate a mode change to invalid mode 1` at 23:42:17.252; corresponding invalid mode 0 at 23:42:22.660 rollback |
| Rollback | 60/60 policy at 23:42:22.652; HWC target markers return to 60; SF upper active mode returns to 60 |

At rollback, the SurfaceFlinger policy log counted **one mode change under the
120/120 policy for each display**. Thus the prior zero-count conclusion is
specific to the earlier probe; it is not a general block on 120 Hz. The upper
path demonstrably reached HWC and SF reported 120 Hz. The lower path received
a 120 Hz HWC request, but SF continued reporting its 60 Hz active mode and
also logged an invalid-mode error. The trace does not independently prove the
lower panel's physical scanout cadence: no in-window lower DRM/vblank sample
was captured. The 8.33 ms vsync markers are not enough by themselves to assign
that cadence to the lower panel.

Raw `atrace gfx` and logcat remain outside Git at
`C:\Temp\thor-refresh-trace-20261007\`. SHA-256:
`atrace.txt` = `0B823204F73E35AF0E29C3A33F8B94143F79975389A5D26EEC60B8F03A0E6035`;
`logcat.txt` = `F454772323E709885D030A078E217D258848C38070CD26EF101CE72605E70DA8`.
No more refresh-policy writes are planned until the lower invalid-mode path and
blink are understood from the captured evidence.

### SurfaceFlinger binary check: no HWC mode request in the probe

**PROVEN for this exact firmware binary and the 12:28:39-12:28:49 policy
window.** The copied `/system/bin/surfaceflinger` has ELF Build ID
`a4e0851419d45662b0fd5cd067b585bf`. Its policy-change log string is used
by the routine at `0x125800`: that routine atomically exchanges the per-display
mode-change counter at object offset `+0x2a0` with zero (`0x125878-0x125884`)
and logs the old value (`0x125924`). The mode-initiation routine increments
that same counter at `0x124250-0x124258` (and on its alternate path at
`0x1243ec-0x1243f4`) **before** dispatching the HWC mode request. These
locations were checked with ARM64 disassembly of the copied binary, not inferred
solely from log wording. The matching
[AOSP `DisplayDevice` implementation](https://android.googlesource.com/platform/frameworks/native/+/refs/heads/android13-qpr3-c-s2-release/services/surfaceflinger/DisplayDevice.cpp)
also increments the counter before `setActiveModeWithConstraints`.

At policy rollback, the log reported `0 mode changes were performed under the
previous policy` for **both** displays. That proves no mode-initiation call
reached the HWC request **during the final 120/120 policy interval**, including
failed HWC attempts counted by this path. It does **not** prove that no attempt
occurred immediately before that interval.

The safe raise sequence matters here:

```text
Settings state:
60/60
  -> peak=120  => transient settings state 60-120
  -> min=120   => final settings state 120-120
```

The raw SurfaceFlinger log now settles this for the sustained probe. Both
physical displays changed directly from fixed 60/60 to fixed 120/120 at
`12:28:39.059`, then directly back to 60/60 at `12:28:49.107-108`. There is no
intermediate 60-120 policy block in this capture. The `mNumModeSwitchesInPolicy`
counter therefore did not hide an initiation under an intermediate policy.
The AYN-modified `SettingsObserver.onChange()` may have batched delivery, but
the log alone does not prove its exact internal scheduling.

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
`DisplayDevice::setDesiredActiveMode` at `0x1255ac-0x125610`. If its
`mDesiredActiveModeChanged` byte at `DisplayDevice + 0x281` is already set, it
replaces the cached desired mode and returns false. The caller at `0x1af168`
only schedules a new composition when that return is true. This matches the
[AOSP pending-mode behavior](https://android.googlesource.com/platform/frameworks/native/+/refs/heads/android13-qpr3-c-s2-release/services/surfaceflinger/DisplayDevice.cpp).

Because the log shows a direct 60-60 -> 120-120 transition, the proposed
intermediate-policy stale-state sequence is rejected for this probe. A pending
desired mode could still predate the probe or arise at the single 120/120 edge;
neither was observed directly. The preferred-mode messages are `ALOGV` in AOSP
and absent from this exact Thor binary, so missing logcat messages cannot be
used to decide whether the desired mode existed.

Android 13's `setActiveModeInHwcIfNeeded()` also leaves the desired mode
present when `initiateModeChange()` returns an error; it logs the failure and
continues rather than clearing that desired state in the failure branch. That
provides one concrete way an earlier attempt could feed the cached-request path.

There is independent architectural corroboration that this class of state is
fragile: a later SurfaceFlinger modesetting-state-machine rewrite explicitly
describes the old implementation as carrying redundant desired/pending state
that could become stale and says the previous `setDesiredMode` could fail to
emit a mode-change event when a mode was already desired or pending. This is
**not proof that the Thor hit that later-fixed bug**, but it makes the stale
desired/pending mechanism a grounded hypothesis rather than a speculative one.

The captured dump does **not** expose `mDesiredActiveModeChanged`, and the
versioned notes do not contain the pre-12:28:39 policy-change block. The upper
gate therefore remains open until the local raw log is checked.

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

The Thor has two unlike internal displays and, importantly, **two different
refresh mechanisms**:

- the ICNA3520 upper panel exposes explicit 120 Hz and 60 Hz timing configs;
- the CH13726A lower panel is described by the stock-aligned device tree as a
  60 Hz base timing with `120 60` dynamic-FPS support, dynamic DSI clocks and
  an AYN-specific RAM-bypass/pass command path.

That changes the original framing. The leading question is no longer simply
whether Android invents a logical 120 Hz mode for a physically 60-only lower
panel.

The question to prove is now:

> When Android requests 120 Hz on the Thor, which panel-specific mechanism is
> selected, where does the request stop, and what scanout/pacing does each
> physical display actually reach?

The previous "fake 120" name remains useful as historical shorthand, but a
60-only lower-panel assumption is no longer justified by the current evidence.

## Evidence ladder

Use these labels consistently:

- **PROVEN** - measured on the validation Thor or established directly from its
  pulled firmware/binaries.
- **OBSERVED** - independently reported/implemented by another project or
  public source, useful corroboration but not our device proof.
- **HYPOTHESIS** - explanation that still needs a discriminating measurement.

## What is already proven in this project

**PROVEN on the validation Thor**

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
- In the controlled 120/120 policy window, both active DRM CRTCs, Qualcomm SDM
  state and HWC vsync-period evidence remained at 60 Hz.
- The exact Thor SurfaceFlinger binary recorded **zero mode-change initiations**
  for both displays during that window. The request therefore did not reach the
  normal HWC active-mode handoff in that probe.

**Strong hardware/source evidence, not yet a physical measurement on this exact
stock firmware**

- The public AYN/Lineage device tree for the CH13726A lower panel advertises
  `qcom,dsi-supported-dfps-list = <120 60>`, enables dynamic FPS and dynamic
  DSI clocking, and defines AYN-specific bypass/pass-RAM DSI commands.
- The current upstream Linux CH13726A driver has an
  `ayntec,thor-panel-bottom` match with explicit 1080x1240 120 Hz and 60 Hz
  modes.
- The Thor upper ICNA3520 description contains separate 120 Hz and 60 Hz timing
  configs and distinct timing-switch commands.

These sources make the old "lower hardware is definitely 60-only" hypothesis
too weak to use as a premise. They do not replace a measured stock-Android
vblank/panel scanout result.

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

For the earlier 12:28 probe, an important finding is that Android 13 SurfaceFlinger stores a refresh
policy for an inactive internal display but deliberately does not apply its mode
until that display becomes the active internal display. A second gate clears a
pending desired mode if that internal display is no longer active. This is a
strong architectural explanation for why the Thor lower display can show a
120 policy while remaining on its previous 60 Hz HWC/DRM mode.

That does **not** explain the upper panel remaining at 60 during the controlled
120/120 probe. The exact Thor SurfaceFlinger binary and its policy counters now
show that neither panel initiated an HWC mode request in that policy window.
The upper-panel question is narrower: was it SF-active at the policy callback,
and, if so, did it already have a pending desired mode or did a newly scheduled
mode get skipped before HWC? Exact-binary disassembly shows the fixed-120
policy's default mode maps to upper mode 1 (120 Hz), the selected mode is
checked for eligibility, and the allowed path calls `setDesiredActiveMode`.
The dump shows the upper active about four seconds after the policy callback;
it does not expose the pending flag or prove it was active at that callback.

Public Qualcomm HWC code also shows that SetActiveConfigWithConstraints can
queue a pending config before it is submitted to the display interface. That
boundary was not reached in the earlier probe. The later supervised trace
reached it for both display IDs and exposed a lower invalid-mode error.

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

### Historical Linux 60-only clue - superseded by newer Thor support

An older Linux/Gamescope patch described the CH13726A lower path as having only
a 60 Hz mode. That was useful early evidence, but it is no longer the strongest
available hardware description.

Newer upstream Linux support is explicitly matched to
`ayntec,thor-panel-bottom` and contains both 120 Hz and 60 Hz modes.
Separately, the stock-aligned AYN device tree advertises lower-panel DFPS
`<120 60>`.

The older 60-only description is therefore retained only as historical context,
not as proof that the Thor lower panel is physically limited to 60 Hz.

### Stock-aligned AYN device-tree evidence

The CH13726A lower-panel node is configured as DSI video mode with:

```text
qcom,dsi-supported-dfps-list = <120 60>
qcom,mdss-dsi-pan-enable-dynamic-fps
qcom,mdss-dsi-pan-fps-update = "dfps_immediate_porch_mode_hfp"
qcom,dsi-dyn-clk-enable
qcom,dsi-dyn-clk-type = "constant-fps-adjust-hfp"
qcom,dsi-dyn-clk-list = <1011000000 505000000>
```

Its nominal timing node is 60 Hz, so 120 is represented as a dynamic-FPS /
dynamic-clock operating point rather than a second static timing node.

The same node enables `qcom,mdss-dsi-bypass-ram-switch` and defines distinct
BYPASS/PASS RAM DSI command sequences.

### Current upstream Linux Thor lower-panel driver

The current CH13726A DRM panel driver is explicitly matched as:

```text
compatible = "ayntec,thor-panel-bottom"
```

and exposes 1080x1240 timings calculated at both 120 Hz and 60 Hz.

That is strong independent evidence that the Thor lower panel is intended to
operate at both rates. It is still a driver description rather than a direct
oscilloscope/vblank measurement of the validation unit.

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

The v2 collector can pull these read-only and generate SHA-256 manifests.

## Current hypotheses

### H1 - lower 120 policy is stored but not applied while the lower display is SF-inactive
**Confidence: high for the earlier 12:28 probe; insufficient for the later trace.**

Android 13 SurfaceFlinger can retain refresh policy for a secondary internal
display without initiating its physical mode transition while another internal
display is the active display.

Prediction:

- lower 120 policy is visible;
- lower active mode remains 60;
- no lower HWC mode-change initiation occurs.

This fits the earlier zero mode-change count and does not require a vendor
failure for that probe. It does not explain the later lower HWC request and
invalid-mode log.

### H2 - upper 120 policy is accepted but never becomes an HWC mode-change request
**Confidence: medium-high for the earlier 12:28 probe only.**

The upper display was marked active in the later dump, yet its mode-change
counter remained zero. A silent 60 Hz scheduler selection is disfavored by the
fixed-120 policy and exact-binary allowed-mode path. The remaining boundaries
are active-display identity at policy delivery, desired-mode pending state,
and execution/clearing of a newly scheduled change before HWC initiation.

The exact Thor binary contains a branch where an already-pending desired mode
can be replaced without scheduling a new composition. That is a plausible
mechanism for that earlier run, not yet proof. The later run did issue an upper
HWC request and report upper 120 Hz, so this cannot be a universal gate.

### H3 - lower 120 uses DFPS + PASS-RAM rather than a conventional static mode switch
**Confidence: high as architecture; exact lower physical state still unproven.**

The AYN framework writes the lower-panel `bypass_ram` sysfs control around
refresh-policy changes. Public AYN driver source maps that control directly to
secondary-panel DSI BYPASS/PASS RAM commands, while the lower device tree
advertises 120/60 DFPS and two dynamic DSI clocks.

Prediction for a real lower 120 transition:

- lower path enters PASS-RAM state;
- lower dynamic timing/clock changes from its 60 operating point;
- DRM/SDM/vsync evidence moves consistently toward 120.

The earlier probe never reached an SF/HWC mode transition. The later trace did
reach HWC but did not include an in-window lower DRM/vblank measurement, and
SurfaceFlinger logged an invalid lower mode.

### H4 - tearing is a pacing/synchronization problem even when both panels can run 120
**Confidence: open.**

If a later safe measurement proves both physical display paths at ~120 while
tearing persists, the investigation moves to SurfaceFlinger pacesetter/follower
selection, present fences, HWC composition and per-display vsync timing rather
than a fake-mode explanation.

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

- lower logical ~=120 + lower DRM/scanout ~=60:
  `LOGICAL_120_SCANOUT_60`;
- top logical ~=120 + lower logical ~=60:
  `MIXED_LOGICAL_120_60`;
- lower logical ~=120 without DRM/scanout timing:
  `LOGICAL_SHARED_120_SCANOUT_UNKNOWN`;
- lower logical ~=60 + DRM/scanout ~=60:
  `BOTTOM_60_CONSISTENT`;
- lower logical ~=120 + DRM/scanout ~=120:
  `BOTTOM_120_REPORTED_AT_BOTH_LAYERS`.

These labels describe Android-vs-scanout evidence only; none is automatically a
bug diagnosis or direct proof of the physical panel's electrical/optical refresh rate.

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

Keep the Thor at 60/60. The immediate task is offline analysis of the second
trace's lower `invalid mode` error, its HWC request and the AYN secondary-panel
PASS-RAM/fade handling. Compare the exact lower mode IDs and the order of
HWC submission, SF internal-state update and rollback. The earlier upper
no-request branch remains a timing/context question, but it no longer blocks
the primary finding. Do not infer lower physical 120 Hz, a safe user-facing
120 Hz setting, or the cause of either blink from the current trace.
