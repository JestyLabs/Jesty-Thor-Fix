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

## 1.0.1 focused UI and telemetry check

The signed `1.0.1` patch was installed in place on the same physical Thor.

- [x] Package identity was `versionCode=38`, `versionName=1.0.1`.
- [x] Signing certificate matched the stable update line.
- [x] TOP with fix OFF/native returned `power=1` with both CRTCs active.
- [x] The manual check identified LITTLE and BIG pinned at their maximums.
- [x] TOP with fix ON returned `power=0`, CRTC 181 active, CRTC 243 inactive.
- [x] The manual check reported the bottom hardware fully off and clocks
  released.
- [x] A full app stop and relaunch preserved and rendered each selected state.

This patch changes dashboard copy and combines existing DRM/CPU telemetry in
one manual check. Display control, wake repair, and timing code are unchanged.

## 1.1.0 two-fix validation

The exact signed `1.1.0` APK was installed in place on the same physical Thor.

- [x] Package identity was `versionCode=42`, `versionName=1.1.0`.
- [x] Signing certificate matched the stable update line.
- [x] Dashboard-open/BOTH/fix-OFF reproduced LITTLE `2.02/2.02 GHz` and BIG
  `2.71/2.71 GHz` and produced the sustained red warning.
- [x] Enabling Dashboard CPU Fix set the vendor property and restarted display
  and USB without changing the Android boot ID.
- [x] The privileged daemon restarted after the display reset and continued to
  observe subsequent TOP/BOTH changes.
- [x] With Dashboard CPU Fix enabled, BOTH produced 0/25 simultaneous
  LITTLE+BIG maximum samples in the focused low-load run.
- [x] TOP true-off ended at `power=0`, CRTC 181 active, CRTC 243 inactive.
- [x] Sleep/wake in TOP ended `OFF_OK` with CRTC 243 inactive.
- [x] Closing the UI with Back left the daemon and both fixes active.
- [x] A real Android reboot restored Dashboard CPU Fix, True Bottom Display
  Fix, TOP mode, and physical lower CRTC off.
- [x] UI mode/background followed physical `bottom_crtc`, avoiding the stale
  logical BOTH/TOP presentation seen during diagnosis.

Pulse/Cluster Tune coexistence was not claimed as confirmed because that exact
combination was not part of this focused release run.

## 1.1.1 restart hotfix validation

The exact signed `1.1.1` APK was installed in place on the same physical Thor.

- [x] Package identity was `versionCode=43`, `versionName=1.1.1`.
- [x] Signing certificate matched the stable update line.
- [x] CPU Fix OFF performed one Android framework restart and returned without
  the device entering suspend.
- [x] CPU Fix ON performed one Android framework restart and returned without
  the device entering suspend.
- [x] The timed transition wake-lock was active only during recovery and was
  released afterward.
- [x] The privileged daemon recovered after both transitions.
- [x] A normal reboot restored the saved CPU Fix choice and completed its one
  expected framework restart without a suspend delay.
- [x] The device remained stable after recovery with one app process and one
  privileged daemon.

This focused hotfix run validates the restart path changed in 1.1.1. It does
not repeat the unchanged display true-off and wake-repair matrix.

## 1.2.0 state model, telemetry, and persistence validation

The exact signed `1.2.0` APK was installed in place on the same physical Thor.

- [x] Package identity was `versionCode=44`, `versionName=1.2.0`.
- [x] Signing certificate matched the stable update line.
- [x] BOTH, AYN fake-off, and Jesty true-off were derived from live mode, power,
  and lower-CRTC state and selected their matching artwork.
- [x] CPU Fix active stayed green through repeated low-load and transient-high
  samples; no red stock-bug verdict was emitted while the fix was active.
- [x] CPU Fix OFF and ON completed one framework restart each in approximately
  28 and 26 seconds without changing the Android boot ID.
- [x] CPU Fix OFF in BOTH mode produced no false sustained-pinning verdict when
  LITTLE and BIG were not simultaneously pinned.
- [x] Live USB and estimated system-power telemetry returned after each
  framework restart.
- [x] TOP sleep/wake restored true-off and ended `OFF_OK` with the lower CRTC
  inactive.
- [x] A normal reboot changed the Android boot ID, restored both saved fixes,
  and completed the expected second framework phase in about 60 seconds.
- [x] The privileged daemon returned after every transition.
- [x] Closing the visible UI with Back left the privileged daemon and both
  fixes active.
- [x] Final state was BOTH with both fixes enabled and both displays active.

The power value is a live device estimate intended for nearby state
comparisons. Boot/load peaks and charging direction are expected and are not a
battery-runtime claim.
