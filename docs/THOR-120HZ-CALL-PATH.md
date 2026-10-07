
# Thor 120 Hz mode-switch call path

Status: **READ-ONLY STATIC ANALYSIS**
Date: 2026-10-07

This note narrows where the controlled 120/120 request can disappear between
Android policy and physical DRM scanout. Newer stock-aligned device-tree and
upstream Linux evidence now show that the Thor lower CH13726A path is intended
to support both 120 Hz and 60 Hz; that source evidence is still distinct from a
physical measurement on the validation unit. This note does not propose another
state-changing probe.

## Known physical result

The validation Thor accepted a 120/120 refresh policy in DisplayModeDirector for
about ten seconds, while all independently observed active layers stayed at
60 Hz:

- SurfaceFlinger active modes: 60 / 60;
- Qualcomm SDM: cur:60 / cur:60;
- HWC vsync period: 16,666,666 ns;
- DRM CRTC 181: 1080x1920x60cmd;
- DRM CRTC 243: 1080x1240x60vid;
- SurfaceFlinger mode-change count: zero.

That means the failure boundary is above, at, or immediately below the
SurfaceFlinger/HWC mode-change handoff. Merely observing a 120 vote is not proof
that an HWC mode request occurred.

## Android 13 path

The closest public framework reference is LineageOS 20 / Android 13. The names
and control flow below match AOSP Android 13 architecture; they are reference
code, not proof that AYN made no private changes.

### 1. DisplayModeDirector -> DisplayManagerService

DisplayManagerService.DesiredDisplayModeSpecsObserver walks every logical
display. For each display whose desired specs changed it calls:

LogicalDisplay.setDesiredDisplayModeSpecsLocked(...)

which ultimately reaches the local physical display device.

### 2. LocalDisplayAdapter -> SurfaceControl

LocalDisplayDevice.setDesiredDisplayModeSpecsLocked(...) resolves the framework
mode ID to a SurfaceFlinger mode ID, preferring the vendor/default mode group,
then asynchronously calls:

SurfaceControl.setDesiredDisplayModeSpecs(displayToken, modeSpecs)

Important consequence: the framework can show a 120/120 policy before an active
physical mode has changed.

### 3. SurfaceFlinger stores policy first

Android 13 SurfaceFlinger::setDesiredDisplayModeSpecsInternal:

1. ignores the request only when the backdoor override is active;
2. calls display->setRefreshRatePolicy(...);
3. returns if the policy is unchanged;
4. **for an internal display that is not SurfaceFlinger's active display, stores
   the policy and returns without applying a mode**;
5. otherwise calls applyRefreshRateConfigsPolicy(...).

The validation dump explicitly reported:

mode override by backdoor: no

so the known backdoor short-circuit is not the explanation.

The inactive-internal branch is highly relevant to the lower screen. Android 13
SurfaceFlinger still has a single active internal display concept. A secondary
internal display may therefore hold a 120 policy while remaining physically at
its previous 60 Hz mode.

This mechanism can explain the **lower display** result without any HWC failure.

It does not, by itself, explain why the upper display also remained at 60.

### 4. SurfaceFlinger chooses a preferred mode

applyRefreshRateConfigsPolicy:

- informs the scheduler that a primary/non-primary display policy changed;
- asks the scheduler for its preferred display mode;
- if the scheduler's mode belongs to this physical display, uses it;
- otherwise falls back to the policy's default mode;
- if allowed, calls setDesiredActiveMode(...).

Reference log strings worth searching in an existing capture are:

- Setting desired display mode specs
- Inactive display
- trying to switch to Scheduler preferred mode
- switching to Scheduler preferred display mode

If the 120/120 policy exists but none of the preferred-mode strings appears for
the upper physical display, the request was suppressed before a desired active
mode was created.


### 4a. Accepted 120 policy rules out a simple 60-default explanation

Android 13 RefreshRateConfigs validates a policy before storing it.

The reference is explicit:

- defaultMode must exist;
- defaultMode's FPS must be inside primaryRange;
- available primary modes are then filtered by resolution, DPI, mode group
  (unless group switching is enabled) and the primary range;
- an empty filtered set is fatal.

Therefore, **if the 120/120 range seen in the SurfaceFlinger dump is the
accepted current SF policy for the upper physical display**, its default mode
cannot simply be a 60 Hz mode outside that range.

This removes one easy explanation for the upper-panel result.

Mode groups still matter for deciding *which 120 config* is available, but they
cannot by themselves explain an accepted 120/120 SF policy whose only default
mode is 60.

The remaining high-value distinction is now:

- was the upper display considered inactive by SurfaceFlinger, so the accepted
  policy was stored but never applied; or
- was it active, in which case a 120 desired mode should normally have been
  selected and the next boundary is the desired-mode/HWC handoff?

### 5. Desired mode is only a pending request

setDesiredActiveMode does not immediately program the panel. It stores the
desired mode, schedules composition, resynchronizes hardware vsync and leaves a
mode change pending for the main SurfaceFlinger composition path.

### 6. Active-display gate is checked again

SurfaceFlinger::setActiveModeInHwcIfNeeded iterates internal displays with a
pending desired mode.

It explicitly does this:

- no desired mode -> skip;
- display is no longer the active display -> **clear the desired mode and abort**;
- desired mode no longer exists -> clear and abort;
- desired mode no longer allowed -> clear and abort;
- already active -> complete without an HWC switch;
- otherwise call DisplayDevice::initiateModeChange(...).

Reference strings:

- changing active mode to
- Desired display mode is no longer supported
- initiateModeChange failed

This second active-display gate strengthens the lower-display explanation:
even a previously-created pending lower mode is discarded if that physical
display is not the active internal display at apply time.

### 7. HWC handoff

DisplayDevice::initiateModeChange stores the upcoming active mode and calls:

HWComposer::setActiveModeWithConstraints(physicalId, hwcId, ...)

Only here does the framework actually ask Hardware Composer to transition.

Therefore the existing result must not be described as "HWC rejected 120"
unless the logs or binary instrumentation show this handoff occurred.

## Qualcomm composer / SDM reference path

The Thor uses Qualcomm's composer/SDM stack. Public Qualcomm display source is
architecture guidance only; the exact pulled Thor binaries remain the authority.

A contemporary public Qualcomm HWC implementation follows this path:

HWCSession::SetActiveConfigWithConstraints
-> HWCDisplay::SetActiveConfigWithConstraints
-> RequestActiveConfigChange
-> later ProcessActiveConfigChange
-> SubmitActiveConfigChange
-> SubmitDisplayConfig
-> display_intf_->SetActiveConfig(config)

### Immediate HWC validation

SetActiveConfigWithConstraints can reject before scheduling when:

- config is absent from variable_config_map_;
- IsModeSwitchAllowed(config) returns false;
- seamless switching was required but the config is not seamless-compatible.

SurfaceFlinger sets seamlessRequired=false in the Android 13 reference path,
so a same-group seamless restriction should not be the primary blocker for this
specific request.

Useful Qualcomm strings:

- Invalid config
- Not allowed to switch to mode
- Seamless switch to the config

### Successful HWC call still does not mean the panel changed

A successful SetActiveConfigWithConstraints only stores:

- pending_refresh_rate_config_;
- requested refresh time;
- expected applied time.

The actual SDM call occurs later when the pending change is ready:

display_intf_->SetActiveConfig(config)

A later failure can therefore happen **after SurfaceFlinger received success**.

Useful strings:

- Failed to set %d config! Error: %d
- Active configuration changed to: %d

This distinction matters for the Thor: if the framework did hand 120 to HWC and
the initial request returned success, the physical state can still stay at 60
until a later validate/present path submits the config.

## A second Qualcomm refresh mechanism

Qualcomm built-in-display validation also has a dynamic-refresh path:

GetOptimalRefreshRate(...)
-> display_intf_->SetRefreshRate(refreshRate, force, idle)
-> GetRefreshRate(...)

This is separate from the HWC2 active-config transition.

So there are two vendor concepts that must not be conflated:

1. switching a display/HWC config (for example a 60-mode ID to a 120-mode ID);
2. SDM dynamic refresh-rate selection during validation.

The Thor probe ended with SDM cur:60 and 16.666 ms vsync. Whichever vendor path
was involved, neither had produced a measured 120 active state.

## Thor-specific proprietary evidence

Public Lineage/TheMuppets proprietary repositories identify their AYN common
blob source as Thor.20260112.215904. This is a Thor firmware source, but not the
exact validation firmware .377_20260206.

Useful files present in those extracted blobs include:

- vendor/lib64/libsdmextension.so;
- vendor/lib64/libdisplayqos.so;
- Qualcomm display/DPU configuration XMLs;
- advanced_sf_offsets.xml;
- thermallevel_to_fps.xml.

The Thor-specific proprietary repository also contains QDCM calibration files
named for both physical panels:

- qdcm_calib_data_icna3520_amoled_panel_with_DSC.json;
- qdcm_calib_data_ch13726a_video_mode_dsi_boe_panel_with_DSC.json.

That independently confirms the vendor split:

- upper: ICNA3520;
- lower: CH13726A, video-mode DSI.

The common Qualcomm XMLs contain generic 60/90/120/144 tuning. They prove the
stack can model those rates; they do not prove that each Thor panel supports
every rate.

## Lower-panel hardware and driver evidence

The previous research state treated a 60-only Linux description as the strongest
public clue for the CH13726A. That is now obsolete.

### Stock-aligned AYN device tree

The CH13726A lower panel is described as DSI video mode with:

```text
qcom,dsi-supported-dfps-list = <120 60>
qcom,mdss-dsi-pan-enable-dynamic-fps
qcom,mdss-dsi-pan-fps-update = "dfps_immediate_porch_mode_hfp"
qcom,dsi-dyn-clk-enable
qcom,dsi-dyn-clk-type = "constant-fps-adjust-hfp"
qcom,dsi-dyn-clk-list = <1011000000 505000000>
qcom,mdss-dsi-bypass-ram-switch
```

The node has one nominal 1080x1240 60 Hz timing. The 120 operating point is
therefore modeled through Qualcomm dynamic-FPS / dynamic-clock machinery rather
than a second static timing node.

The Thor upper ICNA3520 is materially different: its device tree contains
separate 120 Hz and 60 Hz timing configs and explicit timing-switch commands.

### AYN lower-panel bypass/pass-RAM call path

Public AYN display-driver source exposes the exact sysfs node written by the
Thor framework:

```text
/sys/class/bypass_ram_class/bypass_ram_device/bypass_ram
```

The driver parses `qcom,mdss-dsi-bypass-ram-switch` for the secondary panel and
maps the sysfs values to:

```text
write 1
  -> dsi_panel_switch_bypass_ram(1)
  -> DSI_CMD_SET_VID_BYPASS_RAM

write 0
  -> dsi_panel_switch_bypass_ram(0)
  -> DSI_CMD_SET_VID_PASS_RAM
```

The stock-aligned CH13726A device tree defines those commands as display
off/on sequences containing:

```text
BYPASS RAM -> B9 00
PASS RAM   -> B9 11
```

The reverse-engineered AYN framework path uses:

```text
peak >= 110
  -> bypass_ram = 0
  -> PASS RAM

otherwise
  -> bypass_ram = 1
  -> BYPASS RAM
```

That creates a concrete cross-layer chain:

```text
Android refresh-policy observer
  -> lower brightness fade
  -> bypass_ram sysfs
  -> AYN DSI driver
  -> secondary CH13726A DSI command
```

The exact electrical/internal-panel meaning of B9 00 vs B9 11 is not claimed
without a controller datasheet. The driver naming and call path are sufficient
to prove that the sysfs write is a real secondary-panel DSI action, not an
unrelated setting.

### Current upstream Linux Thor driver

The upstream CH13726A driver contains a dedicated
`ayntec,thor-panel-bottom` match and explicit 1080x1240 timings calculated at
both 120 Hz and 60 Hz.

This aligns with the stock Android connector advertisement and the AYN
device-tree DFPS list rather than with the older 60-only Linux description.

A kernel mode table still does not prove actual panel scanout on the validation
unit. The remaining hardware question is therefore not "can the source model
120?" but "does the exact stock Android path actually enter its 120 operating
point, and what is the measured cadence when it does?"

## Ranked hypotheses after the controlled probe

### A — lower request stored but not applied because it is not SF-active
**Confidence: high as the explanation for the lower result in this probe.**

Prediction in existing logs/dumps:

- lower 120 policy exists;
- lower active mode stays 60;
- lower has no real HWC transition;
- SurfaceFlinger identifies another internal display as active or records
  "Inactive display".

No vendor failure is required. This says nothing negative about the lower
panel's actual 120 capability; the request can be stopped before that capability
is exercised.

### B — upper request was accepted but did not schedule/enter a desired 120 transition
**Confidence: medium-high; main unresolved boundary.**

The upper was the expected active internal display and nevertheless recorded
zero mode-change initiations.

The exact Thor SurfaceFlinger binary has a pending-desired-mode branch:
if the display already has a desired-mode change marked pending, a new desired
mode can replace the cached value and return without requesting a fresh
composition. This is now a concrete candidate mechanism, but the capture does
not expose the pending-state byte needed to prove it.

Other remaining candidates are scheduler/preferred-mode selection or another
pre-HWC policy gate.

Evidence needed:

- active physical-display identity;
- accepted policy default mode ID and mode group;
- scheduler preferred mode;
- desired/upcoming mode state;
- whether a composition was scheduled for the policy edge.

### C — lower 120 requires the AYN DFPS + PASS-RAM path
**Confidence: high as architecture; not exercised by the captured probe.**

The stock-aligned device tree advertises lower 120/60 DFPS and the framework
switches the secondary panel to PASS-RAM for the 120 policy. If a future safe
transition reaches the lower hardware path, these states should be captured
together with SDM/vsync/DRM cadence.

### D — HWC/SDM rejects or fails a valid 120 request
**Confidence: low for the captured probe.**

The exact SurfaceFlinger mode-change counter stayed zero, so the normal HWC
mode-initiation boundary was not reached. Vendor rejection becomes relevant only
after a future trace proves a request crossed that boundary.

### E — tearing is downstream pacing rather than a false physical mode
**Confidence: open.**

If both display paths are later measured at the intended cadence and tearing
still occurs, investigate SurfaceFlinger pacesetter/follower scheduling,
present fences and Qualcomm composition/vsync timing rather than panel-mode
capability.

## Read-only decision tree

Use existing evidence only:

1. Was the upper physical display SurfaceFlinger's active internal display?
   - no -> active-display gating explains both;
   - yes -> continue.
2. Did SF create a desired 120 active mode for the upper?
   - no -> problem is scheduler/policy/mode selection before HWC;
   - yes -> continue.
3. Did initiateModeChange reach HWC?
   - immediate error -> HWC config validation;
   - success -> continue.
4. Did Qualcomm submit SetActiveConfig(120-config)?
   - no -> pending-config/validate path;
   - yes + error -> SDM/kernel config rejection;
   - yes + success -> compare active DRM mode/vblank and panel timing.

Only after this tree is resolved should another state-changing refresh test be
considered.
