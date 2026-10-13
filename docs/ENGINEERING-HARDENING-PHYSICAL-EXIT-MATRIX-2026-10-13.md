# Single consolidated future Thor session — preparation only

All cases below are NOT RUN in this workstream. No ADB, install, restart, screen command or capture is authorized by this document. Owner controls each operation.

## Entry requirements

- [ ] Reproduce and resolve both Codex updater bugs with production AppUpdater plus receiver on exact PR SHA, including stale consent after cancel and old callback deleting new download.
- [ ] Re-run clean Windows JDK 17 and full unsigned build, CI, CodeQL, Android 13 disposable-key emulator on ONE exact head.
- [ ] Independently review all privileged IPC/client changes and source-to-build provenance.
- [ ] Identify exact signed candidate SHA/versionCode/certificate, preserve stable fallback and private safe-stop/recovery steps.
- [ ] Obtain owner approval before any physical write, installation, restart, or additional active-lid reproduction.
- [ ] Record initial switches, display mode and battery/USB state in private log for restoration.

## Proposed order if and only if approved

| Phase | Scenario | Stop / success evidence |
| --- | --- | --- |
| 0 | Read-only installed package/certificate, source and daemon identity against published code76 | Any mismatch -> STOP without installation |
| 1 | Install in place with preferences retained; Android consent/denial/cancellation/retry and stale-session handling | Wrong session cleanup, unsigned/unknown candidate, changed settings -> STOP |
| 2 | Approved IPC identity/normal commands; no deliberate firmware crashes | Peer, availability, timeout or daemon instability -> STOP |
| 3 | BOTH / TOP ordinary display and CPU Fix/sleep/wake with Guard OFF | Wrong CRTC/power, extra compositor restart, stuck BOOT_HOLD -> STOP |
| 4 | Guard ON normal BOTH/TOP recovery | Observe results; active unintended closed-lid wake blocking is NOT a PASS unless actually triggered and proven |
| 5 | BOTTOM-only, dock/external or other firmware | SKIP unless separately authorized with safe recovery |
| 6 | Restore starting preferences and display mode, disable test-only diagnostics | Must leave device in owner's selected state |

Retain previously accepted RC2/code76 baselines instead of repeating unaffected work. Do not invent ad hoc root service kills, data clearing, firmware downgrades or emergency commands. Keep raw logs private and report PASS/FAIL/NOT RUN with the exact candidate SHA. This one session is intentionally the **last** activity after every feasible offline gate.
