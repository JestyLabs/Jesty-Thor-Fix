# Jesty Thor Fix v1.7.0

Production candidate — final hardware validation pending; not released.

- Lower background watcher CPU work while retaining event-assisted transitions
  and safety polling. Research hardware A/B measured about 89% less daemon CPU
  in awake BOTH and TOP on AYN Thor firmware .377.
- Preserve display events around watcher waits and DRM snapshots.
- Keep completed CPU Fix startup proof intact during a guarded app update.
- No battery-life, gameplay or frame-time improvement is claimed.

The experimental predecessor passed BOTH/TOP switching, two dashboard-closed
TOP power-button wake repairs and a 10-minute awake TOP observation. Those tests
do not validate this exact production candidate. See
[the candidate checklist](VALIDATION-1.7.0-PENDING.md) before publication.
