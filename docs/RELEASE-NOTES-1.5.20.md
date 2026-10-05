# Jesty Thor Fix v1.5.20

This release brings the v1.5.18 safety work, the v1.5.19 code reorganization
and the corrected daemon launch together. The exact signed APK passed the
scoped supervised Thor checks below.

- The boot daemon can recover from a failed CPU Fix handoff instead of holding
  display actions indefinitely. A stalled same-version boot phase can be
  replaced through the authenticated daemon identity checks.
- The app and daemon are split into smaller components. The Wake Guard can use
  a single unambiguous `SW_LID` device if the named Hall device is absent.
- With the dashboard open, the optional updater checks GitHub for a newer
  release, verifies the APK size, SHA-256, package, version and signing
  certificate, then asks Android to confirm installation. Test pre-releases
  require an explicit opt-in; the updater does not use the root daemon.
- The v1.5.19 candidate failed to launch its replacement daemon on the Thor.
  v1.5.20 shortens and bounds that launch command. Its signed in-place upgrade
  succeeded without clearing app data or restarting Android.

**Observed on the Thor:** BOTH↔TOP, TOP sleep/wake, a TOP cold boot with one
expected Android UI/display restart, and a closed-lid sleep plus one controlled
false wake passed. The final state was BOTH, both screens normal, CPU Fix active
and Wake Guard OFF. The owner saw no green flash, wrong panel or extra boot
cycle. The exact signed APK passed the host suites and v1/v2/v3 signature
checks. See the [validation diary](VALIDATION-1.5.20-PENDING.md).

**Limits:** the same early `no suitable EGLConfig found` SurfaceFlinger abort
seen on older versions still occurred before the app started. The in-app updater
has not installed a newer release on this Thor and its end-to-end test is
deferred by owner decision; adversarial helper recovery
was tested on the host, not provoked on the device. A pre-compositor property
write and an earlier CPU Fix restart remain unimplemented investigations.

APK: `Jesty-Thor-Fix-1.5.20.apk` (versionCode 69)

APK SHA-256: `AC4EC453863935F8ECA674CE5F6F2B0418C1AEFEC081987B247AE7E6A10C79C6`

Signing certificate SHA-256: `727D4850779BED1E51018108E13BC399D4DA38CFC68F4F7504120AD5E2DAD6FC`
