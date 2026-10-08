# Jesty Thor Fix v1.7.0

Lower background watcher work, with the existing display and CPU Fix safeguards.

- Lower background watcher CPU work while retaining event-assisted transitions
  and safety polling. Research hardware A/B measured about 89% less daemon CPU
  in awake BOTH and TOP on AYN Thor firmware .377.
- Preserve display events around watcher waits and DRM snapshots.
- Keep completed CPU Fix startup proof intact during a guarded app update.
- No battery-life, gameplay or frame-time improvement is claimed.

The exact signed release APK passed guarded update, BOTH/TOP switching,
two dashboard-closed TOP power-button wake repairs with direct OFF_OK/RUNNING
results, and one normal supervised TOP reboot with confirmed CPU Fix proof.
The 10-minute awake TOP observation was on the experimental predecessor.
The existing early EGL startup limitation remains documented in the
[validation report](https://github.com/JestyLabs/Jesty-Thor-Fix/blob/main/docs/VALIDATION-1.7.0-PENDING.md).

APK: `Jesty-Thor-Fix-1.7.0.apk`, version code 71, signed with the established key.
Install over the existing app to preserve settings; no uninstall is needed.

SHA-256: `0c237de7bb730e90cf1beb76118d21bd53a090de3dcf4ed605a7006f7c4f31c8`.
