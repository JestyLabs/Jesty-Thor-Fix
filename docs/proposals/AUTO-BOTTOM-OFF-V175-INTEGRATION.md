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
- `scripts/test-bottom-idle-runtime-host.py --negative-controls`: compiles the **real** `BottomIdleRuntime`, policy, restore queue and touch monitor together with the production coordinator. Only the Looper/input boundary, the Settings mode read and the vendor panel call are host doubles. It covers enable/attach on display 4, silence and other-display touches, an exact deadline, the watcher holding an owned OFF, display-on ownership, tap restore with re-arm, BOTH/TOP/BOTH monitor lifecycle, back-off deferral and disable-while-dark restore. Three compiled controls must fail at their own assertions.

## Review corrections (offline)

- **Re-arm after restore.** A tap delivers DOWN and UP while the restore is still queued, so neither touch could arm the deadline. After a confirmed restore the runtime now re-arms from the restoring touch; previously a single tap left the lower panel lit until another touch.
- **Atomic display-on ownership.** The display callback used two separate checks (owned OFF?, then reset). One policy operation now checks ownership and resets touch evidence.
- **Quick Settings.** With True Bottom Screen Off disabled the tile shows "Needs True Off" instead of an unqualified ACTIVE state; it stays clickable so the option can still be turned off.
- **Startup reconciliation.** It no longer waits up to ~70 s on held phases that only end through replacement or reboot (`HANDOFF_FAILED`, `BOOT_SAFETY_TIMEOUT`).

Open product decisions, not changed here: inactivity counts lower-screen touches only (game controls and upper-screen input do not), so a passive second game screen can idle off; a live handover is refused while the lower panel is idle-off because the predecessor gate requires an active lower CRTC; every timeout and restore adds lower-panel power transitions in BOTH, the path involved in the unresolved vendor stall.

## Explicit release stop

**No physical Thor validation.** Before merging or publishing, supervise on-device: verify passive input routing, input delivery after CRTC OFF, touch restore, repeated sleep/wake, mode transitions, boot hold, closed lid, stalled compositor, duplicate Quick Settings clicks, font scaling, signed upgrade/handover, and passive CPU/battery effect. A physical recovery plan is mandatory. A green emulator is NOT this proof.

NO SIGNING / NO RELEASE / NO DEVICE TEST.