**Historical protocol (2026-10-11), inactive.** Retained for research provenance. It grants no current device access or collection authorization. See [the completed investigation](BOTTOM-BRIGHT-FLASH-AFTER-LID-WAKE-20261011.md) for the 19 October findings and closure disposition.

# RC2 opportunistic wake observation — safe Codex handoff

**Scope:** Use the owner's already-running, authorized, corrected RC2
`1.7.1-rc2/73` physical validation on AYN Thor firmware `.377`.
This research document does **not** authorize any extra lid cycles,
installs, shell writes, screen-blank operations, refresh changes, or
deployment. The existing owner-approved ADB session is the maximum scope;
ask before expanding it.

## Goal

Determine whether the current RC2 emits `WATCH_BOTH ON` during **naturally
occurring** BOTH sleep transitions, and whether it correlates with a
visible BOTTOM flash *on lid opening*. Separately evaluate whether the
Android awake/interactive state is observable reliably enough to inform
a future runtime guard. A state observed after the event is NOT proof
of what `isInteractive()` would have returned at the exact command instant.

## Before doing anything

1. If the primary RC2 test's logcat recorder already exists, **reuse the
   recording**. Do not launch a second collector that competes with it,
   overwrite any logs, restart ADB, or reset logcat.
2. Confirm the current APK and daemon identity match the corrected RC2
   previously accepted for device testing; check the private local APK hash
   against the signed candidate metadata from main `17bac625...`.
3. Make a timestamped note of exact settings: BOTH or TOP, selected
   refresh policy (selected 120 Hz is not physical BOTTOM 120 Hz),
   Display Fix / CPU Fix / Lid Guard, USB state, battery, and screen awake.
4. Keep private filenames, device identifiers, full logcat and any optical
   captures outside GitHub and Actions artifacts.

## Passive recording during the tests the owner is already performing

- Only if no existing equivalent capture, and only within the *already
  authorized* read-only ADB scope, start a LOCAL computer-side append-only
  logcat stream with **monotonic** Android timestamps and buffers
  main/system/events. Keep the unfiltered raw logs private. Do not
  alter Android's logging level or clear buffers.
- While the user naturally runs scheduled RC2 BOTH lid and wake cases,
  record their approximate observation: lid-close/lid-open sequence number,
  whether BOTTOM looked briefly bright or black, whether both panels
  recovered, and a human time anchor. Ask for visual observations ONLY when
  already planned; do not ask for repeats to reproduce the flash.
- Use PowerManager `dumpsys power` and a read-only Q / paired DRM state
  snapshot **before and after** authorized scenarios, when fully awake,
  and only if permitted by the existing acceptance collector. Do NOT
  poll these heavyweight commands around the critical transition unless
  a separate overhead/risk review approves it. `dumpsys power` may
  include the latest sleep reason/timestamps, but is not a continuous
  instrument for the exact isInteractive value at the ON command.
- Do not activate Lid Guard ON, CPU Fix toggle, change 60/120 settings,
  invoke shell sleep/wake keys, restart compositor, force-stop, or modify
  kernel/driver parameters as part of this investigation.

## Correlation and interpretation

1. Extract the observed PowerManager sleep/wake times and logcat
   `WATCH_BOTH ON`, `WATCH_TOP OFF`, `WAKE_REPAIR_RESULT`,
   `EVENT ON CONFIRMED` and any vendor brightness/fade records.
2. Maintain one **monotonic clock** per timeline; do not correlate
   Unix/epoch and since-boot clock values without a verified mapping.
3. Classify per cycle:
   - Jesty ON near sleep and its time relative to PowerManager sleep;
   - Jesty ON near wake (or **not observed in available logs**);
   - any vendor brightness entries, and only any *actual* measured
     CRTC 1/0 observation when present (do not infer this from ON logs);
   - owner visual symptom; temporary brightness unmeasured means UNKNOWN.
4. Existing private RC2 evidence already records a `WATCH_BOTH ON`
   **+836 ms** after first sleep and **+850 ms** after second sleep.
   The second event occurred about **42 seconds before wake**, not at
   visible lid-open. Compare new accepted observations to those reference
   events, and distinguish normal from anomalous cycles.
5. The separate host-only `AutomaticBottomOnPolicy` is **not included**
   in the RC2 APK: the ongoing physical tests cannot validate its future
   effectiveness, performance, or exact awake/epoch gate. They CAN
   validate the existing ON-during-sleep signature and collect evidence
   about any alternative signal already emitted by Android.
6. A lack of log evidence is **UNKNOWN**, not proof the operation never
   occurred. A temporally nearby operation is correlation, not causation.

## Reporting / stop criteria

Produce a sanitized `RC2-BOTTOM-WAKE-FLASH-HANDOFF.md` privately containing
exact installed candidate identity, test conditions, per-cycle relative
timelines, whether user visually observed a flash, evidence completeness,
and three independent conclusions: ON-during-sleep, ON-near-wake,
and cause-of-brightness-flash, each PASS/FAIL/UNKNOWN as appropriate.
Refer to private evidence files by an owner-local manifest; do not publish
raw logs or device identifiers. Reuse the existing RC2 report package.

Immediately stop additional collection if it disrupts the existing
validation, affects measured CPU/latency, causes unexpected panel state
or service instability, or if owner authorization does not cover it.
Normal RC2 safety stop rules take precedence. No runtime fix, merge,
new APK or release action is included in this handoff.
