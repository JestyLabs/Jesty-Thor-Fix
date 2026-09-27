# Jesty Thor Fix 1.3.0

This release makes the dashboard quieter, clearer, and more trustworthy. Both
firmware fixes are unchanged; the visible status and telemetry have been
redesigned around automatic hardware evidence.

## What changed

- Removed the redundant `Check now` button. Display and CPU status now settle
  automatically while the dashboard is open.
- Replaced instantaneous LITTLE/BIG checks with a 12-second kernel
  `time_in_state` window. Brief frequency spikes no longer look like pinning.
- Added clear physical display states for BOTH, AYN black screen, Jesty
  true-off, BOTTOM, transitions, and persistent mismatches.
- Replaced the charging-only system-power estimate with **Battery draw**. It is
  shown only while the Thor is discharging; USB power deliberately shows
  `UNPLUG USB`.
- Simplified the two fix labels, centered the Jesty header over the dashboard,
  and kept only the open-source message in the lower-right badge.
- Added a protocol migration so updating from 1.2.0 relaunches the privileged
  daemon automatically instead of waiting for a reboot.

The app does not calculate battery-runtime gains. Historical power samples
remain available in the repository with their original limitations.

## Update warning

Changing **AYN Dashboard CPU Fix** still performs the same required Android
framework restart and closes open apps. Enabling it also reapplies that restart
once during a normal boot. This behavior is unchanged from 1.2.0.

## Artifact

- Package: `com.thor.displaypowertest`
- Version: `1.3.0` (`versionCode 45`)
- APK: `Jesty-Thor-Fix-1.3.0.apk`
- Signing certificate SHA-256:
  `727D4850779BED1E51018108E13BC399D4DA38CFC68F4F7504120AD5E2DAD6FC`
- APK SHA-256: `2D75A7018C9A9D967BEFC1CD0B8FA92933EB835BFF67E13438049E727463BBEF`
