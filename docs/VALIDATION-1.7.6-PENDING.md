# v1.7.6 / code86 — candidate acceptance (pending)

Candidate from [#131](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/131), which contains the S2 work first reviewed in [#129](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/129):

- retained-process service transfer admitted in confirmed TOP (upper CRTC on, lower off, terminal repair, stable generation), in addition to BOTH;
- shared descriptor-bound stop for authorized same-version failed-start recovery, with a durable fence before commit;
- the exact published v1.7.5 admitted as an update origin (five origins: 1.7.1–1.7.5);
- shorter pending-update notice;
- boot-scoped lower-failure latch after a stalled display call (S1, [#132](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/132));
- S1 sleep-order race: in stable BOTH an automatic lower ON waits for one confirming sample and is skipped while the device is non-interactive, so going to sleep no longer re-powers the lower panel for the whole sleep (block E).

## Physical session on the first signed candidate (main `23e1560b`, 2026-10-28)

Owner-supervised, USB, Guard OFF, refresh fixed at 120 Hz in BOTH before block A. No STOP condition.

| Block | Result |
| --- | --- |
| A1–A4 (published 1.7.5) | PASS; A5 NOT RUN (did not occur) |
| B1 upgrade in TOP | **FAIL for the live transfer**: install and version correct, lower panel stayed off, no compositor restart, one service; the successor reported the published 1.7.5 predecessor *ineligible* and held the transfer (cause UNKNOWN: the failed eligibility condition is not logged). One normal restart completed it (one healthy 1.7.6 service, boot READY in TOP). The held state showed a support notice rather than a restart notice |
| B2, C1, C3, C4, C5 | PASS; C6 NOT RUN (Guard not used) |
| C2 | Wakes normal, **but** both BOTH lid sleeps showed the sleep-order race: the lower panel was powered off first, `WATCH_BOTH` re-powered it while the top was still on, and it stayed powered for the whole sleep. The long-sleep collector shows the same race in 7 of 20 BOTH sleeps on published 1.7.5 (pre-existing) |
| D1, D2 | PASS (D2 by owner observation only: the manual toggle leaves no log line); D3 NOT RUN |

That signed artifact is therefore not published. The candidate is rebuilt with the block E fix (and the separate transfer diagnostics work), signed again, and the affected checks are repeated.

Excluded from this candidate: Auto Bottom Off ([#117](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/117)) and refresh research ([#37](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/37)). [#130](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/130) adds host tests and documentation only and does not change the APK. Display commands, wake-repair timing, CPU Fix and Guard behavior are unchanged; stall containment changes only as described in #132, and the automatic BOTH ON only as described in block E.

## Software gates before the physical session

| Gate | Status |
| --- | --- |
| #131 exact-head PR CI (`d461a15`): build, host suites, Android 24-case retirement matrix | PASS ([CI 40406266984](https://github.com/JestyLabs/Jesty-Thor-Fix/actions/runs/40406266984)) |
| #131 exact-head CodeQL (actions + java-kotlin) | PASS ([CodeQL 40406266727](https://github.com/JestyLabs/Jesty-Thor-Fix/actions/runs/40406266727)) |
| #131 squash integration to `main` (no release) | PASS (`36e7fd0`) |
| #132 exact-head PR CI and CodeQL (lower-failure latch) | PENDING |
| #132 squash integration to `main` (no release) | NOT RUN |
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

### D — S1 lower-failure latch (#132), never induced

| ID | Check | Result |
| --- | --- | --- |
| D1 | Across A–C transitions, sleeps and the C4 reboot: no `LOWER_FAILURE_LATCH set` in diagnostics; lower panel turns ON normally in BOTH; status never stuck in BOTH_ON_BACKOFF/BOTH_DEGRADED | NOT RUN |
| D2 | Manual fix toggle in the app behaves as before (the latch's only in-process exit) | NOT RUN |
| D3 | Only if a stall occurs naturally: lower panel stays OFF after a BOTH wake until manual toggle or reboot; follow the agreed recovery | NOT RUN (never induced) |

### E — S1 sleep-order race fix, never induced

| ID | Check | Result |
| --- | --- | --- |
| E1 | Two ordinary lid sleeps in BOTH: no `WATCH_BOTH` call between the lower and top power-off; if the lower panel goes off first, one `WATCH_BOTH_SKIPPED reason=NON_INTERACTIVE`; lower panel stays off during sleep; both panels normal on wake | NOT RUN |
| E2 | TOP → BOTH still turns the lower panel on immediately (same sample, no extra delay) | NOT RUN |
| E3 | Awake lower-panel loss in stable BOTH restored after confirmation | NOT RUN (cannot be induced safely; host tests only) |

**STOP conditions:** black or abnormal presentation on either screen, unexpected compositor/UI restart, two services or lost identity after the update, transfer stuck after one normal restart, loss of input. Stop, note the time and last action, preserve diagnostics, then perform only the agreed recovery.

## Publication

After owner acceptance of the exact signed artifact, run **Publish tested candidate** with that main run ID (pre-release or stable). Then update the README and integrity records to v1.7.6 in a documentation follow-up, as for v1.7.4 and v1.7.5. Raw captures, device identifiers and signing material stay owner-local.

No device contact, signing, merge or publication was performed while preparing this record.
