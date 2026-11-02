# v1.7.7 / code87 — candidate acceptance (pending)

**Status: candidate; physical acceptance NOT RUN.** Nothing here is signed or published yet.

The candidate contains:
- [#139](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/139) (main `a11b48f`): panel-RAM bypass interception;
- [#140](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/140) (main `044e88d`): poisoned-boot detection.

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
| #140: `PoisonedBootTest` (real coordinator, 32 checks); 6/6 mutants rejected | PASS (host) |
| Published v1.7.6 as a live-transfer origin: actual `LiveRetirement` with the frozen published 1.7.6 producer (68 cases); refused before the change | PASS (host) |
| `test-live-transfer-origins.py`: every earlier published release from 1.7.1 is admitted at every site; 13/13 controls rejected | PASS (host) |
| Main CI, Android emulator job, CodeQL on both PRs | PASS |
| Signed candidate | NOT RUN |

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

## Known limitations

- **The vendor driver defect remains.** The interception only covers the framework's write path, and only while the display fix is enabled and the daemon runs. Not covered:
  - a write before the daemon arms at boot;
  - a dead daemon;
  - unknown other writers.
- **An already poisoned boot is not recovered.** Detection asks for a restart. A platform lower ON on wake before that restart can still start the reset loop.
- **The accumulated-suspend explanation is not excluded.**
- **After every boot, the kernel starts with `bypass_ram=0`** whatever the refresh setting, so 60 Hz runs with 0 until the next refresh change. Under study; not changed in this candidate.
