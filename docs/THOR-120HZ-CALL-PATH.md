
# Thor 120 Hz mode-switch call path

Status: **READ-ONLY STATIC ANALYSIS**
Date: 2026-10-07

This note narrows where the controlled 120/120 request can disappear between
Android policy and physical DRM scanout. It does not claim that the lower panel
physically supports 120 Hz and does not propose another state-changing probe.

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

## Linux hardware evidence

Current public Thor Linux support models:

- ICNA3520 upper panel with explicit 60 and 120 Hz timings;
- CH13726A lower panel with a 1080x1240 timing calculated at 60 Hz.

This conflicts with stock Android's lower DRM connector advertisement, which
contains both 1080x1240x60vid and 1080x1240x120vid.

The conflict is now a concrete research target:

> Why does the stock Android kernel/HWC advertise a lower 120 mode when the
> independent Linux hardware description treats the CH13726A Thor mode as 60?

Do not resolve that by assumption. A kernel-advertised mode is not a measured
panel scanout.

## Ranked hypotheses after the controlled probe

### A — lower request stored but not applied because it is not SF-active
**Confidence: high as an architectural explanation for the lower display.**

Prediction in existing logs/dumps:

- lower 120 policy exists;
- lower active mode stays 60;
- lower has no real HWC transition;
- SurfaceFlinger identifies another internal display as active or records
  "Inactive display".

No vendor failure is required.

### B — upper request never became a 120 desired active mode
**Confidence: medium-high; best explanation still missing direct proof.**

This is the key unresolved question because the upper should normally be the
active internal display.

Possible causes:

- SurfaceFlinger did not consider the expected upper physical display active;
- scheduler/preferred-mode selection remained at the 60 mode;
- framework-to-SF mode-ID/group translation selected a 60 default mode despite
  the 120 range;
- a vendor policy/mode-group constraint prevented a 120 candidate before HWC.

Evidence needed from the already-captured files:

- SurfaceFlinger active physical display;
- each display's policy default mode ID;
- supported mode IDs and groups;
- desired/upcoming active mode fields;
- scheduler preferred mode around the probe.

### C — HWC accepted a pending 120 config, then vendor submission failed
**Confidence: medium-low until an HWC handoff is proven.**

Prediction:

- SurfaceFlinger logs a 120 "changing active mode";
- no immediate initiateModeChange failed;
- Qualcomm later logs "Failed to set ... config" or never logs a completed
  active-config change;
- SDM/DRM remains 60.

### D — HWC rejected the 120 config immediately
**Confidence: low-medium.**

Prediction:

- initiateModeChange failed, Invalid config, or Not allowed to switch to mode;
- no pending config submission.

### E — 120 did apply physically but the capture missed it
**Confidence: low for the second probe.**

The 120 policy was held for about ten seconds while independent SF, HWC/SDM,
vsync-period and DRM evidence all remained 60 and SurfaceFlinger recorded zero
mode changes. A sub-capture transient cannot be absolutely excluded, but it is
not the best explanation of the observed steady state.

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
