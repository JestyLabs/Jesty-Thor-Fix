# v1.7.6 / code86 — candidate acceptance

**Owner decision (2026-10-28): publish the second signed candidate, CI run [40690534453](https://github.com/JestyLabs/Jesty-Thor-Fix/actions/runs/40690534453) (main `dd6d773`), as a pre-release; promotion to stable waits for a hardware observation of the sleep-order race path (see Known limitations).** Two owner-supervised physical rounds; no STOP condition. Open items are listed under *Known limitations*.

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
| B1 upgrade in TOP | **FAIL for the live transfer**: install and version correct, lower panel stayed off, no compositor restart, one service; the successor reported the published 1.7.5 predecessor *ineligible* and held the transfer (cause UNKNOWN: the failed eligibility condition is not logged). One normal restart completed it (one healthy 1.7.6 service, boot READY in TOP). The held state showed the restart notice (*SERVICE TRANSFER PENDING*: restart once; contact Jesty only if still pending) |
| B2, C1, C3, C4, C5 | PASS; C6 NOT RUN (Guard not used) |
| C2 | Wakes normal, **but** both BOTH lid sleeps showed the sleep-order race: the lower panel was powered off first, `WATCH_BOTH` re-powered it while the top was still on, and it stayed powered for the whole sleep. The long-sleep collector shows the same race in 7 of 20 BOTH sleeps on published 1.7.5 (pre-existing) |
| D1, D2 | PASS (D2 by owner observation only: the manual toggle leaves no log line); D3 NOT RUN |

That signed artifact is therefore not published. The candidate is rebuilt with the block E fix (and the separate transfer diagnostics work), signed again, and the affected checks are repeated.

## Second signed candidate (main `dd6d773`, CI run 40690534453, 2026-10-28)

Contains #131, #132, [#134](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/134) (sleep-order race and toggle log) and [#135](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/135) (transfer refusal reason).

- **Software gates:** main CI, the Android emulator job and CodeQL PASS. The owner dispatched `sign_main_candidate`, `thor-signing` was approved, and the sign job PASSed.
- **APK:** `Jesty-Thor-Fix-1.7.6.apk`, 9 307 611 bytes, SHA-256 `2620A3004A2219077DE6F5D7FD9147EA4F8B63BC270FBBBB63929351387D641E`.
- **Signatures:** v1, v2 and v3, one signer, the established release certificate `727D4850…AD6FC`; code86.

Physical round 2: owner-supervised, USB, Guard OFF, 120 Hz. The upgrade origin was the first signed candidate, because the device already ran it.

| ID | Check | Result |
| --- | --- | --- |
| F1 | Install in confirmed TOP and open the app | PASS. Lower panel stayed off, no compositor restart, one service. The live transfer was held as designed (a same-version predecessor is not an admitted origin) and the refusal now names its condition: `Retirement predecessor ineligible reason=PREVIOUS_VERSION;value=1.7.6` (#135) |
| F2 | One normal restart | PASS. Boot reconcile OFF in TOP, `BOOT READY`, one 1.7.6 service launched from the only installed APK |
| E2 | TOP → BOTH → TOP → BOTH | PASS. The lower panel is still turned on in the same sample (call ≈ 204–206 ms, no confirmation delay), and off in ≈ 189–198 ms |
| E1 | Two lid sleeps in BOTH | PASS: no lower re-power, normal wakes, no DSI/`-110`/PLL errors. **The race was not exercised**: in this boot the platform turned the top panel off first in both sleeps. On both wakes the new code did not duplicate the platform's lower power-on during the brief top-on/lower-off window |
| E4 | One lid sleep in TOP | PASS. Wake repair turned the lower panel off (`OFF_OK`), the display power flag returned to 0 |
| D2 | Manual fix toggle in BOTH | PASS. `USER_RECONCILE fix=0;result=CONFIRMED` and `fix=1;result=CONFIRMED` logged (#134) |
| C1, C3 | Mode cycle; compositor, surface flinger and system server unchanged since boot; no blocked threads | PASS |
| E3 | Awake lower-panel loss in stable BOTH | NOT RUN (cannot be induced safely; host tests only) |

### Known limitations at publication

- The sleep-order race fix is host-tested but not yet exercised on hardware. The platform's power-off order varies between sleeps and boots (lower first in 12 of 20 BOTH sleeps on 1.7.5 in one boot; top first in both round-2 sleeps). The owner's long-sleep collector keeps recording natural sleeps.
- Upgrading from published 1.7.5 in TOP can hold the live transfer and ask for one normal restart, as observed in round 1. The cause on that device is UNKNOWN. From 1.7.6 on, a held transfer logs the failed condition.
- E3, B3, C6, D3 and failed-start recovery were not run on hardware.

Excluded from this candidate: Auto Bottom Off ([#117](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/117)) and refresh research ([#37](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/37)). [#130](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/130) adds host tests and documentation only and does not change the APK. Display commands, wake-repair timing, CPU Fix and Guard behavior are unchanged; stall containment changes only as described in #132, and the automatic BOTH ON only as described in block E.

## Software gates before the physical session

| Gate | Status |
| --- | --- |
| #131 exact-head PR CI (`d461a15`): build, host suites, Android 24-case retirement matrix | PASS ([CI 40406266984](https://github.com/JestyLabs/Jesty-Thor-Fix/actions/runs/40406266984)) |
| #131 exact-head CodeQL (actions + java-kotlin) | PASS ([CodeQL 40406266727](https://github.com/JestyLabs/Jesty-Thor-Fix/actions/runs/40406266727)) |
| #131 squash integration to `main` (no release) | PASS (`36e7fd0`) |
| #132 exact-head PR CI and CodeQL (lower-failure latch) | PASS |
| #132 squash integration to `main` (no release) | PASS (`23e1560b`) |
| Owner-dispatched main CI with `sign_main_candidate`, protected `thor-signing` approval | PASS (first candidate: run 40417572521; second: run 40690534453) |
| Signed artifact recorded: run ID, `Jesty-Thor-Fix-1.7.6.apk`, size, SHA-256, established certificate, code86 | PASS (second candidate above; first candidate `02F0437E…CA98`, superseded) |
| #134 and #135 exact-head PR CI and CodeQL; squash integration (`20cd92f`, `dd6d773`) | PASS |

The APK tested on the Thor must be that signed main artifact. A test-signed pull-request build does not transfer to publication; if the bytes differ, repeat the affected physical checks.

## Single supervised physical session

One owner-present session covers the pending v1.7.5 acceptance and the v1.7.6 candidate. Follow the [supervised capture protocol](quality/S1-SUPERVISED-THOR-WAKE-CAPTURE-2026-10-18.md): agreed normal recovery before starting, no induced blackouts, no repeated mode/power presses to provoke a fault, and **STOP** at the first abnormal presentation.

**Preflight (record, do not change mid-session):** installed version 1.7.5/code85, firmware, battery level, mode, refresh rate, True Bottom Screen Off, CPU Fix, Guard (OFF unless a Guard case is planned), USB state and any external sleep automation (off).

### A — published v1.7.5 baseline (pending S6 for 1.7.5)

| ID | Check | Result |
| --- | --- | --- |
| A1 | App opens; service ACTIVE (or STOCK as configured); diagnostics readable | PASS |
| A2 | BOTH → TOP → BOTH: lower panel off in TOP, both on in BOTH, status confirmed | PASS |
| A3 | One ordinary sleep/wake in BOTH and one in TOP, Guard OFF | PASS |
| A4 | Settings retained after closing and reopening the app | PASS |
| A5 | Containment status only if it occurs naturally (never induced) | NOT RUN (did not occur) |

### B — in-place upgrade v1.7.5 → v1.7.6 (exact signed APK, no uninstall, no data clear)

| ID | Check | Result |
| --- | --- | --- |
| B1 | Upgrade while in **TOP** with confirmed true off (the new path): open the app, transfer completes without reboot, one healthy 1.7.6 service, lower panel stays off, no compositor restart | FAIL for the live transfer (held; one restart completed it safely; see round 1) |
| B2 | Settings (True Off, CPU Fix, Guard) identical after the transfer | PASS |
| B3 | Upgrade in BOTH from 1.7.5 | NOT RUN by design: one installed origin per session; no downgrade to manufacture a second trial. Generic Android covers BOTH/TOP for all five origins |

If the transfer stays pending, record the notice text and diagnostics, then perform one normal restart only; do not retry the update repeatedly.

### C — v1.7.6 behavior

| ID | Check | Result |
| --- | --- | --- |
| C1 | BOTH → TOP → BOTH with confirmed status | PASS (both rounds) |
| C2 | One ordinary sleep/wake per mode | Round 1: wakes normal, sleep-order race observed (fixed in #134); round 2: see E1/E4 |
| C3 | CPU Fix state retained; no additional compositor restart after the transfer | PASS (both rounds) |
| C4 | One normal reboot: boot gate reaches READY, settings retained, same behavior | PASS (both rounds) |
| C5 | Dashboard health, pending-update notice absent after success, diagnostics save | PASS |
| C6 | Guard ON normal cycle in BOTH and TOP (only if Guard is part of the owner's usage) | NOT RUN (Guard not used) |

Not run by design: failed-start recovery (cannot be induced safely on hardware), stall/blackout induction, BOTTOM-only and dock.

### D — S1 lower-failure latch (#132), never induced

| ID | Check | Result |
| --- | --- | --- |
| D1 | Across A–C transitions, sleeps and the C4 reboot: no `LOWER_FAILURE_LATCH set` in diagnostics; lower panel turns ON normally in BOTH; status never stuck in BOTH_ON_BACKOFF/BOTH_DEGRADED | PASS |
| D2 | Manual fix toggle in the app behaves as before (the latch's only in-process exit) | PASS (round 2 logged by #134) |
| D3 | Only if a stall occurs naturally: lower panel stays OFF after a BOTH wake until manual toggle or reboot; follow the agreed recovery | NOT RUN (never induced) |

### E — S1 sleep-order race fix, never induced

| ID | Check | Result |
| --- | --- | --- |
| E1 | Two ordinary lid sleeps in BOTH: no `WATCH_BOTH` call between the lower and top power-off; if the lower panel goes off first, one `WATCH_BOTH_SKIPPED reason=NON_INTERACTIVE`; lower panel stays off during sleep; both panels normal on wake | PASS; race not exercised (top first in both sleeps) |
| E2 | TOP → BOTH still turns the lower panel on immediately (same sample, no extra delay) | PASS |
| E3 | Awake lower-panel loss in stable BOTH restored after confirmation | NOT RUN (cannot be induced safely; host tests only) |

**STOP conditions:** black or abnormal presentation on either screen, unexpected compositor/UI restart, two services or lost identity after the update, transfer stuck after one normal restart, loss of input. Stop, note the time and last action, preserve diagnostics, then perform only the agreed recovery.

## Publication

After owner acceptance of the exact signed artifact, run **Publish tested candidate** with that main run ID (pre-release or stable). Then update the README and integrity records to v1.7.6 in a documentation follow-up, as for v1.7.4 and v1.7.5.

**Published 2026-10-28 as a pre-release:**
- [publication run 40783945707](https://github.com/JestyLabs/Jesty-Thor-Fix/actions/runs/40783945707), from signed candidate run 40690534453;
- tag `v1.7.6` points to `dd6d773`;
- the downloaded release asset has SHA-256 `2620a300…d641e`, identical to the tested artifact. See [release integrity](RELEASE-INTEGRITY.md).

Promotion of the same tag to stable is pending the race-path observation. Raw captures, device identifiers and signing material stay owner-local.

This record was prepared after the physical rounds; it contains no raw captures, device identifiers or signing material.
