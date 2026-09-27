# Changelog

## 1.1.0

- Added a separate, persistent **AYN Dashboard CPU Fix** for the dual-screen
  clock-pinning behavior reproduced with AYN Dashboard open.
- Added an explicit confirmation before the one-time display-compositor restart
  used to enable or disable that fix. The warning explains that both displays
  and USB can briefly reset and that the effect may look like a second reboot.
- Reapplies the saved Dashboard CPU Fix choice once during a normal boot; merely
  opening Jesty Thor Fix never restarts the display.
- Restarts the privileged daemon after the display reset so mode observation
  and true-off wake repair remain active when Android's display services return.
- Added DRM-backed lower-display telemetry so the displayed mode and background
  follow the physical CRTC state instead of relying only on AYN's logical mode.
- Redesigned the UI around two equal feature toggles and one consolidated live
  status/check panel with larger CPU readings.
- Expanded clock-pinning detection to BOTH/dual-screen mode while keeping all
  existing true-off wake repair and display-control timing unchanged.

## 1.0.4

- Moved the live fix/native status beside the header lockup and aligned it to
  the right edge of the control column.
- Replaced the dense Display & Service block with a clear display-mode label:
  TOP ONLY, BOTH SCREENS, or BOTTOM ONLY.
- Highlighted LITTLE and BIG values immediately in red when stock TOP-only has
  the bottom hardware active and a cluster is near maximum under low load.
- Kept the multi-sample confirmed bug warning and all control/wake behavior
  unchanged.

## 1.0.3

- Returned the Support and GitHub actions to the top-right corner.
- Moved the open-source badge to the bottom-right and reordered its copy so
  the support line appears above the amber Jesty message.
- Returned the header lockup to left alignment within the control panel.
- Kept all display-control, CPU/DRM check, wake-repair, and layout behavior
  unchanged from 1.0.2.

## 1.0.2

- Restored the full `JESTY APPS ARE FREE & OPEN SOURCE` message in amber at
  the top-right, with Support and GitHub actions grouped at the bottom-right.
- Centered the Jesty Thor Fix lockup within the left control panel, tightened
  the telemetry layout, and removed the duplicated top-screen status line.
- Kept the 1.0.1 CPU/DRM check and all display-control behavior unchanged.
- Bumped the visual-only patch to versionCode 39 / versionName 1.0.2.

## 1.0.1

- Made the stock TOP-only warning explicit: a black lower panel can still have
  active display hardware and keep LITTLE/BIG cores pinned.
- Extended **Bottom screen & CPU check** to verify both DRM state and current
  LITTLE/BIG frequencies in one tap.
- Preserved the continuous low-load clock-lock detector and made its confirmed
  warning visible as `STOCK BUG CONFIRMED`.
- Kept display control, wake repair, timing, and privileged bridge behavior
  unchanged from 1.0.0.
- Bumped the patch update to versionCode 38 / versionName 1.0.1.

## 1.0.0

- Promoted the tested Thor fix to the first stable public release.
- Added the final transparent Jesty Thor Fix lockup to the app and README.
- Replaced both dashboard states with the approved AYN Thor ON/OFF artwork.
- Regenerated the transition loop from those exact states.
- Kept the display-control, wake-repair, daemon, clock monitoring, and
  privileged bridge behavior unchanged from the validated 0.33 line.
- Bumped the public update to versionCode 37 / versionName 1.0.0.

## 0.33

- Replaced the separately assembled header with the approved transparent
  Jesty Thor Fix project lockup in both the app and repository landing page.
- Consolidated LITTLE, BIG, and PRIME readings into one CPU speeds panel.
- Aligned dashboard panels and active-state colors with the Retroid app.
- Moved the lower-screen hardware check beside the main fix control and
  replaced scanout/CRTC-first wording with a user-friendly result.
- Kept the underlying daemon protocol, wake-repair timing, and clock-lock
  detection unchanged.
- Added the compact Support Jesty / Star on GitHub footer.
- Updated the provisional ON/OFF dashboard artwork supplied by the maintainer.
- Bumped the public update to versionCode 36 / versionName 0.33.

## 0.32

- Integrated final maintainer-supplied ON/OFF backgrounds and the new Jesty
  header wordmark.
- The dashboard background follows confirmed lower-display power and the ON
  loop includes brief lower-screen flicker frames.
- Added the landscape dashboard and persistent true-off/native-mode control.
- Added LITTLE, BIG, PRIME, load, daemon, display, and wake-repair telemetry.
- Added the sustained low-load clock-lock warning.
- Added one-shot DRM scanout verification.
- Added lower-screen-aware dashboard artwork and animation behavior.
- Preserved the stable 700 ms wake repair with a minimum 400 ms delay after Display 4 ON.
- Preserved immediate manual TOP/BOTH transitions and final-state reconciliation.

## 0.31.1

- Invalidated stale repair deadlines across rapid wake/sleep/wake sequences.
- Validated twenty mixed wake cycles and a real screen-timeout wake.

## 0.31

- Moved wake repair out of the early Android wake pipeline.
- Retained the display callback as a late confirmation and reconciliation signal.
