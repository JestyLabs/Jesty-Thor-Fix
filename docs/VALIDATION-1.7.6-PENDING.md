# v1.7.6 / code86 — candidate acceptance (pending)

Candidate from [#131](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/131), which contains the S2 work first reviewed in [#129](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/129):

- retained-process service transfer admitted in confirmed TOP (upper CRTC on, lower off, terminal repair, stable generation), in addition to BOTH;
- shared descriptor-bound stop for authorized same-version failed-start recovery, with a durable fence before commit;
- the exact published v1.7.5 admitted as an update origin (five origins: 1.7.1–1.7.5);
- shorter pending-update notice.

Excluded from this candidate: Auto Bottom Off ([#117](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/117)), refresh research ([#37](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/37)) and any new black-screen mitigation. [#130](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/130) adds host tests and documentation only and does not change the APK. Display commands, wake-repair timing, stall containment, CPU Fix and Guard behavior are unchanged.

## Software gates before the physical session

| Gate | Status |
| --- | --- |
| Exact-head PR CI: build, host suites, Android 24-case retirement matrix | PENDING |
| Exact-head CodeQL (actions + java-kotlin) on the `main`-based PR | PENDING |
| Squash integration to `main` (no release) | NOT RUN |
| Owner-dispatched main CI with `sign_main_candidate`, protected `thor-signing` approval | NOT RUN |
| Signed artifact recorded: run ID, `Jesty-Thor-Fix-1.7.6.apk`, size, SHA-256, established certificate, code86 | NOT RUN |

The APK tested on the Thor must be that signed main artifact. A test-signed pull-request build does not transfer to publication; if the bytes differ, repeat the affected physical checks.

## Single supervised physical session

One owner-present session covers the pending v1.7.5 acceptance and the v1.7.6 candidate. Follow the [supervised capture protocol](quality/S1-SUPERVISED-THOR-WAKE-CAPTURE-2026-10-18.md): agreed normal recovery before starting, no induced blackouts, no repeated mode/power presses to provoke a fault, and **STOP** at the first abnormal presentation.

**Preflight (record, do not change mid-session):** installed version 1.7.5/code85, firmware, battery level, mode, refresh rate, True Bottom Screen Off, CPU Fix, Guard (OFF unless a Guard case is planned), USB state and any external sleep automation (off).

### A — published v1.7.5 baseline (pending S6 for 1.7.5)

| ID | Check | Result |
| --- | --- | --- |
| A1 | App opens; service ACTIVE (or STOCK as configured); diagnostics readable | NOT RUN |
| A2 | BOTH → TOP → BOTH: lower panel off in TOP, both on in BOTH, status confirmed | NOT RUN |
| A3 | One ordinary sleep/wake in BOTH and one in TOP, Guard OFF | NOT RUN |
| A4 | Settings retained after closing and reopening the app | NOT RUN |
| A5 | Containment status only if it occurs naturally (never induced) | NOT RUN |

### B — in-place upgrade v1.7.5 → v1.7.6 (exact signed APK, no uninstall, no data clear)

| ID | Check | Result |
| --- | --- | --- |
| B1 | Upgrade while in **TOP** with confirmed true off (the new path): open the app, transfer completes without reboot, one healthy 1.7.6 service, lower panel stays off, no compositor restart | NOT RUN |
| B2 | Settings (True Off, CPU Fix, Guard) identical after the transfer | NOT RUN |
| B3 | Upgrade in BOTH from 1.7.5 | NOT RUN by design: one installed origin per session; no downgrade to manufacture a second trial. Generic Android covers BOTH/TOP for all five origins |

If the transfer stays pending, record the notice text and diagnostics, then perform one normal restart only; do not retry the update repeatedly.

### C — v1.7.6 behavior

| ID | Check | Result |
| --- | --- | --- |
| C1 | BOTH → TOP → BOTH with confirmed status | NOT RUN |
| C2 | One ordinary sleep/wake per mode | NOT RUN |
| C3 | CPU Fix state retained; no additional compositor restart after the transfer | NOT RUN |
| C4 | One normal reboot: boot gate reaches READY, settings retained, same behavior | NOT RUN |
| C5 | Dashboard health, pending-update notice absent after success, diagnostics save | NOT RUN |
| C6 | Guard ON normal cycle in BOTH and TOP (only if Guard is part of the owner's usage) | NOT RUN |

Not run by design: failed-start recovery (cannot be induced safely on hardware), stall/blackout induction, BOTTOM-only and dock.

**STOP conditions:** black or abnormal presentation on either screen, unexpected compositor/UI restart, two services or lost identity after the update, transfer stuck after one normal restart, loss of input. Stop, note the time and last action, preserve diagnostics, then perform only the agreed recovery.

## Publication

After owner acceptance of the exact signed artifact, run **Publish tested candidate** with that main run ID (pre-release or stable). Then update the README and integrity records to v1.7.6 in a documentation follow-up, as for v1.7.4 and v1.7.5. Raw captures, device identifiers and signing material stay owner-local.

No device contact, signing, merge or publication was performed while preparing this record.
