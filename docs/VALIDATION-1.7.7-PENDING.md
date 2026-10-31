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
| #139: `BypassRamInterceptorTest` (real coordinator, 48 checks); 8/8 mutants rejected | PASS (host) |
| #140: `PoisonedBootTest` (real coordinator, 32 checks); 6/6 mutants rejected | PASS (host) |
| Main CI, Android emulator job, CodeQL on both PRs | PASS |
| Signed candidate | NOT RUN |

## Physical acceptance plan (owner-supervised, long-sleep collector running)

| ID | Check | Expected |
| --- | --- | --- |
| C0 | Install, one normal restart, then read the daemon's mount namespace and mountinfo | `BYPASS_INTERCEPT_READY`; the bind and second sysfs mount are present in the init namespace; `bypass_intercept=ARMED` |
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

## Known limitations

- **The vendor driver defect remains.** The interception only covers the framework's write path, and only while the display fix is enabled and the daemon runs. Not covered:
  - a write before the daemon arms at boot;
  - a dead daemon;
  - unknown other writers.
- **An already poisoned boot is not recovered.** Detection asks for a restart. A platform lower ON on wake before that restart can still start the reset loop.
- **The accumulated-suspend explanation is not excluded.**
- **After every boot, the kernel starts with `bypass_ram=0`** whatever the refresh setting, so 60 Hz runs with 0 until the next refresh change. Under study; not changed in this candidate.
