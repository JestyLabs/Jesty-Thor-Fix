# Single consolidated future Thor session — preparation only

> **Historical pre-session plan:** NOT RUN states below are preserved from 2026-10-13. Current execution is recorded in [the 2026-10-16 physical closure](PR67-PHYSICAL-VALIDATION-2026-10-16.md): 15 PASS / 1 historical FAIL in T00–T15, plus separate A1 PASS. Exclusions remain unexecuted; this plan is not authorization for another test.

All cases below are NOT RUN in this workstream. No ADB, install, restart, screen command or capture is authorized by this document. Owner controls each operation.

Current entry decisions and the proposed data-preserving activation/recovery path
are recorded in the [2026-10-14 closure checkpoint](ENGINEERING-HARDENING-CLOSURE-2026-10-14.md).
That checkpoint supersedes the original offline preparation status below;
physical scenarios remain NOT RUN.

## Entry requirements

- [x] Reproduce and resolve the identified updater ownership/lifecycle bugs with real AppUpdater/receiver host fixtures and the controlled Android installer suite; current runtime/test baseline `e190544` has completed build/Android CI. This is offline evidence, not a physical session.
- [ ] Require full unsigned build, CI and Android emulator validation on the final candidate identity/head. Analyses succeeded at `e190544`; the aggregate CodeQL finding disposition remains a separate open gate. A metadata bump needs new CI.
- [ ] Independently review all privileged IPC/client changes and source-to-build provenance.
- [ ] Identify exact signed candidate and forward-recovery source/build/version/code/provenance; preserve the stable baseline, but do not assume a code76 downgrade is supported. Review activation by controlled reboot separately from live daemon replacement.
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
