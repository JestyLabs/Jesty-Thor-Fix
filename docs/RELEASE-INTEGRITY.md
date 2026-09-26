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
