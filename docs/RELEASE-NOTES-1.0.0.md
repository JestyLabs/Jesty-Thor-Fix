# Jesty Thor Fix 1.0.0

The first stable Jesty Thor Fix release.

## What it does

- Actually powers down the AYN Thor lower display in TOP-only mode.
- Restores the selected fix automatically after a normal reboot and wake.
- Lets you close the interface and remove it from Recents; the background
  service continues without keeping the dashboard open.
- Uses AYN's privileged bridge. The user does not need root, Magisk, Termux,
  or shell commands.

Android Settings → Apps → Jesty Thor Fix → **Force stop** is the explicit
exception: Android prevents the service from continuing until the app is
opened again.

## 1.0.0 refresh

- Final transparent Jesty Thor Fix lockup in the app and README.
- Approved Thor lower-display ON/OFF artwork and matching transition loop.
- No changes to governors, CPU frequencies, composer behavior, wake timing,
  or the hardware display-control implementation.

## Install

1. Download `Jesty-Thor-Fix-1.0.0.apk`.
2. Install it over 0.31, 0.32, or 0.33; it uses the same signing certificate.
3. Open the app and enable **True Bottom Display Fix**.

- Package: `com.thor.displaypowertest`
- Version: `1.0.0` (`versionCode 37`)

Jesty Thor Fix is free community software and is not affiliated with AYN.
No functionality is locked behind donations.

## Validation

The exact downloadable APK was installed in place on a physical AYN Thor.
TOP true-off, BOTH mode, the in-app hardware check, clearing Recents,
sleep/wake repair, and reboot persistence all passed. See
[`docs/DEVICE-VALIDATION.md`](DEVICE-VALIDATION.md) and
[`docs/RELEASE-INTEGRITY.md`](RELEASE-INTEGRITY.md) for the recorded results
and artifact hashes.
