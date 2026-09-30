# Jesty Thor Fix 1.4.2 — testing pre-release

This is a small visual update to the v1.4.1 testing build. The dashboard
subtitle now shows the version of the installed app, for example
`Display and CPU fixes for AYN Thor (v1.4.2)`. The version is read from the
installed package, so it will update automatically in future builds. The
README now shows a screenshot captured from the v1.4.2 app on the maintainer's
Thor in TOP-only true hardware-off mode.

There are no changes to display control, the CPU fix, lid guard, boot timing,
daemon protocol, permissions or backgrounds. The exact signed APK was
installed in place and its version and dashboard subtitle were checked; no
reboot was performed for this visual update. Boot/lid model and dashboard
tests passed, as did the clean build, alignment and signature checks.

**This remains a testing pre-release.** The 20-boot configuration matrix,
closed-lid false-wake and dock cases, and further sleep/wake checks are still
pending. The Closed-Lid Wake Guard is OFF by default. Changing the AYN
Dashboard CPU Fix still causes one expected Android UI/display restart and
closes open apps; enabling it also reapplies that restart during normal boot.

APK SHA-256: `82B2C346543B0DD4F35DF5892DBB2EAFB8C418981EE1B73407492499FB891C16`
Signing certificate SHA-256: `727D4850779BED1E51018108E13BC399D4DA38CFC68F4F7504120AD5E2DAD6FC`

Validation: [v1.4.1 matrix](https://github.com/JestyLabs/Jesty-Thor-Fix/blob/v1.4.2/docs/VALIDATION-1.4.1-PENDING.md).
