# Release integrity

## 1.5.17 testing pre-release (Thor validation pending)

- Package: `com.thor.displaypowertest`
- Version code/name: `66` / `1.5.17`
- APK: `Jesty-Thor-Fix-1.5.17.apk`
- Local signed APK SHA-256: `6E9BE2F4076FAB75753CD742D64D5027CFBF8C07275EF61119E67CFE93EDC550`
- Signing certificate SHA-256: `727D4850779BED1E51018108E13BC399D4DA38CFC68F4F7504120AD5E2DAD6FC`
- Alignment and v1/v2/v3 signatures passed. Host suites passed after the
  patch and local corrections. The exact APK has **not** been installed or
  boot-tested on the Thor. The GitHub pre-release asset was downloaded after
  upload and matched the local SHA-256 and 9,151,581-byte size.

v1.5.16 remains Latest stable. No APK, signing material or device log is
committed to Git.


## Stable 1.5.16 release

- Package: `com.thor.displaypowertest`
- Version code/name: `65` / `1.5.16`
- APK: `Jesty-Thor-Fix-1.5.16.apk`
- Local signed APK SHA-256: `4C697E4AD43D689BAF14ECD8184F827AC1E3E86BFD4CA198B18334104B6050F5`
- Signing certificate SHA-256: `727D4850779BED1E51018108E13BC399D4DA38CFC68F4F7504120AD5E2DAD6FC`
- Alignment and v1/v2/v3 signatures passed during the signed build.
- The exact APK was installed over v1.5.15 without clearing data and passed
  one supervised cold boot on the Thor in BOTH. It reached `BOOT READY` at
  65.479 s with both CRTCs active; the user reported no green flash or
  artifact. The GitHub release asset was downloaded after publication:
  9,147,485 bytes; SHA-256 matched the local signed APK above. GitHub marks
  v1.5.16 as Latest and not a pre-release.

The supervised checks recorded in `VALIDATION-1.5.16.md` passed for
this exact signed APK. One boot is evidence for this device and configuration,
not a repeatability guarantee. No key, password, APK, or device log is stored
in Git.


## Previously validated 0.32 APK

- File: `Jesty-Thor-Fix-0.32.apk`
- SHA-256: `786AB258A42EA4CA2FD01EF7C5193D10066EA4E7448D2A190529EAE3085DED91`
- Signing certificate SHA-256: `727D4850779BED1E51018108E13BC399D4DA38CFC68F4F7504120AD5E2DAD6FC`
- Verified signing schemes: APK Signature Scheme v1, v2, and v3.

Future update APKs must use the same certificate to install over 0.31/0.32 without uninstalling.

This hash identifies the validated rollback build before the final public
artwork refresh. It is not the hash of the new unsigned candidate below.

## Public-source build check

The sanitized public-source candidate produced an aligned unsigned APK successfully with apktool 3.0.3, JDK 17, Android platform 34, and Build Tools 35.0.0.

Before the artwork refresh, the following APK entries matched the validated
signed 0.32 APK byte for byte by SHA-256:

- `AndroidManifest.xml`
- `classes.dex`
- `res/drawable-nodpi/jesty_logo.png`
- `res/drawable-nodpi/jesty_thor_background.png`
- `res/drawable-nodpi/jesty_thor_background_off.png`
- `res/drawable-nodpi/jesty_thor_wordmark.png`
- `res/raw/jesty_thor_background_loop.mp4`

The unsigned container itself has a different overall hash because it has no official signature block.

## Current 0.33 signed candidate

- Package: `com.thor.displaypowertest`
- Version code: `36`
- Version name: `0.33`
- APK: `Jesty-Thor-Fix-0.33.apk`
- SHA-256: `2EBF38A64C5B624A9487F8BB5E56D8C2FCA857BB129A98EF7C680DC0BDDEBE5A`
- Signing certificate SHA-256: `727D4850779BED1E51018108E13BC399D4DA38CFC68F4F7504120AD5E2DAD6FC`
- Verified signing schemes: APK Signature Scheme v1, v2, and v3.

This build preserves the display-control implementation and replaces the public
UI artwork/header. It is signed with the same certificate as the validated
0.31/0.32 line. At initial pre-release publication, this exact 0.33 artifact has
not yet completed the physical-Thor checklist. Results are added here after the
post-release validation rather than being claimed in advance.

The 0.32 final-art APK was initially published before its exact artifact had
been exercised on a connected Thor. It was installed and tested afterwards.
That chronology is why the project continues to use pre-release labels until a
new candidate completes the current validation checklist.

## Stable 1.0.0 release

- Package: `com.thor.displaypowertest`
- Version code: `37`
- Version name: `1.0.0`
- APK: `Jesty-Thor-Fix-1.0.0.apk`
- SHA-256: `1585BAEC43DD899270F522276D8F4752F5DE45E1B58D3B52EB632CD82652A840`
- Signing certificate SHA-256: `727D4850779BED1E51018108E13BC399D4DA38CFC68F4F7504120AD5E2DAD6FC`
- Verified signing schemes: APK Signature Scheme v1, v2, and v3.

The exact signed APK was installed in place on the tested physical AYN Thor.
TOP with the fix enabled ended at `power=0`, CRTC 181 active, and CRTC 243
inactive. BOTH restored `power=1` and both CRTCs. Removing the app from Recents
did not change the true-off state; the following sleep/wake cycle repaired to
`OFF_OK`. A normal reboot with the fix enabled restored TOP true-off before the
dashboard was opened.

## Stable 1.0.1 patch

- Package: `com.thor.displaypowertest`
- Version code: `38`
- Version name: `1.0.1`
- APK: `Jesty-Thor-Fix-1.0.1.apk`
- SHA-256: `445704EC32E864D617CC6594AC4CC4A3DDF54E1D03EC81454990CED884838913`
- Signing certificate SHA-256: `727D4850779BED1E51018108E13BC399D4DA38CFC68F4F7504120AD5E2DAD6FC`
- Verified signing schemes: APK Signature Scheme v1, v2, and v3.

The signed patch was installed in place and checked in native TOP and Jesty
true-off states. It changes only user-facing status/check behavior and top
action placement; display transitions and wake-repair timing are unchanged.

## Stable 1.0.2 visual patch

- Package: `com.thor.displaypowertest`
- Version code: `39`
- Version name: `1.0.2`
- APK: `Jesty-Thor-Fix-1.0.2.apk`
- SHA-256: `2B646A4F2310A9016DB3D9BFBDBAED753D02726950ED637FA018938276585BA2`
- Signing certificate SHA-256: `727D4850779BED1E51018108E13BC399D4DA38CFC68F4F7504120AD5E2DAD6FC`
- Verified signing schemes: APK Signature Scheme v1, v2, and v3.

This visual-only patch was installed in place and its package identity was
confirmed on the physical AYN Thor. The full hardware regression matrix was
not repeated for this layout-only update. Display control, CPU/DRM checks, and
wake-repair behavior are unchanged from 1.0.1.

## Stable 1.0.3 visual patch

- Package: `com.thor.displaypowertest`
- Version code: `40`
- Version name: `1.0.3`
- APK: `Jesty-Thor-Fix-1.0.3.apk`
- SHA-256: `957A1961EC5A0AD3EED5B98049D7FB3DC83DC74B557D07096BD7C4D421AB7EF1`
- Signing certificate SHA-256: `727D4850779BED1E51018108E13BC399D4DA38CFC68F4F7504120AD5E2DAD6FC`
- Verified signing schemes: APK Signature Scheme v1, v2, and v3.

This patch only reorders the two optional corner elements. It does not change
display control, CPU/DRM checks, wake repair, or service behavior.

## Stable 1.0.4 dashboard patch

- Package: `com.thor.displaypowertest`
- Version code: `41`
- Version name: `1.0.4`
- APK: `Jesty-Thor-Fix-1.0.4.apk`
- SHA-256: `215F7490722F0463653EE2DC4909932097BD93B4E61CA5502073AD9BD8A88A56`
- Signing certificate SHA-256: `727D4850779BED1E51018108E13BC399D4DA38CFC68F4F7504120AD5E2DAD6FC`
- Verified signing schemes: APK Signature Scheme v1, v2, and v3.

The signed APK was installed in place on the physical AYN Thor. Native
TOP-only showed the bottom artwork active, LITTLE/BIG at their maximum values
in red, and the confirmed pinned-core warning. Re-enabling the fix restored
`fix=1`, `mode=1`, `power=0`, and `OFF_OK`. This patch changes dashboard
presentation and highlighting only.

## Stable 1.1.0 two-fix release

- Package: `com.thor.displaypowertest`
- Version code: `42`
- Version name: `1.1.0`
- APK: `Jesty-Thor-Fix-1.1.0.apk`
- SHA-256: `10240A143C2B3C73333F089ACD45D563D476E571FA93EDEA9F765DA421D0EE77`
- Signing certificate SHA-256: `727D4850779BED1E51018108E13BC399D4DA38CFC68F4F7504120AD5E2DAD6FC`
- Verified signing schemes: APK Signature Scheme v1, v2, and v3.

The exact signed APK was installed in place and exercised on the physical Thor.
It adds the independent AYN Dashboard CPU Fix while preserving the existing
true-off path. The display/USB reset used to change the new property did not
change the Android boot ID, and the privileged daemon restarted itself after
the reset so TOP/BOTH observation and wake repair remained live. A subsequent
real Android reboot changed the boot ID and restored both saved fixes.

## Stable 1.1.1 restart hotfix

- Package: `com.thor.displaypowertest`
- Version code: `43`
- Version name: `1.1.1`
- APK: `Jesty-Thor-Fix-1.1.1.apk`
- SHA-256: `C72019B4AD1C3AB0717AC5B963C206960754EDDC362D55D491EACAE8DA04B82F`
- Signing certificate SHA-256: `727D4850779BED1E51018108E13BC399D4DA38CFC68F4F7504120AD5E2DAD6FC`
- Verified signing schemes: APK Signature Scheme v1, v2, and v3.

The exact signed APK was installed in place on the physical Thor. Both CPU Fix
OFF and ON completed their required Android framework restart without the
device entering suspend. A subsequent normal reboot restored the saved CPU Fix
setting and completed its expected framework restart without a suspend delay.
The timed transition wake-lock was inactive after recovery.

## Stable 1.2.0 state and telemetry update

- Package: `com.thor.displaypowertest`
- Version code: `44`
- Version name: `1.2.0`
- APK: `Jesty-Thor-Fix-1.2.0.apk`
- SHA-256: `11CC0973965B344B216C7C8509564139BF3030C97CC9F6ADA9797A2DCD44DFD5`
- Signing certificate SHA-256: `727D4850779BED1E51018108E13BC399D4DA38CFC68F4F7504120AD5E2DAD6FC`
- Verified signing schemes: APK Signature Scheme v1, v2, and v3.

The exact signed APK was installed in place on the physical Thor. It correctly
rendered BOTH, AYN fake-off, and Jesty true-off from live display hardware
state; prevented red stock-bug verdicts while the CPU Fix was active; and
restored both fixes after a real reboot and its expected framework restart.

## Stable 1.5.15 release

- Package: `com.thor.displaypowertest`
- Version code: `64`
- Version name: `1.5.15`
- APK: `Jesty-Thor-Fix-1.5.15.apk`
- APK SHA-256: `093B6AF26E86E072343988C04CBF03256005703D567177E99D308B9BADC71F2B`
- Signing certificate SHA-256: `727D4850779BED1E51018108E13BC399D4DA38CFC68F4F7504120AD5E2DAD6FC`
- Verified signing schemes: APK Signature Scheme v1, v2, and v3.

The exact signed and aligned APK was installed on the physical Thor. A copy
pulled from the device matched this SHA-256 byte for byte. The supervised
in-place CPU Fix transition and a genuine cold boot of the final APK restored
property `1` with one compositor restart and one daemon. BOTH remained active
on both CRTCs; the user reported no green flash, artifact, or loop. AYN
Dashboard on the lower display left Jesty showing `CPU FIX ACTIVE · CLOCKS
NORMAL` on the upper display. The expected Android UI restart can look like a
second boot without changing the kernel boot ID. See
[`VALIDATION-1.5.0-PENDING.md`](VALIDATION-1.5.0-PENDING.md) for scoped physical,
host, and unavailable cases. The published download must be checked against
the SHA-256 above; publication alone does not verify it. The published
9,143,389-byte asset was downloaded and matched this SHA-256. The maintainer
promoted the same release and unchanged APK from pre-release to Latest stable.
v1.3.0 remains available in release history. BOTTOM ONLY and physical dock
use remain outside the tested scope.

## 1.4.2 testing pre-release

- Package: `com.thor.displaypowertest`
- Version code: `48`
- Version name: `1.4.2`
- APK: `Jesty-Thor-Fix-1.4.2.apk`
- SHA-256: `82B2C346543B0DD4F35DF5892DBB2EAFB8C418981EE1B73407492499FB891C16`
- Signing certificate SHA-256: `727D4850779BED1E51018108E13BC399D4DA38CFC68F4F7504120AD5E2DAD6FC`
- Verified signing schemes: APK Signature Scheme v1, v2, and v3.

The exact signed APK was installed in place without a reboot. The screenshot
`images/dashboard-true-off-v1.4.2.png` was captured from the top display and
visually checked: it shows the installed version suffix, TOP true-off and
automatic CPU status. The app's display/CPU/boot logic matches v1.4.1. The
full cold-boot and lid-guard matrix remains pending.

## 1.4.1 testing pre-release

- Package: `com.thor.displaypowertest`
- Version code: `47`
- Version name: `1.4.1`
- APK: `Jesty-Thor-Fix-1.4.1.apk`
- SHA-256: `E2D67F5A02CD1CB1BD117A19A2F7775BD4D2B8A5216422D1B8D1110BB47F8A96`
- Signing certificate SHA-256: `727D4850779BED1E51018108E13BC399D4DA38CFC68F4F7504120AD5E2DAD6FC`
- Verified signing schemes: APK Signature Scheme v1, v2, and v3.

The AYN physical-button TOP/BOTH cycle passed on the local test candidate with
the same watcher logic. Unit/static tests and clean signed build passed for
v1.4.1. This remains an opt-in testing pre-release; cold boots and the lid
guard matrix are pending. See `VALIDATION-1.4.1-PENDING.md`.

## 1.4.0 testing pre-release

- Package: `com.thor.displaypowertest`
- Version code: `46`
- Version name: `1.4.0`
- APK: `Jesty-Thor-Fix-1.4.0.apk`
- SHA-256: `C3B5D4927A131EF900F30ACBA250823D43A5952CF6D095B0E4866C8EAC939E14`
- Signing certificate SHA-256: `727D4850779BED1E51018108E13BC399D4DA38CFC68F4F7504120AD5E2DAD6FC`
- Verified signing schemes: APK Signature Scheme v1, v2, and v3.

The exact signed APK was installed in place and passed one user-observed clean
cold boot with both fixes enabled. This is a testing pre-release, not the
Latest stable release. The full physical matrix and lid guard validation remain
open; see `VALIDATION-1.4.0-PENDING.md`. The lid guard defaults to OFF.

## Stable 1.3.0 dashboard telemetry update

- Package: `com.thor.displaypowertest`
- Version code: `45`
- Version name: `1.3.0`
- APK: `Jesty-Thor-Fix-1.3.0.apk`
- SHA-256: `021AC43581E6999AABFB896B760834B4F1804EF43EFD63709058E36F5D9297AD`
- Signing certificate SHA-256: `727D4850779BED1E51018108E13BC399D4DA38CFC68F4F7504120AD5E2DAD6FC`
- Verified signing schemes: APK Signature Scheme v1, v2, and v3.

The exact signed APK was installed in place on the physical Thor. Focused live
checks confirmed stable BOTH, AYN black-screen, and Jesty true-off display
states, automatic CPU-clock diagnosis, and the external-power prompt. The
release keeps both fixes unchanged and replaces the old manual snapshot check
with a rolling kernel `time_in_state` diagnostic.
