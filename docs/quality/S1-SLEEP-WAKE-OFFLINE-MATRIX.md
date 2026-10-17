# S1 offline preflight — sleep/wake, mode generations and passive evidence

**Programme:** JTF-QUALITY-2026-10-17 / S1. **Status:** TEST PLAN, NOT EXECUTED by creating this document.  
**Hardware:** none. **Release:** none. **Target:** current main runtime based on published v1.7.3/code83.  
**Trackers:** [#84 persistent TOP black presentation](https://github.com/JestyLabs/Jesty-Thor-Fix/issues/84) (primary); [#65 transient BOTTOM bright flash](https://github.com/JestyLabs/Jesty-Thor-Fix/issues/65) (separate observational tracker).  
**Prior review:** [2026-10-17 evidence assessment](../ISSUES-65-84-ENGINEERING-REVIEW-2026-10-17.md). Do NOT infer shared cause or that #84 is caused by Jesty.

## Approach

First inspect source and existing host suites: DisplayActionCoordinator.onWatcherSample / applyConfirmed, DisplayDecisionModel.reconcile, boot-hold admission, generation invalidation, bounded wake repair and LidProtectionRuntime. Find real production adapters suited to deterministic test clocks and scripted mode/CRTC/interactive snapshots. Avoid duplicating production decision logic in the tests. Preserve the existing timing policy and panel command ordering for now.

| ID | Controlled input / interference | Proposed invariant or question | Offline tier |
| --- | --- | --- | --- |
| S1-01 | BOTH 1/1, no pending repair | No unnecessary BOTTOM power ON | Decision model + coordinator host |
| S1-02 | BOTH 1/0, app active and state known | Reconciliation only when currently authorized; no unbounded repeat | Decision model + coordinator host |
| S1-03 | TOP 1/1, normal watcher sample | Known BOTTOM OFF request bounded/confirmed only under permitted TOP context | Coordinator host |
| S1-04 | Unknown CRTC or screen not interactive | No speculative panel ON; preserve fail-closed path | Production decision host |
| S1-05 | Boot hold active during wake sample | No early hardware writes before boot safety release | Boot/coordinator coupled host |
| S1-06 | TOP wake repair pending, mode becomes BOTH | TOP repair generation is invalidated; no stale BOTTOM OFF after BOTH | Interleaving host |
| S1-07 | TOP -> BOTH -> TOP rapidly | Only currently authorized generation can act | Interleaving host |
| S1-08 | Duplicate wake/mode notifications | Idempotent/admission-bounded handling | Coordinator + clock host |
| S1-09 | CRTC changes between authorization/readback | Stale outcome rejected, no false applied/success receipt | Coordinator + clock host |
| S1-10 | Display apply timeout or vendor failure | No optimistic success, bounded retry/recovery policy | Production adapter/fixture |
| S1-11 | Daemon restart between watcher and pending repair | Old generation cannot mutate state or write a success acknowledgment | Lifecycle host |
| S1-12 | Guard OFF and closed lid wake | Guard path may not autonomously request sleep; distinguish Android/vendor/external source | Lid policy host |
| S1-13 | Guard ON, unknown lid/external display | No unsafe forced sleep on unknown state | Lid policy host |
| S1-14 | Diagnostics capture after multiple transitions | Monotonic ordering, bounded storage, redaction, no raw private identifiers | Real diagnostics host |
| S1-15 | Log event dropped / process killed before export | Explicit visibility gap, never invent an absent failed-transition trace | Analysis/report test |
| S1-16 | #65 BOTH wake with BOTTOM-only visual flash | Observe possible WATCH_BOTH correlation, but power ON is not brightness control; no brightness override | Read-only path review |

Here "1/1" means TOP/BOTTOM CRTC observed active and "1/0" means TOP active/BOTTOM inactive; **CRTC flags are not pixel light output**. The input snapshot and classification must be verified against actual production conventions before implementing fixtures. The expected outcome may be refined only with code evidence, not guessed hardware semantics.

## S1 execution order

1. Pin current main/head and existing tests; produce exact path->owner->side effect map.
2. Execute existing suites as baseline; report NOT RUN if Windows JDK17/Android SDK is unavailable.
3. Add **tests only** for S1-01 through S1-13 where the relevant real class can be exercised. Confirm they fail or pass against actual baseline without changing production behavior.
4. Characterize S1-14/15 privacy and time ordering. Only propose passive event fields if evidence is currently insufficient; bound CPU, memory, log lifetime and exported metadata.
5. Compare hypotheses: external sleep automation, stale mode/wake generation, vendor compositor/panel/power state. No causal conclusion without attributable evidence.
6. Produce a separately reviewable supervised physical capture specification (monotonic clock, power source, mode, Guard, firmware, active app, CRTC, compositor, automation ON/OFF) with STOP and independent recovery. Do not run it within the offline phase.

## Evidence output template

| Case | HEAD SHA | Test command | HOST result | EMULATOR result | THOR result | Notes/unknowns |
| --- | --- | --- | --- | --- | --- | --- |
| S1-01 ... S1-16 | not pinned yet | not run | NOT RUN | NOT RUN | NOT RUN | matrix preparation only |

**Exit gate:** A green synthetic model test cannot close #84, and absence of reproductions cannot prove safety. Keep #84 open until failure mechanism and physical confirmation; #65 remains distinct. No runtime change, signing or device session is authorized by this matrix.