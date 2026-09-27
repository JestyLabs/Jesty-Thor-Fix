# Release integrity

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
- SHA-256: `9566D549A743C0E2E18C958CD67C249B3C4F6835EE17D52BA454C1C3DDAAADA2`
- Signing certificate SHA-256: `727D4850779BED1E51018108E13BC399D4DA38CFC68F4F7504120AD5E2DAD6FC`
- Verified signing schemes: APK Signature Scheme v1, v2, and v3.

This patch only reorders the two optional corner elements. It does not change
display control, CPU/DRM checks, wake repair, or service behavior.
