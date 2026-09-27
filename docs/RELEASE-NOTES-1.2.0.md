# Jesty Thor Fix 1.2.0

This release makes the two fixes easier to understand and the live diagnosis
harder to misread.

## What changed

- The dashboard now distinguishes three physical display states:
  **BOTH SCREENS**, **TOP ONLY · AYN FAKE OFF**, and
  **TOP ONLY · JESTY TRUE OFF**.
- Fake-off and true-off have separate artwork on the lower display of the
  background device.
- Live USB input and an estimated system-power value are shown in the display
  panel.
- Clock-pinning diagnosis now waits for a stable state and sustained samples.
- The app never reports the stock pinning bug as confirmed while the Dashboard
  CPU Fix is active.
- CPU Fix restart prompts are shorter and explicitly warn that open apps close.

## Important restart behavior

Changing **AYN Dashboard CPU Fix** restarts the Android framework once. When
the fix is enabled, Android also performs that restart once during each normal
boot. The display can remain black for several seconds and the boot may appear
to have two phases; this is expected.

On the tested Thor, the focused OFF and ON transitions returned in about 28
and 26 seconds. A full reboot with the fix enabled restored the app and daemon
after the second phase in about 60 seconds. These are measurements from one
device, not universal timing guarantees.

## Physical-device validation

- Package: `com.thor.displaypowertest`
- Version: `1.2.0` (`versionCode 44`)
- CPU Fix OFF and ON each completed one framework restart without changing the
  Android boot ID.
- A normal reboot changed the boot ID, restored both saved fixes, and completed
  the expected framework restart.
- BOTH, fake-off, and true-off each selected the correct state label and
  background artwork.
- CPU Fix active remained green through settled sampling and never produced a
  false red stock-bug verdict.
- The privileged daemon recovered after every transition.

## Integrity

```text
11CC0973965B344B216C7C8509564139BF3030C97CC9F6ADA9797A2DCD44DFD5  Jesty-Thor-Fix-1.2.0.apk
```

Signing certificate SHA-256:

```text
727D4850779BED1E51018108E13BC399D4DA38CFC68F4F7504120AD5E2DAD6FC
```

Verified signing schemes: APK Signature Scheme v1, v2, and v3.
