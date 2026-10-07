# Thor 120 Hz: per-display mode-object provenance and event boundaries

**Status:** OFFLINE REFERENCE-SOURCE ANALYSIS — NO DEVICE/BINARY ACCESS  
**Scope:** Draft PR #37; compare with the supervised 2026-10-07 trace already documented in this PR.  
**Firmware investigated in earlier captures:** `Thor_V1.0.0.377_20260206_165408_user`.  
**Physical state at end of supervised test:** 60/60, no reboot, both CRTCs active.

## 1. Exact Android reference for the counter vs. invalid-mode ordering

Unlike some nearby older AOSP revisions, the following **specific reference revision** includes the policy counter *and* the invalid-mode guard in the same function:

- [AOSP DisplayDevice.cpp at 21c49252bf1039465ca5654fcdd3335f40c7ac49, lines 220-233](https://android.googlesource.com/platform/frameworks/native/+/21c49252bf1039465ca5654fcdd3335f40c7ac49/services/surfaceflinger/DisplayDevice.cpp#220)
- [Same file, setDesiredActiveMode at lines 518-543](https://android.googlesource.com/platform/frameworks/native/+/21c49252bf1039465ca5654fcdd3335f40c7ac49/services/surfaceflinger/DisplayDevice.cpp#518)
- [Same file, setRefreshRatePolicy at lines 558-576](https://android.googlesource.com/platform/frameworks/native/+/21c49252bf1039465ca5654fcdd3335f40c7ac49/services/surfaceflinger/DisplayDevice.cpp#558)

In this *reference version* the sequence is:

```text
DisplayDevice::initiateModeChange(info)
  |
  +-- !info.mode OR info.mode.physicalDisplayId != display.physicalId
  |       -> "Trying to initiate ... invalid mode %s on display %s"
  |       -> BAD_VALUE  [NO mode-change count; NO HWC target; NO HWC call]
  |
  +-- mNumModeSwitchesInPolicy++
  +-- mUpcomingActiveMode = info
  +-- ATRACE_INT(ActiveModeFPS_HWC..., info.mode.fps)  [SF's intent]
  +-- mHwComposer.setActiveModeWithConstraints(physicalId, hwcConfigId)
```

Consequences **conditional on the Thor ELF matching that guard/order**:

1. One `invalid mode` line and one `ActiveModeFPS_HWC` marker **cannot be from the same invocation**.
2. An increment of one on BOTTOM under the 120/120 policy cannot be explained by an invalid-mode-only invocation. It indicates another guard-passing initiation, whether or not Qualcomm accepted/applied it.
3. In this reference, `invalid mode 1` alone does **not** mean that local mode ID 1 is unsupported. If the mode pointer is present, it means the pointer's **physical-display identity** differs from the target display's identity.
4. The log embeds the *numeric ID of the provided mode object*, not its FPS, physical-owner ID, HWC config ID or why it arrived. Any attempt to map `1` globally to 120 Hz is unsound.

The PR previously reverse-engineered the exact /system/bin/surfaceflinger and identified the policy counter and increment offsets, but the *invalid-mode branch predicates* themselves still require independent inspection against that locally retained ELF. Do not claim the AOSP predicate is already proven for AYN.

## 2. A subtle reference-code provenance window

[Android 13 QPR3 SurfaceFlinger.cpp, setActiveModeInHwcIfNeeded, lines 1202-1294](https://android.googlesource.com/platform/frameworks/native/+/refs/heads/android13-qpr3-c-s2-release/services/surfaceflinger/SurfaceFlinger.cpp#1202) reads the cached `desiredActiveMode` (an object containing a mode pointer), then looks up a **current** `desiredMode = display->getMode(desiredActiveMode->mode->getId())`. It validates that the current mode exists, uses that lookup for FPS reporting, but hands **`*desiredActiveMode` (the original cached object)** to `display->initiateModeChange(...)`.

That makes a specific class of discrepancy worth testing offline: the current mode-ID lookup is not an automatic replacement/refresh of the cached mode pointer. If a display's mode inventory or physical identity changed between the original cache operation and HWC handoff, the cached pointer could be stale even though a *same-numbered* current mode exists. **This is a candidate timeline/lifecycle mechanism, not proof it occurred on the Thor.**

A strong counterweight exists in the same Android reference: `DisplayDevice::setDesiredActiveMode` has a **fatal precondition** if the supplied mode pointer's physical-display ID differs *at assignment time*. Thus a foreign TOP mode pointer would normally fail early rather than survive to `initiateModeChange`. Any credible Thor explanation must therefore identify a later stale-object/topology transition, another producer, or a vendor difference. The two errors on entry and rollback may also be distinct mechanisms.

In particular, the current source calls `display->getMode(modeId)` within that same display, but later calls `initiateModeChange` with the **cached** object. The exact Thor binary must establish whether it does the same and whether the `DisplayModePtr` state can actually become stale without an intervening display lifecycle operation. No hotplug/recreation was proven in the trace.

## 3. Strong active-display constraint: two 120 target markers need explanation

In the same [Android 13 QPR3 function](https://android.googlesource.com/platform/frameworks/native/+/refs/heads/android13-qpr3-c-s2-release/services/surfaceflinger/SurfaceFlinger.cpp#1202), the iteration is over internal displays but a `!isDisplayActiveLocked(display)` branch **aborts and clears** a pending mode before the HWC handoff.

The supervised trace as summarized in PR #37 claims `ActiveModeFPS_HWC=120` and `SetActiveConfigWithConstraints` for **both physical IDs**, while a later SF dump identifies TOP active and BOTTOM inactive. That combination is **not explained by the straightforward single-active-internal-display AOSP path** at a stable moment. Discriminate:

- Vendor modified the active-display gate or has a second vendor-driven path;
- Active-display identity changed during the very short handoff interval;
- Atraces are from different displays/paths/timestamps than the summary implies;
- A trace counter/track label was mapped to a display incorrectly.

**No definitive branch can be selected without per-track physical ID evidence and the exact binary.** In particular, an SF dump captured later cannot establish which display was active at the earlier callback.

## 4. Why TOP 120 / BOTTOM 60 in SF need not describe BOTTOM scanout

The Android 13 reference [`updateInternalStateWithChangedMode`](https://android.googlesource.com/platform/frameworks/native/+/refs/heads/android13-qpr3-c-s2-release/services/surfaceflinger/SurfaceFlinger.cpp#1146) obtains `getDefaultDisplayDeviceLocked()` and updates that display's state. The pending-mode completion path is therefore *default-display-centric* in this implementation.

This is a plausible explanation for **framework-reported state lag** for a second internal panel, but it proves neither that the BOTTOM HWC config was applied nor that the panel scanned at 120. For that we need a time-stamped BOTTOM-specific DRM active mode, per-panel vblank cadence or stronger measurement.

## 5. Qualcomm reference separates acceptance, execution, and physical proof

The independent older Qualcomm [sm8150 HWC2 implementation](https://android.googlesource.com/platform/hardware/qcom/sm8150/display/+/refs/heads/master/sdm/libs/hwc2/hwc_display.cpp) shows:

```text
SetActiveConfigWithConstraints
  -> validate map / seamless condition
  -> RequestActiveConfigChange        [pending ID + target time]
  -> return success                    [NOT necessarily applied]
ProcessActiveConfigChange
  -> SubmitActiveConfigChange
       -> SetActiveConfig(pending)    [may fail]
       -> on success update transient vsync timeline and clear pending ID
```

It also exposes `GetTransientVsyncPeriod`, so a reported vsync period can reflect transition bookkeeping rather than an independently sampled electrical cadence. A trace marker naming `SubmitDisplayConfig` only proves entry to that function unless its return/result is established. This public Qualcomm source is **not** the Thor's exact composer implementation and must not be used to claim that its specific logic or outcomes happened.

## 6. Public vendor binary provenance is not an exact-firmware analysis

The public [TheMuppets AYN QCS8550 common vendor manifest](https://github.com/TheMuppets/proprietary_vendor_ayn_qcs8550-common/blob/lineage-23.2/qcs8550-common-vendor.mk) lists `libsdmextension`, `libdisplayqos`, `advanced_sf_offsets.xml`, and `thermallevel_to_fps.xml`. That confirms some available reference components, not their behavior on `.377_20260206`.

The GitHub connector exposed metadata for a binary `proprietary/vendor/lib64/libsdmextension.so` but did **not** provide its bytes for static disassembly in this pass. No hashes or symbol behavior were invented. The exact 22 Thor copies, raw atrace, and logcat are on the owner's Windows host, not in this PR.

## 7. Concrete offline evidence checklist (no device writes)

Prioritize these steps **against the already-saved local files**:

1. **Exact ELF guard:** inspect `/system/bin/surfaceflinger` Build ID `a4e0851419d45662b0fd5cd067b585bf`, the invalid-mode format-string xrefs, and both `mNumModeSwitchesInPolicy` increment sites (PR notes: `0x124250-0x124258` and `0x1243ec-0x1243f4`). Record the mode-null vs physical-ID predicate and confirm whether the trace/counter comes before or after guard failure.
2. **Trace separation:** split the existing 23:42 entry/rollback windows into `INVALID_SF_ATTEMPT`, `VALID_SF_INITIATION`, `HWC_CONFIG_REQUEST`, `VENDOR_SUBMITTED`, `VENDOR_APPLIED`, `SF_ACTIVE_MODE`, `DRM_ACTIVE_MODE`, and `PHYSICAL_CADENCE_MEASURED`. Preserve each evidence source, timestamp/clock domain and display ID.
3. **Mode provenance:** resolve, for every alleged attempt, `(physicalDisplayId, DisplayModeId, HwcConfigId, FPS)`. Numeric `mode 1` alone never identifies a panel or a physical Hz value.
4. **Clock alignment:** `logcat 23:42:17...` is wall-clock and `atrace 76261...` is device monotonic/uptime. Establish the mapping with a shared marker or robust anchor, **not** by subtracting unrelated timestamps without provenance. Keep unknown alignment explicitly unknown.
5. **Panel state:** the documented AYN `services.jar` fade, lower backlight zero/write, PASS-RAM sysfs command and restoration may explain a black blink. Search the existing logs for *executed* writes, not just decompiled intended actions; do not conflate this with physical refresh-mode change.
6. **External evidence:** the CH13726A Linux driver revision that added 120 Hz was a driver source update, not direct verification of the user's stock Android hardware timing. The [v2 cover letter explicitly calls out adding 120 Hz](https://www.mail-archive.com/dri-devel%40lists.freedesktop.org/msg595282.html). Keep that distinction intact.

The host-only capture analyzer now emits `sfInvalidModeEvents` with the reported source display, mode token (including null), line number, and timestamp *without* automatically joining those events to HWC requests. It deliberately preserves the cross-clock and cross-display uncertainty.

## Stop gate

No additional refresh settings writes, panel commands, app runtime edits, release promotion, or physical-mode tests. A diagnosis of a vendor bug requires evidence from the **exact** Thor build; any proposed fix requires a separate reviewed rollback and explicit hardware safety analysis.
