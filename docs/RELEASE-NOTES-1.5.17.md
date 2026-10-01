# Jesty Thor Fix v1.5.17 — testing pre-release

This build prepares a lower-overhead boot gate and adds timing evidence for
the Android UI restart. It does **not** remove the CPU Fix compositor restart
or shorten the 10-second and five-second safety graces, three matching CRTC
samples, eight-second helper floor or 60-second timeout.

- Short-lived commands use a bounded five-millisecond exit poll. Gate samples
  start at a 500-millisecond cadence
  and the grace ends only after a fresh, valid sample.
- Both display CRTCs come from one DRM state read; malformed or neighboring
  CRTC blocks stay unknown. This reduces debugfs reads in the boot gate.
- The helper and successor trace new Android service/PID and boot-animation
  signals. That observer samples once per second and does not gate actions.
- The helper tolerates a daemon that exits during the identity/kill check,
  while still aborting for a live identity mismatch. The exported boot
  receiver ignores actions other than `BOOT_COMPLETED`.
- Local boot traces rotate at 256 KiB, retaining one previous file.

**Validation:** The host boot/lid and dashboard suites passed. The full
Android APK build, alignment and v1/v2/v3 signing checks passed with the
established certificate. This exact APK has **not** been installed or tested
on the Thor. A faster boot, correct second-boot trace and physical display
behavior are pending supervised validation; the estimated ~2-second gain is
not a measured result. Keep [v1.5.16](https://github.com/JestyLabs/Jesty-Thor-Fix/releases/tag/v1.5.16)
as the stable download.

[Validation plan](https://github.com/JestyLabs/Jesty-Thor-Fix/blob/v1.5.17/docs/VALIDATION-1.5.17-PENDING.md) ·
[Code review](https://github.com/JestyLabs/Jesty-Thor-Fix/blob/v1.5.17/docs/CODE-REVIEW-1.5.17.md)

APK: `Jesty-Thor-Fix-1.5.17.apk` (versionCode 66)

APK SHA-256: `6E9BE2F4076FAB75753CD742D64D5027CFBF8C07275EF61119E67CFE93EDC550`

Signing certificate SHA-256: `727D4850779BED1E51018108E13BC399D4DA38CFC68F4F7504120AD5E2DAD6FC`
