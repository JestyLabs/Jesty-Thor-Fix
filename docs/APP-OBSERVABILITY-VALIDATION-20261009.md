# PR #55: Thor validation, 2026-10-09

Status: **partial physical validation; energy comparison remains pending**.

## Installed candidate and app checks

The owner manually installed the candidate based on `9dcdffb`. The installed
DEX, manifest and resources matched all eleven corresponding entries from that
commit's CI artifact. The installed APK verified with the established signer.
All CI checks for that candidate passed. Raw APKs, UI captures, reports and
device/session identifiers remain owner-local outside Git.

The endpoint-reporting correction in this change was then built, signed with
the same established signer and installed as an in-place test update. The
existing overnight trial remained intact and exposed its endpoint fields.
The installed APK's hash matches that signed local reporting-patch build.

Verified on the Thor:

- The dashboard and diagnostics preview open and receive existing telemetry.
- Save report opens Android's document picker and creates readable text.
  Saved text matches the preview's report body exactly and passed checks for
  forbidden device/boot identifiers and PID/UID/path/timestamp keys.
- Cancelling the document picker returns to the dashboard when cancellation
  is sent after the picker is ready.
- The passive result survives app recreation and explicit app-only
  force-stop/relaunch. Android reports the controlled stop as USER_REQUESTED;
  this validates the retained reason path, not crash or memory-pressure causes.
- The root daemon remained the same process through the app-only stop. Existing
  display services remained running. TOP mode reported upper CRTC active and
  lower CRTC inactive at 60/60; this is software state, not a panel power reading.

Export automation initially attempted clicks while the landscape keyboard was
in fullscreen editing mode. Completing the keyboard's Done action finished the
save. This was not evidence of an app export failure.

## Owner's overnight observation

The owner reports a closed lid and no charger overnight, recalls arming the
trial, and says the app was reopened before connecting USB in the morning.
The retained trial records approximately 9 h 42 min elapsed, about 13 min on
the uptime clock and a 97.8% suspend-clock difference, with boot count verified.
Requested switches were display/CPU ON and lid guard OFF. The trial does not
record historical AYN mode, so the current TOP mode cannot be assigned to the
whole overnight interval.

Energy state is **EXTERNAL_POWER_OR_UNKNOWN**. Wh and mean watts are unknown.
The updated report identifies the start as battery (plugged mask 0) and the end
as USB (plugged mask 2). Start/end voltage was 4349/4377 mV and temperature
28/26 degrees C. These are retained endpoint observations, not measurements
of the entire night's conditions. The recorded resume boundary explains energy
rejection; it does not establish when USB was connected during the interval.
The owner's observation is retained separately from the API classification. Do not turn
battery percentage or suspend-clock percentage into a nighttime energy
measurement, panel scanning conclusion or zero-power claim.

Read-only discovery found charge_counter and voltage_now nodes and accessible
cumulative suspend success/failure counters; energy_now was unavailable.
These findings do not establish BatteryManager energy-counter availability,
calibration or a suspend count attributable to this trial. No initial baseline
for cumulative kernel counters was captured overnight.

## Supervised 60 Hz display and lid checks

With USB connected, min/peak policy remained 60/60. Display and CPU fixes were
requested ON and lid guard OFF. The owner confirmed the upper image was stable
and the lower screen visually off in TOP mode. Switching only the display fix
OFF through the app changed the lower CRTC from inactive to active; switching
ON restored inactive. Visual confirmation for the OFF state remains pending.

The owner selected BOTH using the normal device controls. Both internal CRTCs
were active at 60 Hz. Closing the lid produced Android Asleep, both logical
displays OFF and both CRTCs inactive in the captured sample. On opening, the
owner confirmed both screens recovered normally; both CRTCs became active.

The owner then selected TOP mode and repeated the lid cycle. The closed sample
again showed Asleep, both logical displays OFF and both CRTCs inactive. On
opening, the owner confirmed normal upper image with the lower still off;
upper CRTC was active and lower inactive. Display-service processes remained
unchanged across both cycles.

With familiar moving game content in TOP mode, the owner reported normal image
without noticed tearing, flicker or abnormal pauses. A concurrent read showed
policy 60/60, upper CRTC active at 60 Hz and lower inactive. This qualitative
observation is not a measurement of frame rate, pacing or physical cadence.
The owner then selected BOTH and reported the same moving content remained
normal; both internal CRTCs were active at 60 Hz and policy remained 60/60.

These are single supervised cycles and discrete software observations, not
proof of uninterrupted suspend, panel rails, physical cadence or energy. The
BOTH closed-lid sample differs from the earlier lower-active sleep capture;
the cause and repeatability remain unknown. Mixed-refresh tests are still
pending. No refresh-policy or direct sysfs writes were made.

## Follow-up and remaining gates

The report now retains bounded voltage/temperature context on rejected results
and exposes the plugged mask for each boundary. This is reporting only: energy
rejection and sampling boundaries remain unchanged. The introduction also
distinguishes whole-interval energy estimates from panel power.

Still required:

- Confirm both endpoints unplugged: unplug before leaving the app, reopen it
  before reconnecting USB, and verify duration/counter context.
- Confirm short-trial and trial cancellation behaviour, Activity recreation
  while the document picker is open, and energy-counter fallback/unknown cases.
- Establish measurement resolution and independent suspend evidence, then
  repeat controlled OFF/ON trials for each fix and the bundle.
- Measure diagnostics overhead and check wake behaviour without continuous
  ADB polling during sleep. No new wake/temperature regression is claimed from
  the owner's report alone.

The PR remains a draft. No physical-panel consumption or energy saving has
been established. See [protocol and interpretation](APP-OBSERVABILITY-AND-ENERGY.md).
