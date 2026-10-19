# Auto Bottom Off — draft implementation and validation boundary

This PR now contains a **draft implementation**, superseding the backlog-only
scope above. Nothing is merged, signed, released or physically validated.

## Behaviour

- Disabled by default; 10 discrete timeout settings: 1, 2, 3, 5, 10, 15, 20,
  30, 45, 60 minutes. Slider commits only on release.
- One Android 13 Quick Settings tile, added manually from the panel's Edit UI,
  toggles the same saved preference as the dashboard switch.
- In BOTH (mode 0), the privileged daemon registers a passive gesture input
  monitor for logical display 4. A *fresh positive bottom-touch observation*
  is mandatory: a missing or unverified monitor can never trigger OFF.
- The input monitor is attached only while BOTH and True Bottom Screen Off are active; TOP, BOTTOM-only and stock display behaviour leave the Looper dormant. If disabled, the watcher uses a cheap volatile flag and Q returns a shared constant with no new threads, monitors or scheduled timeouts.
- One delayed message is scheduled per last input event; no periodic read,
  framebuffer capture, Accessibility Service or input injection.
- Timeout asks DisplayActionCoordinator (the existing sole hardware-action
  coordinator) to turn the lower display off; touch requests restore. Screen
  mode edges, display-on events, disabled True Bottom Screen Off, user changes
  and boot hold take priority. Native BOTH restoration is suppressed only for
  a confirmed or in-flight **owned** idle-off.
- The daemon reports `idle_enabled`, `idle_minutes`, `idle_input`, and
  `idle_off` in the existing Q sample; visible UI reuses that sample.

## Hardware unknowns / release blockers

1. Confirm the Thor firmware exposes `InputManager.monitorGestureInput` for
   logical display 4 to the root `app_process` daemon, without consuming
   input or intercepting other displays. Missing input fails closed.
2. Confirm input events still reach the listener after the lower display's
   SurfaceControl power mode becomes OFF. If not, use the Quick Settings OFF
   tile or switch modes to recover; do not release with touch-to-restore claims.
3. Verify video/reading use: inactivity means **no touches**, not static
   framebuffer analysis. Long passive viewing can trigger OFF; document and
   reconsider guard behaviour before release.
4. Validate AYN firmware, lid closed/open, sleep/wake, external display,
   TOP/BOTTOM-only, unresponsive daemon, update/handover, repeated tile taps.
5. Run Java host, Android-34 compile, Android-13 emulator UI tests, and
   physical Thor tests including CPU and battery cost versus baseline.

The feature MUST remain in Draft until physical signoff; emulator tests cannot
validate vendor input routing or display wake. The feature is opt-in; no new
app-permission grant, instrumentation or background polling is used.
