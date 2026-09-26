# Jesty Thor Fix 0.32

## Highlights

- New landscape dashboard with persistent true-off/native-mode control.
- Live LITTLE, BIG, PRIME, display, daemon, load, and wake-repair telemetry.
- Sustained low-load clock-lock warning.
- One-shot DRM scanout verification.
- Lower-screen-aware dashboard artwork and animation.
- Extended local protocol: `E`, `N`, `Q`, and `V`, preserving legacy `0` and `1`.

## Stable behavior retained

- Wake repair remains 700 ms after wake and no earlier than 400 ms after Display 4 ON.
- Manual TOP/BOTH transitions remain immediate.
- The root daemon remains independent of the dashboard Activity.
- Closing the app or removing it from recents does not stop the fix.
- The saved fix ON/OFF state is reconciled after a normal reboot.

Android Settings → Force stop is the exception: it may prevent automatic restart until the app is opened again.

## Safety

- CPU clock and load information is read-only.
- The app never changes governors or frequencies.
- The old SystemLoadFix path is not present.
- Signing material, passwords, device serials, and raw logs are excluded from the public source.

## AI assistance

Code, documentation, and visual assets were created or refined with AI assistance and reviewed by the maintainer. Runtime claims are based on real-device validation; see `AI_DISCLOSURE.md`.
