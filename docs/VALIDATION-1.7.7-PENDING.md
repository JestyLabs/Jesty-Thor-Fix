# v1.7.7 / code87 — candidate acceptance (pending)

**Status: published as stable.** Round 3 (candidate `3363F51A…`, the released APK) passed C1–C3 and C5–C7. C4 cannot run on a 1.7.7 candidate. D-1 and D-2 are NOT RUN: the owner postponed them.

**Published as stable v1.7.7 on 2026-11-03** (owner decision; [publication run](https://github.com/JestyLabs/Jesty-Thor-Fix/actions/runs/41622630233) from signed CI run 41502611834). The tag is on `ab5126d`. The release asset's SHA-256 is `3363F51A…`, the same APK that ran round 3. D-1 and D-2 were not run before publication.

The candidate contains:
- [#139](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/139) (main `a11b48f`): panel-RAM bypass interception;
- [#140](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/140) (main `044e88d`): poisoned-boot detection;
- [#142](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/142) (main `c881b92`): published v1.7.6 as a live-transfer origin;
- [#143](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/143) (main `ea79f42`): the attribute written in the kernel's form;
- [#144](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/144) (main `f689949`): start on a restart that comes before the first open after an update;
- [#145](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/145) (main `ab5126d`): write only after the lower panel has finished starting.

v1.7.6 stays a pre-release. It is not promoted to stable, because its containment record assumed the stall latch would contain a blackout, and the 2026-10-29 trial below showed that it does not.

## Hardware basis (owner-supervised, published v1.7.6)

**2026-10-29, the blackout reproduced on demand.** The owner switched to TOP, then changed the refresh 120→60 from the Smooth Display tile:
- At the vendor `bypass_ram` write, with the lower panel off: `DSI_PLL_0: lock failed` → `-110`/`-22` → `failed to send DSI_CMD_SET_VID_BYPASS_RAM`.
- 1st lower start (BOTH): 6× `dsi-ctrl-0: Command transfer failed`. The lower panel stayed black; the ON call took 1407 ms.
- 2nd start: `wait for commit done failed ret:-110` on CRTC 243, and both screens went black until reboot.
- Neither call reached the 5 s stall latch. The reset loop ran without app calls.

**Same day, the same sequence with a file bind-mounted over the attribute** (manual root helpers, not the app):
- the kernel stayed clean;
- the lower panel started normally (ON 207 ms);
- the request was applied afterwards with the lower panel on, with no errors.

**120 Hz with `bypass_ram=1` shows wrong colours.** Writing 0 corrects it.

## Software gates

| Gate | Result |
| --- | --- |
| #139: `BypassRamInterceptorTest` (real coordinator); with the kernel-form fix 56 checks, 12/12 mutants rejected | PASS (host) |
| Write only after the lower panel has finished starting (never during a wake in TOP): 68 checks, 18/18 mutants rejected; the TOP-wake and BOTH-wake cases fail before the change | PASS (host) |
| #140: `PoisonedBootTest` (real coordinator, 32 checks); 6/6 mutants rejected | PASS (host) |
| Published v1.7.6 as a live-transfer origin: actual `LiveRetirement` with the frozen published 1.7.6 producer (68 cases); refused before the change | PASS (host) |
| `test-live-transfer-origins.py`: every earlier published release from 1.7.1 is admitted at every site; 13/13 controls rejected | PASS (host) |
| Restart before the first open after an update: actual `AutoService`/`PServer`/`RebootActivation`, 9 new scenarios; refused before the change; 4 new compiled controls (18/18 rejected) | PASS (host) |
| Main CI, Android emulator job, CodeQL on both PRs | PASS |
| Signed candidate `3363F51A…` from main `ab5126d`: hash, signature and version checked on the host before install | PASS |

## Physical acceptance plan (owner-supervised, long-sleep collector running)

| ID | Check | Expected |
| --- | --- | --- |
| C0 | BOTH, published v1.7.6 daemon running: install over it and open the app, **no restart**; then read the daemon's mount namespace and mountinfo | Live transfer to 1.7.7 (one daemon, the new one); `BYPASS_INTERCEPT_READY`; the bind and second sysfs mount are present in the init namespace; `bypass_intercept=ARMED` |
| C1 | BOTH: Smooth Display 120↔60 | `BYPASS_APPLIED reason=REQUEST` with a short `delay_ms`; one `bypass_ram_store` per change; no error; colours normal |
| C2 | TOP: 120→60 from the tile, then BOTH; repeat 60→120 | No `lock failed` at the change; `BYPASS_DEFERRED reason=LOWER_OFF`; normal lower start; `BYPASS_APPLIED source=LOWER_ON` right after |
| C3 | Change in TOP, then sleep and wake by power button in BOTH | Applied on the platform's lower ON (watcher) |
| C4 | Live service transfer with the interception active | `BYPASS_INTERCEPT_REUSED`; a request made during the transfer is applied |
| C5 | Reboot | Interception re-established; post-boot state recorded |
| C6 | Display fix off | A waiting request is applied, then `NO_MOUNTS_LEFT`; the framework writes directly again (checked in BOTH) |
| C7 | Long sleep with a waiting request | Applied on wake; no error |
| D-1 | Trigger with the interception removed for the test (manual unmount by the owner's root helper), TOP, refresh change | `KERNEL_SIGNAL reason=KMSG_PRECURSOR`, `LOWER_POISONED`, dashboard fault line, one notification; no app lower ON; then reboot |
| D-2 | Detection cost | 1 h of normal use: daemon CPU before and after; sleep unchanged |

**STOP conditions:**
- any `lock failed`, `Command transfer failed` or `-110` outside D-1;
- both screens black;
- a second service;
- an unexpected compositor restart.

## Physical round 1 (2026-11-02, owner-supervised, candidate `DEED4415…`)

The Thor ran the published v1.7.6 daemon in BOTH, with the long-sleep collector running.

| ID | Result | Observed |
| --- | --- | --- |
| C0 | **PASS** | See below |
| C1 | **FAIL** (app defect; fixed for the next candidate) | See below |

**C0: live transfer from v1.7.6, no restart.**
- **Transfer:**
  - after the install, opening the app retired the v1.7.6 daemon and started `READY 1.7.7`, with `KERNEL_SIGNALS ready`;
  - `BOOT READY mode=0 bottom=1`, then `BYPASS_INTERCEPT_READY real=1`;
  - one daemon remained. SurfaceFlinger, system_server and the composer kept their process IDs.
- **Mounts:** a root read showed the daemon in the init mount namespace. The bind over the attribute and the second sysfs mount were present there and propagated to the system_server namespace. The requested, visible and real values matched (1).
- **Kernel:** no DSI error.
- **Dashboard:** the owner confirmed REFRESH GUARD ON.

**C1, 60→120 in BOTH:**
- **What happened:**
  - the request `0x0` was intercepted (`BYPASS_REQUEST value=0;real=1`);
  - the app then wrote `0`, which the kernel store refuses (`oscar bypass_ram_store error`, about once per second as the watcher retried);
  - the attribute stayed at 1 at 120 Hz. No DSI, PLL or `-110` error and no STOP condition;
  - returning to 60 Hz made the request equal to the real value, and the retries stopped.
- **Unexplained:** the owner saw a brief colour artefact at the 120→60 switch. Whether stock behaviour shows the same is UNKNOWN.
- **Why the tests missed it:** the 2026-10-29 manual helpers copied the framework's raw `0x0`, and the host fake accepted any form.
- **Fix:**
  - the real attribute is written in the framework's form (`0x0`/`0x1`);
  - the host fake now refuses other forms, as the kernel does;
  - watcher retries after a refused write stop at three. Each new request and each lower ON earns a fresh attempt.
- **Candidate `DEED4415…` is superseded.**

## Physical round 2 (2026-11-02, owner-supervised, candidate `2A802D2C…`)

Candidate `2A802D2C…` was installed over the running `DEED4415…` daemon, a same-version change that is never a
transfer origin, so a restart was planned. The app was not opened before that restart.

| ID | Result | Observed |
| --- | --- | --- |
| C5, first restart | **FAIL** (activation defect since v1.7.2; fixed for the next candidate) | See below |
| C5, second restart | **PASS** | See below |
| C1, BOTH 60→120→60, twice | **PASS** | See below |
| C2, refresh change in TOP then BOTH, both directions | **PASS** | See below |
| C3, request held in TOP, sleep, wake in TOP | **FAIL** (app defect; fixed in #145) — STOP | See below |

**C5, first restart:**
- **What happened:**
  - no daemon ran after the restart;
  - the boot start found no predecessor to transfer from (`live handover held; safe predecessor not verified`) and stopped there;
  - with no interception, the attribute was 0, both panels were on and there was no DSI error.
- **Cause:** activation records its baseline only when the app or service first runs after an install. That first run was this new boot, so the activation evidence still asked for a restart. Users who update, through the in-app updater or otherwise, and restart before opening the app lose the fix for that boot and need a second restart.
- **Fix:** if no daemon answers and the last instance-lock owner stamped an earlier kernel boot, no daemon has run in this boot. The service then starts held, as after a verified restart, and records proof for this boot. An absent socket, or a stamp that is unreadable or from the current boot, keeps the restart fallback.

**C5, second restart:**
- one daemon, started at once from `STALE_PREVIOUS_BOOT`: `READY 1.7.7`, `KERNEL_SIGNALS ready`, `BOOT READY mode=0 bottom=1`, `BYPASS_INTERCEPT_READY real=0`;
- the bind and the second sysfs mount were in the init namespace and propagated to system_server;
- requested, visible and real values were 0; no DSI error.

**C1, BOTH 60→120→60, twice:**
- **First pair:**
  - the attribute was still 0 from boot, so 120 Hz needed no write;
  - back at 60 Hz the app wrote `0x1` (`BYPASS_APPLIED value=1;source=REQUEST`, 34 ms), and the kernel accepted it (`oscar bypass_ram_store 1`).
- **Second pair:** this is the path that failed on the previous candidate.
  - 60→120: `BYPASS_REQUEST value=0;real=1` → `BYPASS_APPLIED value=0;source=REQUEST` (60 ms); the kernel accepted it (`oscar bypass_ram_store 0`);
  - 120→60: the write of `0x1` was accepted again.
- **Every change:**
  - no `oscar bypass_ram_store error`, no DSI error, both panels on;
  - the owner saw normal colours at both rates;
  - the requested, visible and real values ended equal (`0x1`/`0x1`/1).
- **SELinux:** the root context is permissive. Each write logs `avc: denied { write }` for `bypass_ram` with `permissive=1`. An enforcing policy for that context would deny the write.

**C2, refresh change in TOP then BOTH, both directions:**
- in TOP, the requests for 60 Hz and 120 Hz were held (`BYPASS_DEFERRED reason=LOWER_OFF`);
- on each switch back to BOTH, the daemon's own lower ON returned and the request was applied about 75 ms later (`BYPASS_APPLIED source=LOWER_ON`);
- the kernel accepted one store per change, none with the lower panel off;
- no DSI error, one daemon, no compositor restart.

**C3, request held in TOP, sleep, wake in TOP (STOP):**
- **Before the wake:** a 60→120 change in TOP was held. After a sleep and a wake by power button, still in TOP, the platform started the lower panel, as it always does in TOP before the wake repair.
- **During the start:** about 20 ms into that start the watcher saw the lower panel active, and its power state already read `On`. The waiting request was applied 60 ms into the start (`BYPASS_APPLIED value=0;source=WATCHER`).
- **Kernel:** the log, with the writing thread recorded by the long-sleep collector, shows these failures inside the daemon's `bypass_ram_store` call, 35 ms after the platform's panel reset:
  - `DSI_PLL_0: lock failed`, then `enable failed: -110`;
  - `failed to send DSI_CMD_SET_VID_PASS_RAM cmds, rc=-22`.

  Every later command to the lower panel failed.
- **Detection:** the poisoned-boot detection latched, posted its notice and kept the lower panel off, also after a switch to BOTH. No compositor restart; a restart recovered the panel.
- **Cause:** the interceptor treated `On` as ready. In C2 the daemon's own ON had already finished when its call returned; a platform ON reads `On` from its start.
- **Fix (#145):** write only after the lower panel has been seen on, in BOTH or BOTTOM ONLY, for 1 s; never count an ON seen in TOP.
- **Candidate `2A802D2C…` is superseded.** C4–C7 and D-1/D-2 were not run.

## Physical round 3 (2026-11-02 to 2026-11-03, owner-supervised, candidate `3363F51A…`)

Candidate `3363F51A…` (main `ab5126d`, with #144 and #145) was installed over the running `2A802D2C…` daemon. The app was not opened. The owner then restarted from the Power menu.

| ID | Result | Observed |
| --- | --- | --- |
| C5, restart before the first open | **PASS** (#144) | See below |
| C3, request held in TOP, 38 min sleep, wake in TOP, then BOTH | **PASS** (#145) | See below |
| C1, BOTH 60→120→60, twice | **PASS** | See below |
| C2, refresh change in TOP then BOTH, both directions | **PASS** | See below |
| C4, live transfer with the interception active | **NOT RUNNABLE** on a 1.7.7 candidate | See below |
| C6, display fix turned off with a request waiting | **PASS**, with one log-only defect | See below |
| C7, 11 h sleep on battery with a request waiting | **PASS** | See below |
| D-1, D-2 | **NOT RUN** (postponed by the owner) | — |

Each PASS is one run. No STOP condition occurred in round 3. One daemon ran throughout. SurfaceFlinger, system_server and the composer kept their process IDs from the restart to the end.

**C5, restart before the first open (#144):**
- the boot start found no daemon in this kernel boot and launched at once (`no daemon in this kernel boot; starting without transfer`, then `READY 1.7.7`, `BOOT READY mode=0 bottom=1` and `BYPASS_INTERCEPT_READY real=0`);
- no second restart was needed. Round 2 needed two in the same situation;
- the bind and the second sysfs mount were in the init namespace and propagated to system_server; requested, visible and real values were 0; no DSI error.

**C3, request held in TOP, sleep, wake in TOP (#145):** this is the sequence that poisoned the link in round 2.
- 60→120 in TOP was held (`BYPASS_DEFERRED reason=LOWER_OFF`). The owner slept the Thor for 38 min by power button and woke it in TOP.
- The platform started the lower panel as usual. At the moment the round 2 candidate wrote, this candidate logged `BYPASS_DEFERRED reason=LOWER_SETTLING;source=WATCHER`.
- The wake repair turned the lower panel off. There was no `bypass_ram_store` during the wake.
- On BOTH the request was applied 47 ms after the daemon's own lower ON returned (`BYPASS_APPLIED source=LOWER_ON`). The kernel accepted the store 191 ms after the panel's `DSI_CMD_SET_ON`.
- No `lock failed`, `-110`, `Command transfer failed` or `failed to send DSI_CMD` in the boot.
- A first attempt did not exercise the path: the attribute was already 0 at 60 Hz in this boot, so the 120 Hz request equalled it and nothing was held.

**C1, BOTH 60→120→60, twice:**
- four `BYPASS_APPLIED source=REQUEST`, 35–59 ms each;
- one accepted kernel store per change, no store error, no DSI error;
- the owner saw normal colours.

**C2, refresh change in TOP then BOTH, both directions:**
- the requests made in TOP were held (`LOWER_OFF`). Each was applied 74–89 ms after the daemon's lower ON returned on the switch to BOTH;
- one store per application, none in TOP; no DSI error.

**C4, live transfer with the interception active:**
- the live transfer admits only published releases (1.7.1–1.7.6) as origins, so a 1.7.7 daemon cannot be retired by another 1.7.7. `BYPASS_INTERCEPT_REUSED` can therefore only be seen when the next release takes over from a 1.7.7 daemon with the interception armed;
- C4 moves to the v1.7.8 acceptance. The host tests of the reuse path remain the evidence until then.

**C6, display fix turned off with a request waiting:**
- a 60→120 request was held in TOP. Turning the display fix off in TOP started the lower panel (`USER_RECONCILE ON`). The request was applied on that ON, then `BYPASS_INTERCEPT_OFF cause=FIX_OFF`;
- with the fix off, the framework wrote the attribute directly in BOTH: three kernel stores whose callers were the vendor shell's short-lived threads;
- turning the fix on again re-armed the interception (`BYPASS_INTERCEPT_READY real=1`), with the mounts present again; no DSI error;
- the drain path did not run, because the pending request was already applied by the lower ON. The drain remains host-tested only;
- **Log-only defect:**
  - right after `BYPASS_INTERCEPT_OFF`, one more request event was processed with the attribute already unmounted (`BYPASS_REQUEST value=0;real=?`, then `BYPASS_DEFERRED reason=REAL_UNREADABLE`);
  - probable cause (inferred): the file watcher delivers its stop event, and the callback does not filter the event type;
  - nothing is written, and the next arm clears the stale request;
  - the owner chose to fix it in v1.7.8: accept only write events, and ignore request events outside ARMED/DRAINING.

**C7, long sleep on battery with a request waiting:**
- 60→120 was held in TOP before an 11 h 25 min sleep without the charger. The device was suspended for 98.6 % of the display-off time, and the battery went from 94 % to 93 %;
- on the wake in TOP (lid), the platform's lower start gave `LOWER_SETTLING`, then `LOWER_OFF`. There was no store during the night or the wake;
- on BOTH, `BYPASS_APPLIED source=LOWER_ON` after 11.4 h held. 60 Hz in BOTH was applied in 32 ms;
- no DSI error across the whole night in the long-sleep collector's kernel log.
- **Unexplained:**
  - at that first BOTH the owner saw a brief flash with altered colours;
  - the kernel shows the lower panel starting at 120 Hz with the attribute still at its 60 Hz value. The held request reached the panel 258 ms after `DSI_CMD_SET_ON`;
  - the same start pair (120 Hz with 1) occurred in C3 (192 ms) and in an owner repeat of the sequence with a short sleep (191 ms), with no flash either time;
  - owner checks of BOTH switches without a TOP wake showed no flash. The panel started with matching values, and the only mismatches were 41–88 ms after the rate change that followed;
  - the cause is UNKNOWN. No error followed it.
  - Writing earlier would mean writing during the panel start, which is what poisoned the link in round 2. The app's own share of the delay (47–113 ms) could be shortened in v1.7.8.

## Known limitations

- **The vendor driver defect remains.** The interception only covers the framework's write path, and only while the display fix is enabled and the daemon runs. Not covered:
  - a write before the daemon arms at boot;
  - a dead daemon;
  - unknown other writers.
- **An already poisoned boot is not recovered.** Detection asks for a restart. A platform lower ON on wake before that restart can still start the reset loop.
- **The accumulated-suspend explanation is not excluded.**
- **A first install still needs one restart.** With no earlier daemon there is no boot stamp. An absent socket cannot be told apart from cleared app data with a root daemon still running.
- **After every boot, the kernel starts with `bypass_ram=0`** whatever the refresh setting, so 60 Hz runs with 0 until the next refresh change. Under study; not changed in this candidate.
