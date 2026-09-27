# 0.32 device validation

The final 0.32 candidate was exercised on a physical AYN Thor running Android 13 firmware `TKQ1.231222.001`, build dated 2026-02-06.

## Passed

- [x] Update in place from 0.31.1 with the signing certificate unchanged.
- [x] Existing installations migrated to fix ON.
- [x] TOP with fix ON ended at `power=0`, CRTC 181 active, CRTC 243 inactive.
- [x] TOP with fix OFF immediately restored native power and both CRTCs active.
- [x] Sleep/wake while OFF did not execute a wake repair.
- [x] Reboot while OFF returned to native mode.
- [x] Re-enable reconciled TOP to true bottom-off.
- [x] Disable at the 650 ms boundary won after an in-flight OFF.
- [x] Normal fix-ON wake repaired after the preserved 700 ms window and ended `OFF_OK` after Android screen-on.
- [x] Reboot with fix ON returned to the true bottom-off state.
- [x] LITTLE, BIG, and PRIME current/maximum clocks were credible.
- [x] The sustained low-load warning required ten qualifying samples and cleared after clocks dropped.
- [x] The animated background paused outside the Activity and retained a static fallback.
- [x] Manual DRM verification reported CRTC 181 and 243 correctly.
- [x] No extreme jank or stuck clocks appeared in focused 0.32 testing.

## Scope note

The final palette/wordmark build did not repeat the entire earlier rapid-wake, 700/750 ms, long-suspension, and Octopi regression matrix. Those paths use the unchanged 0.31.1 wake scheduler. The 0.32-specific close, toggle, 650 ms race, OFF reboot, ON reboot, telemetry, and DRM paths were exercised live.

Raw device logs are deliberately not stored in this public-source candidate because they may contain serials, installed-package names, accounts, network details, and unrelated system activity.

## 1.0.0 stable release smoke test

The exact signed `1.0.0` APK was installed in place on the same physical Thor.

- [x] Package identity was `versionCode=37`, `versionName=1.0.0`.
- [x] Signing certificate matched the 0.31/0.32/0.33 update line.
- [x] TOP with fix OFF/native returned `power=1` with both CRTCs active.
- [x] TOP with fix ON returned `power=0`, CRTC 181 active, CRTC 243 inactive.
- [x] The in-app bottom-screen check reported the lower screen fully off.
- [x] BOTH mode restored `power=1` and both CRTCs while leaving the master fix enabled.
- [x] Clearing the app from Recents left the true-off state intact.
- [x] A subsequent sleep/wake restored true-off and ended `OFF_OK`.
- [x] A normal reboot with the fix enabled restored `mode=1`, `power=0`, CRTC 181 active, and CRTC 243 inactive before opening the dashboard.
- [x] The final lockup, ON/OFF artwork, amber toggle, status copy, and ASCII support links rendered correctly.

This is a focused stable-release smoke test. The complete rapid-wake and timing
regression matrix remains documented above for 0.32; the 1.0.0 refresh does not
change that display-control implementation or its timing.
