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
