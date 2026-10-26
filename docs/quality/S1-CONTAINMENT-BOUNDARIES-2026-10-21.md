# S1 — remaining display-containment boundaries

Programme **JTF-QUALITY-2026-10-17**, S1-06/09/10/14/15; tracker [#95](https://github.com/JestyLabs/Jesty-Thor-Fix/issues/95). Base `f53c41c14f70c8a00354af9185cff59f68b46414`. Investigation and tests only: production Java, smali, native code, APK identity, timings and policy are unchanged. The separate TOP update candidate, refresh and timer work are excluded.

## Problem and existing correction

The [captured cases](../evidence/DISPLAY-STALL-CASES-2026-10-20.md) separate a lower-path wake failure from later app reconciliation. In one captured window, the lower commit failure preceded a new app command; earlier app-created state remains a possible contributing condition. A delayed TOP OFF and subsequent BOTH ON could re-expose the failing path. These historical captures predate the delivered containment; they are not failures observed on v1.7.5.

[v1.7.4 containment](../BLACKOUT-CONTAINMENT-1.7.4.md) suppresses automatic ON after a slow app operation returns and can admit lower isolation in BOTH. [v1.7.5](../CONTAINMENT-STATUS-1.7.5.md) preserves current failed/unconfirmed retry status. Neither establishes the initiating electrical, link, panel or firmware cause. The existing 173-assertion production-class back-off suite, 18 compiled mutations and six frozen controls remain the baseline, rather than being replaced by another model.

## New executable characterization

Run with Python and JDK 17, from the source root:

```text
python scripts/test-containment-boundaries-host.py --negative-controls --output <fresh-output-directory>
```

The runner compiles the actual coordinator, hardware wrapper, decision/generation models, hardware profile and **WatcherSupervisor** without production-source adapters. It reuses the existing synthetic Android/panel fixtures. Only the fixture health constant is replaced by the real supervisor, with inert cadence/log overloads and a watcher thread that must never execute. The fixture log list is synchronized and its clock is volatile. Each case runs in a fresh JVM. All threaded paths release their latch in `finally`, join their workers and have an external process deadline. No Android, ADB, Binder, vendor firmware or physical panel runs in these HOST cases.

| Case | Current behavior characterized | Remaining boundary |
| --- | --- | --- |
| `no-total-cap` | 25 failed OFF retries are admitted, with 1499 ms refused and 1500 ms admitted between same-key attempts; errors retain protection | Admission interval is not a total retry cap or evidence that repeated OFF improves physical recovery |
| `fast-errors` | 16 fast ON exceptions fail without arming elapsed-call protection | Repeated fast failures have no separate containment trigger |
| `threshold-one-over` | 5001 ms arms protection; existing baseline retains the exact 5000 ms control | A duration heuristic does not diagnose a particular vendor failure |
| `reset-top`, `reset-bottom` | Leaving BOTH for either mode clears the back-off; since the S1 boot latch ([S1-LOWER-FAILURE-LATCH-2026-10-26.md](S1-LOWER-FAILURE-LATCH-2026-10-26.md)) a later BOTH observation no longer requests ON until reboot or manual reconcile | A mode edge does not prove the lower path is healthy |
| `unknown-mode`, `unknown-power` | Unknown mode or missing PowerManager cannot themselves clear protection or authorize an automatic ON in these cases | Conservative refusal may persist; unknown does not establish recovery |
| `missed-power-cycle` | A false/true power cycle between rate-limited queries is missed and protection remains | The one-second query cadence is an observation limit, not a proven timing defect |
| `sampled-power-cycle` | A sampled false-to-true edge clears the back-off; since the S1 boot latch, ON stays suppressed until reboot or manual reconcile | Neither the edge nor the latch is a panel-health test |
| `transient-crtc` | CRTC 0/0 followed by active panels does not clear protection without observed power sleep/wake | Reset phases must not be confused with an actual system sleep |
| `no-app-call-trigger` | Selected CRTC patterns without a slow app call do not arm protection; ordinary BOTH ON remains eligible | CRTC flags alone are not a vendor-reset or black-frame detector |
| `blocked-return`, `blocked-error` | A sustained synthetic platform block holds cancellation and a pending-state query on the hardware monitor. Real supervisor health becomes STALLED while action status can retain prior CONFIRMED. No completed-call log or new back-off is emitted until return. Late normal/error return is UNCONFIRMED/APPLY_FAILED and arms protection | Prior action status must be interpreted with watcher health. A blocked operation is not cancelled by a later timeout, mode change or pending query |
| `concurrent-property-rollback` | An independent synthetic property write during a failed hardware call is overwritten by rollback to the known previous value | Local serialization does not make the global property transactional; this interleaving is not attributed to the physical incident |

These are **characterization PASS results**, including behavior that remains an open design question. They are not claims that unlimited retries, stale status or every reset condition are desirable. Four compiled controls must fail at their intended assertions: allowing unknown mode to clear protection, removing the BOTTOM reset, removing the cancellation monitor, and logging completion before the platform call returns. A compilation failure is never accepted as a detected mutation. The monitor control tests current serialization, not an endorsement of keeping that lock boundary in any future redesign.

Local HOST execution passed **14 cases / 156 assertions and all four compiled controls**. With the S1 boot latch (2026-10-26) the same runner passes 14 cases / 158 assertions and a fifth control (`latch-cleared-with-mode`, rejected at RESET-03). The unchanged back-off baseline passed separately, as did the existing coordinator readback, failure/rollback and watcher-lifecycle suites. These counts describe the executed synthetic contracts, not exhaustive coverage of every possible interleaving or hardware condition.

The initial local preparation exposed a missing two-argument Log.e overload in the reused HOST fixture when compiling the real supervisor. The runner adds that inert adapter; no product source or assertion was weakened. Full logs and source hashes remain in the owner-local evidence package. Exact-head CI and CodeQL are separate acceptance records.

## Decisions still open

1. **Retry budget:** evaluate a finite per-episode OFF budget versus current interval-only admission. Decide what ends an episode, how a failed/unknown attempt is represented, and what happens when the budget is exhausted. Existing evidence supports lower isolation as a candidate; it does not establish the optimal number of attempts. No cap is implemented here.
2. **Restoration:** evaluate retaining protection across mode/power edges versus the current restoration contract. A latch requiring explicit user retry would intentionally keep the lower panel off longer, including in BOTH. Physical benefit and usability are unproved. No reset condition is changed here.
3. **Blocked-operation ownership:** first specify observable operation identity, start, target, generation, elapsed state and terminal/unknown outcome. Reuse existing STALLED health rather than claiming there is no stall indication. Any later separation of state coordination from blocking platform I/O must preserve a single admitted hardware operation, stale-result rejection, boot/fix/mode gates and handling of late success/error. A worker timeout does not cancel the submitted Binder operation; starting a competing recovery command is not justified.
4. **Shared property:** determine whether error rollback can safely preserve a newer external state with available interfaces. A compare-before-write remains racy and a blanket restore-to-ON is not a demonstrated remedy. Keep source-level possibility separate from incident attribution.
5. **Initial failure:** resolve what made the first lower wake commit fail, using preserved raw traces and exact firmware. The reset/wait chain and later app re-exposure are already investigated; do not relabel them as the missing initiating cause. Prior true-off state is not excluded just because the new command arrived later. Correlate clocks, caller identity and recovery inputs before assigning causality.

These decisions require separate minimal proposals and falsifiable before/after tests. This PR does not select a new runtime policy or provide a physical fault-injection protocol.

## Evidence and execution boundaries

HOST tests use real production classes with explicitly synthetic clock, scheduler, CRTC, Settings, property, PowerManager and SurfaceControl boundaries. The real supervisor's health methods run; its watcher/restart loop does not. Sustained blocking is deliberately released, not proof of an infinite Binder hang. Initial panel fault, actual fence/DSI behavior, writer identity and optical recovery remain UNKNOWN at this tier.

The unchanged generic Android CI remains applicable to ordinary lifecycle/display scheduling with synthetic panels. It does not execute these new HOST fault cases against the Thor vendor implementation. Local Android/device tests are NOT RUN. Existing owner-reported observations of v1.7.4/1.7.5 are separate evidence and do not establish that this rare fault is absent.

All full captures, firmware, session identifiers, comparison research and detailed review material remain owner-local. The offline parallel-analysis package contains the raw context and a source/hash retrieval map. It grants no operational authority: no Thor contact, install, wake, input, settings/property write, helper execution, root request, restart, kill, signing, merge or release. Stop on source drift, an unexplained existing regression or a need to change privileged/runtime policy. **NO SIGNING / NO RELEASE / NO DEVICE TEST.**
