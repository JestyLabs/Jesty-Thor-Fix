# Jesty Thor Fix 1.0.4

This dashboard patch makes the current fix, display mode, and suspected stock
clock pinning easier to read at a glance.

## What changed

- Moved `FIX ACTIVE` / `NATIVE THOR MODE` beside the Jesty lockup and aligned
  it to the right edge of the control column.
- Replaced the dense Display & Service panel with a clear `DISPLAY MODE` value:
  `TOP ONLY`, `BOTH SCREENS`, or `BOTTOM ONLY`.
- LITTLE and BIG values now turn red immediately when stock TOP-only still has
  the bottom hardware active and the cluster is near maximum under low load.
- The existing multi-sample `STOCK BUG CONFIRMED` warning remains in place for
  persistent pinning.

## What did not change

Display control, wake repair, timing, governors, frequency limits, and the
privileged bridge are unchanged.

## Artifact

- Version: `1.0.4` (`versionCode 41`)
- APK: `Jesty-Thor-Fix-1.0.4.apk`
- SHA-256: `215F7490722F0463653EE2DC4909932097BD93B4E61CA5502073AD9BD8A88A56`
- Signing certificate SHA-256:
  `727D4850779BED1E51018108E13BC399D4DA38CFC68F4F7504120AD5E2DAD6FC`

No functionality is locked behind donations.
