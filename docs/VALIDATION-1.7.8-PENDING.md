# v1.7.8 / code88 — candidate acceptance (pending)

**Status: candidate not yet built or signed. Physical acceptance NOT RUN.**

v1.7.7 was published as stable on 2026-11-03 ([record](VALIDATION-1.7.7-PENDING.md)). Two items from its acceptance move
here:
- **C4 could not run on a 1.7.7 candidate.** The live transfer admits only published releases as origins, so the reuse
  of the interception across a transfer can only be seen when the next release takes over from a 1.7.7 daemon.
- **C6 showed a log-only defect.** Right after the interception closed, one more request event was processed with the
  attribute already unmounted. The owner chose to fix it in v1.7.8.

The owner's requirement stands: an update never requires a restart. Every published release must be a live-transfer
origin of the next one.

The candidate will contain:
- [#147](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/147): the candidate identity 1.7.8/code88, published v1.7.7 as a
  live-transfer origin, and a new lower-fault gate;
- [#148](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/148): only completed writes count as bypass requests (the C6
  defect);
- this record and the release notes.

## The lower-fault gate (#147)

- **What a 1.7.7 daemon keeps in memory only:** its lower-panel fault latch. It is `LATCHED` after a slow or failed lower
  start, or `POISONED_<reason>` after a kernel signal.
- **Why that matters:** the successor's kernel reader starts at the end of the log. After a live transfer it would start
  unlatched, and it could power on a lower panel whose DSI link is already poisoned. That starts the reset loop.
- **The gate:** a 1.7.7 origin is transferred only if its snapshot reports `lower_fault=0` exactly. Otherwise the update
  completes at the next restart, which the fault notice already asks for.
- **Unchanged:** origins 1.7.1–1.7.6 never reported the field, and their gate is the same as before.

## Software gates

| Gate | Result |
| --- | --- |
| #147: origin contract with the identity at 1.7.8 and no admission | FAIL, nine missing sites for 1.7.7 (expected) |
| #147: origin contract after the change; 13/13 controls rejected | PASS (host) |
| #147: actual `LiveRetirement` with the frozen published 1.7.1–1.7.7 producers (67–70 cases each); 49 compiled controls rejected | PASS (host) |
| #147: a 1.7.7 producer reporting `LATCHED` or `POISONED_KMSG_PRECURSOR` is refused with `LOWER_FAULT`; a missing or duplicated field is refused | PASS (host) |
| #147: reboot activation, code87 handover in BOTH and confirmed TOP; with a lower fault, refused inside the retained session and the restart fallback kept | PASS (host) |
| #148: `afteroff` and `watchevents` fail before the change, the first with the C6 log lines | FAIL before, as expected |
| #148: `BypassRamInterceptorTest`, 76 checks; 21/21 mutants rejected (3 new) | PASS (host) |
| Main CI, Android emulator job (retirement matrix, 32 cases), CodeQL | NOT RUN (pending on the PRs) |
| Signed candidate: hash, signature and version checked on the host before install | NOT RUN |

## Physical acceptance plan (owner-supervised, long-sleep collector running)

**Order matters:**
- The Thor runs the published v1.7.7, the only 1.7.7 origin available.
- Installing the candidate uses it up: going back to 1.7.7 needs an uninstall.
- So C4 runs first, in BOTH, and nothing is installed before it.

| ID | Check | Expected |
| --- | --- | --- |
| C4-pre | Before the install: BOTH, published v1.7.7 running | One daemon `READY 1.7.7`; `bypass_intercept=ARMED`; the bind and the second sysfs mount in the init namespace; no kernel fault in this boot |
| C4 | Install the signed candidate over the running v1.7.7 and open the app, **no restart** | Live transfer, not held: one daemon, the new one, `READY 1.7.8`; `BYPASS_INTERCEPT_REUSED`; the same mounts, with requested, visible and real values equal; no `bypass_ram_store` during the transfer; no DSI error; SurfaceFlinger, system_server and the composer keep their process IDs |
| C1 | BOTH: Smooth Display 60→120→60, after the transfer | `BYPASS_APPLIED source=REQUEST` for each change through the reused mounts; one accepted store each; no error; colours normal |
| C2 | TOP: refresh change, then BOTH | `BYPASS_DEFERRED reason=LOWER_OFF` in TOP; `BYPASS_APPLIED source=LOWER_ON` on BOTH; no store in TOP |
| C6 | TOP: refresh change, then the display fix off; then the fix on again in BOTH | As in v1.7.7, and in addition: no `BYPASS_REQUEST` or `BYPASS_DEFERRED` line after `BYPASS_INTERCEPT_OFF`; `BYPASS_INTERCEPT_READY` when the fix is turned on again |
| C5 | Restart | One daemon `READY 1.7.8`; `BYPASS_INTERCEPT_READY`; mounts present; no DSI error |
| C7 | Long sleep with a request held in TOP, wake in TOP, then BOTH | `LOWER_SETTLING` or `LOWER_OFF` at the wake; `BYPASS_APPLIED source=LOWER_ON` on BOTH; no store overnight; whether the brief flash seen in the v1.7.7 C7 recurs is recorded |
| D-1 | Detection test: interception removed for the test by the owner's root helper, TOP, refresh change | `KERNEL_SIGNAL reason=KMSG_PRECURSOR`, `LOWER_POISONED`, dashboard fault line, one notification; no app lower ON; then reboot |
| D-2 | Detection cost | 1 h of normal use: daemon CPU before and after; sleep unchanged |

**Not planned physically: the `LOWER_FAULT` refusal on a real 1.7.7 daemon.** It needs a deliberately poisoned 1.7.7
boot, and it would use up the only 1.7.7 origin that C4 needs. The host evidence above stands for it.

**STOP conditions:**
- any `lock failed`, `Command transfer failed` or `-110` outside D-1;
- both screens black;
- a second service;
- an unexpected compositor restart;
- a C4 transfer that is held. That fails the owner's no-restart requirement. Record the refusal reason before any
  restart.

Publication is the owner's decision. The proposed minimum before stable is C4, C1, C2, C6 and C5.

## Known limitations

The limitations recorded for v1.7.7 are unchanged:
- the vendor driver defect remains;
- an already poisoned boot is not recovered;
- the accumulated-suspend explanation is not excluded;
- a first install still needs one restart;
- after every boot the kernel starts with `bypass_ram=0`.

The brief flash seen once in the v1.7.7 C7 is still unexplained. v1.7.8 does not change the timing of the held write.
