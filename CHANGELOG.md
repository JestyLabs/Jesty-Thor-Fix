# Changelog

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
