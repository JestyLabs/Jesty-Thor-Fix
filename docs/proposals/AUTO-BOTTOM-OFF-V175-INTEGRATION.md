# Auto Bottom Off — integration on v1.7.5 (offline candidate)

This branch integrates the earlier #117 timer into the **current main v1.7.5/code85** coordinator. The merge-parent preserves existing display stall containment, observer diagnostics, build identity and the native updater/retirement tests. It is **DRAFT** and not a published feature.

## Hardware/policy contracts

- **OFF by default.** Disabled snapshots are constant and the daemon opens no idle input monitor, Looper or timer when disabled.
- The enabled watcher attaches to logical display 4 only in BOTH with True Bottom Screen Off enabled. Missing or unverified input **never** starts a deadline.
- One observed lower touch starts a bounded timeout; on timeout, the guarded coordinator verifies the mode/boot/watchdog and reads final CRTC. Vendor calls run outside the touch event Looper.
- The v1.7.5 `lowerOnBackoff` containment forbids automatic OFF and automatic restore when a display power operation has been classified as stalled; it is **not reset** by this feature.
- Delayed restore requests have **exact policy generation leases**. A new touch, timer, mode edge, reconfiguration, disable/re-enable, and a newer pending OFF invalidate older restore callbacks.
- Policy/host fixtures cannot prove power mode transitions, input delivery while lower panel OFF or vendor-compositor reliability.

## Regressions / validation tiers

- `scripts/test-dashboard.ps1`: compiles the real `BottomIdlePolicy` and `BottomIdleSettings`, with negative controls for fresh-touch proof, held gestures, stale OFF attempts and restore leases.
- `scripts/test-display-coordinator-host.ps1`: compiles the production v1.7.5 coordinator with inert vendor boundaries. New `BottomIdleCoordinatorHostTest` checks old/invalid OFF, boot hold, back-off admission, stale queued restores, CRTC-confirmed OFF, a 6-second simulated compositor stall and current-lease restore. Existing coordinator tests and mutations must still pass.
- `scripts/test-display-backoff-host.py` and the emulator's display-wake harness retain the existing v1.7.5 back-off tests. They exercise synthetic platform boundaries.
- `scripts/test-retirement-activation-host.py`: adds `BottomIdleSettings` to the real activation fixture source list without reverting the v1.7.5 native retirement harness.

## Explicit release stop

**No physical Thor validation.** Before merging or publishing, supervise on-device: verify passive input routing, input delivery after CRTC OFF, touch restore, repeated sleep/wake, mode transitions, boot hold, closed lid, stalled compositor, duplicate Quick Settings clicks, font scaling, signed upgrade/handover, and passive CPU/battery effect. A physical recovery plan is mandatory. A green emulator is NOT this proof.

NO SIGNING / NO RELEASE / NO DEVICE TEST.