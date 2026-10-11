# Research: transient BOTTOM bright flash after lid-open in BOTH

**Status: INVESTIGATION ONLY / no fix proposed / no additional physical test authorized.**
Related: [#37 mixed-refresh research](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/37). This report investigates a different observed symptom; do not conflate black refresh blinks, tearing and an apparent bright flash.

## One observed incident (owner report, 2026-10-11)

- **Software:** Jesty Thor Fix v1.7.1-rc2 / versionCode 73, AYN Thor firmware `.377`.
- **Selected settings:** BOTH, 120 Hz selected, Display Fix ON, CPU Fix ON, Lid Guard OFF.
- **Conditions:** USB attached after performance measurements; no fix or performance-profile setting was changed.
- **Action:** second ordinary close -> open lid cycle while in BOTH.
- **Visual report:** at opening, BOTTOM briefly appeared much brighter than normal; then recovered. Neither actual brightness level nor duration was instrumented. The terms "maximum" and "millisecond" are subjective descriptions, not measurements.
- **Afterwards:** both CRTCs active, both panels appeared recovered; same boot and observed service processes. This validates eventual recovery, not transient backlight behavior.
- **Unavailable evidence:** no synchronized event-time video, actual brightness register measurement or aligned per-cycle daemon/vendor log proving the trigger.

120 Hz **selected** is not proof of physical BOTTOM 120 Hz or of a refresh-mode change during the lid event. The lower panel's distinct optical cadence was not measured.

## Concrete source-backed hypothesis to test

At the current main source, `DisplayDecisionModel.reconcile()` returns `Action.ON` for **BOTH + TOP CRTC 1 + BOTTOM CRTC 0**, with Display Fix enabled, action gate clear and no pending wake repair. `DisplayActionCoordinator.onWatcherSample()` then calls `attempt(true, "WATCH_BOTH", mode)` (with a short duplicate-attempt throttle). The implementation gates unknown modes and boot hold, reads the paired DRM CRTC state and serializes display actions.

`DisplayHardware.apply(true, "WATCH_BOTH")` sets `display.power.state=1` and uses hidden `SurfaceControl.setDisplayPowerMode(bottomToken, 2)`. It **does not set a brightness level**. The vendor framework/panel may still implement a backlight or initialization transition when the panel is brought on. A short wake-stage 1/0 observation followed by this ON request is therefore a **plausible contribution**, not evidence that this command happened in the reported cycle or caused the flash.

The #58 paired DRM read changes how CRTC state is observed but introduce no brightness-setting call. The #57 Lid refactor was *not* part of the tested RC2. Lid Guard OFF does not disable the unrelated Display Fix BOTH reconciliation path.

A separate plausible explanation is **stock vendor panel/backlight/lid-open handling**, potentially influenced by selected refresh policy. The #37 exact-firmware research documented vendor brightness/fade and PASS/BYPASS-RAM logic for **refresh policy changes**, but no evidence currently ties that particular path to this lid-open incident. We should not extrapolate those traces to a lid event.

## Evidence discriminators (minimum read-only collection)

Use only already-authorized device evidence or a **new separately approved read-only capture**. Do **not** intentionally reproduce the flash by cycling the lid; the owner is currently testing a different RC.

1. Record the exact app APK SHA-256/source and daemon identity, mode and Fix/Lid flags, selected Android refresh policy, and relevant boot/session identity **privately**.
2. Prefer an existing logcat `-v epoch` export with at least a short before/after interval and the owner's approximate flash timestamp. Optionally sample existing read-only `Q` and DRM endpoints **only if already permitted**. Do not write display settings, backlight, `bypass_ram`, DSI, CPU governor or refresh policy.
3. Identify a `ThorDisplayDaemon: WATCH_BOTH ON` operation and/or `WATCH_BOTH:ON` action status **near the observed lid opening**; check whether a transient BOTH 1/0 source condition actually occurred, and if a second writer (vendor framework) was active. When a log is absent, report **NOT OBSERVED IN AVAILABLE LOGS**, not "did not happen".
4. Compare the time of any `WATCH_BOTH ON` with relevant vendor backlight/brightness/SurfaceFlinger logs, acknowledging that basic logcat may not contain actual driver transitions. A same-time association is **not causation**.
5. Record if the flash occurs in BOTH while both CRTCs are already 1/1 and without any Jesty ON request. This would weaken the particular WATCH_BOTH hypothesis (given complete reliable logs), not prove a vendor-only cause.
6. Use safe synthetic model tests to verify the branch condition. The existing Display Decision model *deliberately* handles BOTH 1/0 by requesting ON; do not "fix" it by suppressing all wake-on work.

## Offline tooling and privacy

`scripts/analyze-bottom-wake-flash.py` accepts a **local** `adb logcat -v epoch` text file and produces only recognized event categories and relative-time offsets. It never runs ADB, opens sysfs, changes Android properties, or exports original log text. The input can contain private details, so keep it outside GitHub, Actions artifacts and PR comments. Do **not** automatically upload an anonymized-looking raw log: sanitization must be reviewed.

Example from previously collected evidence (do not interpret as permission to start capture):

```powershell
python scripts/analyze-bottom-wake-flash.py --input "C:\Temp\thor-private\existing-logcat-epoch.txt"
# Optional only if the observer supplied an accurate logcat-clock timestamp:
python scripts/analyze-bottom-wake-flash.py --input "C:\Temp\thor-private\existing-logcat-epoch.txt" --observed-at 1791740000.5 --window-seconds 6
python -m unittest discover -s tests -p "test_bottom_wake_flash.py"
```

The tool's time alignment is only as good as the source clock and visual timestamp. If exact time is unknown, review chronological categories without suggesting alignment.

## Decision matrix before implementing a fix

| Evidence | Interpretation | Next action |
|---|---|---|
| Jesty `WATCH_BOTH ON` at same wake + proof of transient BOTH 1/0 | App-issued power-ON is plausible contributor | Investigate timing vs vendor backlight; derive narrow state-machine guard and host tests separately |
| No ON request in a sufficiently complete, time-aligned capture | Specific app ON hypothesis weakened | Investigate vendor lid / backlight paths read-only |
| Both app ON and vendor brightness/fade logged together | Temporal correlation, not proof of brightness cause | Investigate causal sequence before code changes |
| Log missing, visual time uncertain, or no CRTC samples during flash | INCONCLUSIVE | Preserve hypothesis; do not change wake policy |
| A repeat without explicit authorization | Not permitted | Stop; request a separate supervised plan |

## No-production-change guardrails

This draft should change **only** documentation, read-only local analysis scripts and synthetic tests. No new watcher polling, permanent runtime tracing, added daemon threads, display-power calls, CPU frequency/refresh writes, property changes, sleep/lid automation or modified firmware. In particular, do **not** add a blanket debounce that could delay True Bottom Screen Off / recovery.

After evidence review, any corrective runtime code must be **a new, separately reviewed PR**, with normal BOTH/TOP, sleep/wake and CPU Fix gates and owner-approved hardware tests before any merge.
