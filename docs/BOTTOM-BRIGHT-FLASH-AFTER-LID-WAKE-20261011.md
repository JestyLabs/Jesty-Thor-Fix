# BOTTOM bright flash after wake — investigation conclusion

Follow-up: [framework OFF-sentinel correction feasibility](SECONDARY-DISPLAY-OFF-BRIGHTNESS-RESEARCH.md) records a separate prospective correction and optional-feature acceptance criteria. It does not change the runtime disposition below.

PR #62 closes the research phase for [issue #65](https://github.com/JestyLabs/Jesty-Thor-Fix/issues/65), on top of the TOP cancellation correction integrated in PR #82. No new runtime brightness or display-power workaround is justified by the available evidence. A concrete defect in the archived firmware's secondary-display brightness conversion was identified; its contribution to the observed physical flash remains unmeasured.

## Observations and rechecked timing

The original RC2 observation was a brief BOTTOM-only brightness increase on the second BOTH lid-open cycle, with firmware .377, 120 Hz selected, Display Fix/CPU Fix ON, Guard OFF and USB attached. Both displays recovered. A separate 16 October report describes approximately one second of extra BOTTOM brightness after a hardware-button wake, then normal appearance without reboot. The later report does not establish exact installed version, refresh, toggles or transport.

Rechecked owner-local inputs against their archive hashes and replayed the original relative intervals:

| RC2 cycle | App ON after recorded sleep | App ON before recorded wake | Visual observation |
| --- | ---: | ---: | --- |
| First | 836 ms | 35.060 s | No flash reported |
| Second | 850 ms | 41.822 s | Brief bright BOTTOM on opening |

These are software-clock intervals, not optical timing. The ON message follows the platform call's return; it does not report its start, actual scanout or brightness. The same closing-time ON pattern occurred without a reported flash on the first cycle. It is therefore insufficient to identify the later flash's cause. No matching wake-time ON is observed in that retained filtered log, which also cannot prove all operations absent.

A later full log contains 90 brightness-reason messages split equally between the two logical display controllers, ten brightness-synchronizer messages and one access-control message. These are not 101 optical measurements or 101 flashes. They come from a later collection and do not recover the missing brightness trace at the original event.

## Firmware finding

The archived firmware has a separate brightness conversion for the internal secondary display. Static call-path inspection establishes:

1. The power-state update uses the special OFF brightness value while the display is OFF or its fade level is zero.
2. The display manager preserves that value through its request/clamp path.
3. The secondary-display branch applies a brightness curve before the downstream helper tests for the OFF sentinel. The primary-display branch goes directly to that helper.
4. The curve converts the OFF value into a positive normalized input of approximately **0.505**. The sentinel check can no longer recognize it. That result then enters the device-specific backlight mapping and dispatch path.

A restricted, owner-local interpreter of the extracted arithmetic instructions confirms the conversion. This is a demonstrated firmware-side loss of sentinel semantics, not a measured 50.5% luminance, a proof of maximum brightness, or execution of the vendor HAL/panel. Panel-enable gates, downstream mapping, cached brightness, compositor content/fade and restore ordering can still affect what becomes visible.

The firmware also has a separate lower-panel brightness fade around refresh-setting changes. Its observer requires the relevant refresh-setting path; a lid wake or low-power event alone does not prove that fade ran. It is not selected as this incident's cause. Kernel inspection confirms independent panel-enable checks and distinct power/brightness paths, without establishing the actual register values or timing at the flash.

This makes firmware brightness restoration a stronger supported candidate than before. The exact physical causal chain still lacks a synchronized command/brightness/optical trace. App-triggered power interactions are not excluded merely because the app never sets a brightness value.

## Our app and the new production-class characterization

The app's `DisplayHardware` requests panel power and maintains its power flag; it does not set brightness, panel calibration, refresh policy or backlight registers. Guard OFF does not disable ordinary BOTH reconciliation.

The production coordinator and hardware, compiled with synthetic Android observations, now cover these additional cases:

- TOP -> BOTH restores a dark lower panel even when the previous TOP OFF left the flag at0.
- Subsequent BOTH1/1 samples do not repeatedly issue ON.
- Both CRTCs inactive does not request ON; firmware-restored BOTH needs no extra app command.
- Mixed CRTC1/0 can request ON. This observation alone cannot distinguish legitimate recovery from a system sleep transition.
- Boot hold, disabled fix, unknown mode/CRTC, BOTTOM ONLY and a fresh-mode mismatch do not authorize that automatic ON.

There are **17 new assertions**, alongside the **29 TOP cancellation assertions** from PR #82. These execute our production classes with synthetic platform boundaries. They do not reproduce optical brightness or validate the vendor bridge.

## Why no additional app workaround is integrated

Using the power flag as a blanket awake gate would suppress legitimate TOP -> BOTH recovery: that flag also represents the lower panel's OFF state while Android is awake. Suppressing all mixed-CRTC ON requests has the same problem.

The earlier interactive-state and deferred-recovery models remain research-only. A host model's AWAKE/UNKNOWN input does not establish a reliable privileged signal on this firmware; permanent UNKNOWN can strand recovery, and a software check does not make a later platform transition atomic. The model is not imported into `src/` or the APK.

Clamping or restoring brightness from the app would compete with the system's independent brightness writers and introduce new policy. No supported app-side correction for the firmware conversion is established. The PR #82 pending-state correction remains integrated unchanged; it is not presented as a brightness fix.

## Validation, privacy and closure

The offline parser tests preserve provenance/timebase/privacy constraints. Their process identifiers and clocks are synthetic. Historical collection guidance is retained separately as an inactive protocol; no Thor session is authorized or executed here. Exact firmware, disassembly, original logs, hashes and private replay tools remain owner-local outside Git.

At the owner's request, issue #65 is to close after integration of this investigation. Closure records the firmware-side finding and the absence of a validated additional application workaround. It does not claim the physical flash was reproduced or fixed. There is no release/version/signing change. Any future release containing PR #82 still requires its scoped physical acceptance; this investigation adds no second runtime patch to that candidate.
