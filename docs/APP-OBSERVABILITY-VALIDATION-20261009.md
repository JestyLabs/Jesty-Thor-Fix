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
pending. No refresh-policy or direct sysfs writes were made during the 60 Hz checks.

## Initial 120 Hz selection check

After the 60 Hz references, the owner selected 120 Hz through the normal device
control and returned to the same moving game content in BOTH mode. System
min/peak settings read approximately 120 Hz, but both active internal DRM modes
remained 60 Hz. SurfaceFlinger retained TOP active mode 0 and BOTTOM active mode
1, both mapped to 60 Hz. Its primary policy range was 0–120 Hz for TOP and
120–120 Hz for BOTTOM. This is a requested-versus-applied discrepancy, not
evidence of actual mixed 120/60 operation or physical 120 Hz output. A complete
read-only capture was saved owner-local.

The owner returned to the diagnostics app without changing the refresh choice
and reported normal appearance. Its dashboard still reported BOTH, with display
and CPU fixes requested ON and lid guard OFF. TOP changed to active mode 1
(120 Hz) in SurfaceFlinger and a 120 Hz DRM mode. BOTTOM also had an active
120 Hz DRM mode, but SurfaceFlinger retained active mode 1, whose local mapping
is 60 Hz. This records a cross-layer discrepancy; it does not establish optical
cadence, frame uniqueness or its cause. No direct display-policy or sysfs
command was used to force a mode.

On returning to the same game without changing the refresh choice, the owner
again reported normal moving content. Both active DRM modes returned to 60 Hz,
with TOP SurfaceFlinger active mode 0 and BOTTOM mode 1. Owner-local photographs
also show the dashboard retaining its 120-mode label while its current-FPS
counter reads 120 with the diagnostics app and 60 with the game. The counter
agrees with these software observations but does not independently measure
either panel's optical cadence. The reason for the app-dependent transition
remains unproven. This game session therefore does not validate moving content
at applied 120 Hz.

A 60 FPS content cap or app refresh preference could influence the selected
mode. A 60 FPS producer can also run on a 120 Hz display with repeated frames;
these two rates must be measured separately. The game's cap, API request and
vendor policy were not established, so this transition alone does not prove a
refresh defect. See the [Android frame-rate API documentation](https://developer.android.com/media/optimize/performance/frame-rate).
The SurfaceFlinger and DRM reads were sequential, not an atomic snapshot;
repeatable synchronized transition evidence remains necessary before attributing
the cross-layer observations to stale state or incorrect mode forwarding.

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

## 2026-10-09 audit clarification and next experiment boundaries

**OBSERVED — Activity lifecycle boundary, not lid boundary.** Start trial only arms the passive one-shot. The beginning is the next Activity pause; its end is the next Activity resume. A physical lid close/open, suspend begin/end or an AYN mode change is *not* timestamped by this mechanism. The trial can include foreground-to-background activity, awake intervals and app resumption. No periodic sleep collection or kernel residency instrument was installed.

**OBSERVED — rejected overnight trial.** The retained trial has `interval_ms=34905132`, `awake_clock_ms=777948`, `suspend_clock_delta_ms=34127184`, `suspend_percent=97.8`, `plugged_start_mask=0`, `plugged_end_mask=2`, `state=EXTERNAL_POWER_OR_UNKNOWN`, `energy_method=UNAVAILABLE`, and unknown Wh/W. Its 97.8% is specifically the fraction of total interval represented by the **difference of the elapsedRealtime and uptimeMillis deltas**; it is not independently verified kernel deep-sleep residence, physical display OFF duration or an energy measurement. The API indicates battery at the first boundary and USB at the second; it does not establish when the cable was attached. Owner observations of the closed lid and cable handling remain separately documented; do not replace API classification with the recollection.

The report contains no historical AYN mode for this interval. Requested switch mask 3 is not evidence that TOP/BOTH stayed unchanged or that either fix's effect remained active throughout. Kernel cumulative suspend success/failure counters lacked a start baseline. `energy_now` sysfs unavailability does not establish BatteryManager ENERGY_COUNTER unavailability. Neither charge percent, battery-voltage difference nor suspend percentage may be converted to energy or panel-rail conclusions.

**Next read-only device gate:** preserve/export this report, disconnect USB, wait for Android's battery state to update, then run a supervised ~10-minute trial whose start and end are both battery. Reopen and inspect results before reconnecting USB. A procedure-valid trial requires plugged masks 0/0, valid clocks, unchanged requested fixes and either a described energy method or a correct unknown/rejection reason. This is NOT energy-counter calibration or evidence of savings. Optional kernel-counter endpoint checks form a separate, instrumented experiment: reconnecting ADB perturbs the endpoint, and cumulative suspend increments do not measure duration.

**Candidate follow-up (not yet implemented by this documentation commit):** additive allowlisted report tokens `boundary_source=ACTIVITY_PAUSE_RESUME`, `suspend_source=SUSPEND_CLOCK_ESTIMATE` and `minimum_duration_met=1|0|unknown` (>=300000 ms, valid shorter interval, invalid/indeterminate clocks, respectively). No change to private S1 storage, sampling, arithmetic, state precedence or rejection conditions. A short trial with an energy estimate remains preliminary and ineligible for comparative analysis. Test endpoints 299999 and 300000 ms, unknown/invalid clocks, USB, unavailable counters, picker/cancellation/recreation, and max-length sanitized export before integrating code. The AYN mode at resume cannot be reconstructed from a stale Q sample; defer automatic mode provenance until fresh telemetry exists.

**Cross-PR provenance:** the simultaneous-looking SF/DRM values were read sequentially; current A→B→C association is not proof of a game request, vendor policy or optical cadence. `enable=1` with `active=0` does not prove fully unpowered rails. The earlier lower-active BOTH sleep sample and the later both-inactive closed sample remain separate observations with UNKNOWN cause.

**Remote-bundle limits:** manifests verified 124 and 529 entries; the archive totals are 125 and 532 files. Sanitized process IDs cannot independently re-establish continuity of display-service processes. Signed APK, signing artifacts and owner photos remain private and absent. No app code, signing, production behaviour or firmware was changed by this clarification.
