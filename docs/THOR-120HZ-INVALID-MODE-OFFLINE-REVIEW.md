# Thor mixed-refresh: invalid-mode and HWC trace offline review

**Status:** static source review / proposed discrimination; **no new Thor capture or physical test**  
**Date:** 2026-10-07

**Further source-level finding:** [Mode-object provenance, AOSP cached-pointer handoff, and per-display trace ambiguity](THOR-120HZ-EVENT-ORDER-AND-MODE-PROVENANCE.md).  
**Scope:** PR #37, supervised 23:42 probe on `Thor_V1.0.0.377_20260206_165408_user`. All conclusions about the Thor are based on the existing PR's reported trace/logs, **not** a new independent reading of its local raw files or device binaries.

## High-value new finding: the exact Android 13 error guard

The Android 13 QPR3 reference `DisplayDevice::initiateModeChange(const ActiveModeInfo& info, ...)` logs

```
Trying to initiate a mode change to invalid mode %s on display %s
```

when **either** the incoming mode is null **or** `info.mode->getPhysicalDisplayId() != getPhysicalId()`. It returns `BAD_VALUE` **before**:

1. incrementing `mNumModeSwitchesInPolicy`;
2. staging `mUpcomingActiveMode`;
3. writing the `ActiveModeFPS_HWC` atrace integer;
4. calling `mHwComposer.setActiveModeWithConstraints(...)`.

Source: [Android 13 QPR3 DisplayDevice.cpp](https://android.googlesource.com/platform/frameworks/native/+/refs/heads/android13-qpr3-c-s2-release/services/surfaceflinger/DisplayDevice.cpp), `initiateModeChange` (around lines 220-234 of the Gitiles rendering).

**Meaning:** If the exact Thor binary retains this guard, an `invalid mode 1` message with a **non-null numeric ID** is most naturally a **physical-display-identity mismatch**, **not** evidence that Qualcomm/SDM rejected mode 1, or that the lower panel is hardware-limited to 60 Hz. Verify the exact binary's branch, not just the existence of this string.

In the documented in-window SF table, the mode IDs are **display-local**:

| Display | Mode ID 0 | Mode ID 1 |
|---|---|---|
| TOP | 60 Hz | 120 Hz |
| BOTTOM | 120 Hz | 60 Hz |

The invalid-mode pairs on the **lower** display are especially suggestive:

- on entry, `invalid mode 1` could refer to a TOP 120 Hz mode object (`ID 1`) being passed to the BOTTOM;
- on rollback, `invalid mode 0` could analogously refer to a TOP 60 Hz object (`ID 0`) being passed to the BOTTOM;
- alternatively, a BOTTOM mode object could be carrying an incorrect/stale `physicalDisplayId` (for example during display recreation). **Neither candidate is demonstrated without exact-object provenance.**

Do **not** equate equal integer `DisplayModeId` values across physical displays. The ID is not a globally unique reference to one panel or to one refresh rate.

### Important constraint on that hypothesis

The same stock Android 13 `DisplayDevice::setDesiredActiveMode` has a **fatal** assertion if the desired mode's physical-display ID differs from the receiving DisplayDevice. A straightforward foreign-mode assignment through that unmodified setter therefore should not silently survive to `initiateModeChange`.

Possible explanations that remain to discriminate include a vendor modification, a display lifecycle/recreation path, a stale cached mode object after topology updates, or a **different guard implementation in Thor's exact binary**. A foreign-mode error should be called the *leading code-level hypothesis*, **not a diagnosed AYN bug**.

Source: [Android 13 QPR3 DisplayDevice.cpp](https://android.googlesource.com/platform/frameworks/native/+/refs/heads/android13-qpr3-c-s2-release/services/surfaceflinger/DisplayDevice.cpp), `setDesiredActiveMode` (around lines 518-544).

## The apparent contradiction can represent distinct attempts

The existing PR reports, during the supervised 120 policy:

- `invalid mode 1` on BOTTOM;
- `ActiveModeFPS_HWC=120` atrace markers for two physical IDs;
- `SetActiveConfigWithConstraints` and `SubmitDisplayConfig` trace activity for both;
- **one** mode-change count under that policy for each display on rollback;
- SF TOP active at 120 while SF BOTTOM remains at 60.

For the QPR3 reference implementation, the **invalid-mode guard returns before** the mode-change counter and the `ActiveModeFPS_HWC` trace point. Thus, if the exact Thor binary matches it, the invalid attempt **cannot be the same invocation** that emitted a BOTTOM `ActiveModeFPS_HWC=120` marker or incremented BOTTOM's per-policy counter. At least two distinct initiations/attempt paths must be reconciled on the lower display; the lower trace must not be described as one rejected HWC request.

`ActiveModeFPS_HWC` is written by **SurfaceFlinger** just before it asks the HWC to switch. It reports an **intended target**, not a measurement of the display, confirmation from the composer, or proof of 120 Hz physical scanout. A mode-change counter measures an initiation after validation, not completion. A `SubmitDisplayConfig` trace segment likewise needs its SDM return/config/result correlated before describing it as a successful applied mode.

Sources:
- [Android 13 QPR3 DisplayDevice.cpp](https://android.googlesource.com/platform/frameworks/native/+/refs/heads/android13-qpr3-c-s2-release/services/surfaceflinger/DisplayDevice.cpp)
- [Android 13 QPR3 SurfaceFlinger.cpp](https://android.googlesource.com/platform/frameworks/native/+/refs/heads/android13-qpr3-c-s2-release/services/surfaceflinger/SurfaceFlinger.cpp), `setActiveModeInHwcIfNeeded`.

## Secondary architectural clue: completion is default-display-centric

In the Android 13 reference, `SurfaceFlinger::updateInternalStateWithChangedMode()` obtains `getDefaultDisplayDeviceLocked()`, rather than taking an arbitrary physical display ID. Other completion paths use a shared `mSetActiveModePending` flag. This is a plausible contributor to an SF-visible TOP 120 / BOTTOM 60 discrepancy **even if a lower HWC request was made**. It is **not** proof that the BOTTOM hardware stayed at 60, nor proof the Thor has this unmodified AOSP behavior.

The reference's `setActiveModeInHwcIfNeeded()` also skips non-active internal displays; the reported near-simultaneous HWC paths for both IDs therefore merit exact call-site and ID verification against the proprietary build before attributing either to the stock SF loop.

Source: [Android 13 QPR3 SurfaceFlinger.cpp](https://android.googlesource.com/platform/frameworks/native/+/refs/heads/android13-qpr3-c-s2-release/services/surfaceflinger/SurfaceFlinger.cpp), `updateInternalStateWithChangedMode` and `setActiveModeInHwcIfNeeded`.

## Exact-binary analysis to do **offline on existing local copies**

The PR has already documented the exact /system/bin/surfaceflinger ELF Build ID `a4e0851419d45662b0fd5cd067b585bf`, and a `mNumModeSwitchesInPolicy` increment at `0x124250-0x124258` (alternate path `0x1243ec-0x1243f4`). This review **did not re-open that ELF**: the 22 copied binaries and the raw trace are stated to be on the user's Windows host, outside Git. No unsupported claim of independent disassembly is made here.

**Priority 1 — confirm the Thor guard**: disassemble/cross-reference `DisplayDevice::initiateModeChange` around the already-noted counter increments and `invalid mode` format string. Verify which condition leads to `BAD_VALUE`; verify whether counter/atrace/HWC are downstream of that branch and whether `setDesiredActiveMode` retains the physical-ID fatal check. Record byte offsets or function offsets, ELF Build ID, and exact outputs. Do not transpose AOSP's branch into Thor as fact until checked.

**Priority 2 — reconstruct two distinct per-display attempts**: from existing `atrace.txt` and `logcat.txt`, correlate exact timestamps, trace track identifiers, physical display IDs, mode IDs, HWC config IDs and the `initiateModeChange failed: -22` message if present. Build one timeline for the TOP and one for BOTTOM. The logcat wall-clock and atrace monotonic clock must be correlated explicitly; similar-looking seconds alone are insufficient. Compare arrival of the `invalid mode` entry to each display's `ActiveModeFPS_HWC` marker and the `SetActiveConfigWithConstraints` calls. Avoid inferring an HWC failure solely from the SF error.

**Priority 3 — vendor completion**: for `vendor.qti.hardware.display.composer-service`, `libsdmcore.so`, `libsdmextension.so`, `libsdmutils.so` and any corresponding display libraries already copied, find the **exact** target configuration, `SetActiveConfigWithConstraints` result, deferred `ProcessActiveConfigChange` / `SubmitDisplayConfig` and final `display_intf_->SetActiveConfig` result **per display**. Inspect existing `cur:...`, DRM CRTC and vsync evidence with sample times. Do not infer BOTTOM scanout from the shared/global 8.33 ms vsync evidence.

**Priority 4 — visible black blinks**: align the AYN services.jar observer's lower brightness=0, 50 ms delay, PASS/BYPASS-RAM write, 200 ms delay, brightness fade, and rollback with the two reported visual disruptions. The first blink is known to be on BOTTOM; the second is not precisely timed/identified. A forced lower fade can explain black frames without an HWC failure, but its execution in this trace still needs proof.

### Checks the current research tooling should gain

- Detect and report `Trying to initiate a mode change to invalid mode (\d+|null) on display ...` separately from Qualcomm/vendor config failures.
- Model mode IDs as `(physicalDisplayId, frameworkModeId, hwcConfigId, fps)` rather than a bare numeric ID.
- Report attempted/invalid, SF target-marker, HWC requested, HWC accepted, SDM applied, DRM active-mode, and physical-cadence evidence as **separate** states, never a single success flag.
- Add synthetic tests for (a) foreign physical-ID with matching numeric mode ID, (b) invalid SF attempt plus separate valid HWC request, (c) source files lacking timestamp alignment, and (d) global vsync that cannot be attributed to BOTTOM.
- Preserve source and firmware provenance; do not put raw user-device dumps or binaries into the public repository.

## Current conclusion / gate

The supervised run proves a 120 Hz **SF/HWC request boundary was exercised**, and SF reported TOP 120. It does **not** prove BOTTOM physical 120, BOTTOM physical 60, a vendor rejection, or the cause of either blink. The **highest-leverage next answer** is whether the exact Thor binary's invalid-mode branch checks **physical mode-object identity**; if so, determine how an object with the wrong physical ID reached BOTTOM while a separate valid lower request also took place.

Keep the Thor at 60/60. **No new refresh writes, firmware changes, app runtime changes or release changes.**
