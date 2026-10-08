# Jesty Thor Fix v1.7.0

Validated local production candidate — not released.

- Lower background watcher CPU work while retaining event-assisted transitions
  and safety polling. Research hardware A/B measured about 89% less daemon CPU
  in awake BOTH and TOP on AYN Thor firmware .377.
- Preserve display events around watcher waits and DRM snapshots.
- Keep completed CPU Fix startup proof intact during a guarded app update.
- No battery-life, gameplay or frame-time improvement is claimed.

The exact local signed candidate passed guarded update, BOTH/TOP switching,
two dashboard-closed TOP power-button wake repairs with direct OFF_OK/RUNNING
results, and one normal supervised TOP reboot with confirmed CPU Fix proof.
The 10-minute awake TOP observation was on the experimental predecessor.
See [candidate results](VALIDATION-1.7.0-PENDING.md), including the already
documented early EGL limitation and the exact-artifact publication boundary.
