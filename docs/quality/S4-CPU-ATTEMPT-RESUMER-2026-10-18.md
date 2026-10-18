# S4 — isolate persisted CPU-attempt resumption

Programme **JTF-QUALITY-2026-10-17**, S4. Base source:
`369b26c` (S3 client characterization). One behavior-neutral responsibility;
this is not a boot/CPU/updater rewrite or a reliability-fix claim.

## Before changing production

`CpuFixController` owns import, status, restart state/persistence, helper recovery
and a retry-thread loop. Existing tests exercise its pure policies, but not that
controller's persisted-attempt resumption. The new fixture first ran against the
unchanged controller, before any production edit.

```powershell
./scripts/test-cpu-resume-host.ps1 -RunNegativeControls
```

HOST baseline: **6 cases / 31 checks**, with two compiled controls detected.
The actual controller, boot session and pure attempt/import/recovery models run
with inert stores/probes/wake lock. Only the resume loop's 200ms sleep is replaced
in a temporary source copy by a deterministic wait endpoint. No actual marker,
property, compositor/helper process or kernel wake lock is accessed. The original
controller source/hash and before/after logs are retained owner-local.

| Controlled state | Observed controller behavior, before and after |
| --- | --- |
| Absent marker | No attempt, wait or wake release |
| Already-applied valid successor proof | One attempt, no wait, one release; CONFIRMED |
| Corrupt marker | One attempt, terminal ERROR and one release |
| Two transient not-ready results then confirmed state | Three attempts, two waits, one release |
| Always not-ready | 130 attempts and 130 wait requests, then bounded-exhaustion log; no release from this branch |
| Interrupted retry wait | One attempt/one wait, interrupt flag restored; no release from this branch |

Exhaustion/interruption retain their existing behavior. This increment does not
invent additional wake release or alter the existing timed-lock ownership policy.
The real transition wake lock has its separate 150-second acquisition limit;
native expiration/ownership is not exercised by this host fixture.

## Minimal extraction

`CpuAttemptResumer` now owns only that loop and its unchanged retry-result
classification. `CpuFixController` still owns initial marker admission, thread
creation/name/daemon flag, desired-state reads, `apply` locking and effects, and
conditional wake release. Its existing classifier method delegates so
`BootCoordinator` does not change. Callback evaluation still reads the current
volatile desired state on each attempt; no state snapshot or new persisted format
is introduced.

The controller loses the embedded loop (33 removed lines in the scoped diff),
while the small worker adds explicit attempt/release dependencies. Total source
size is not reduced: the benefit is a single reviewable retry responsibility and
an executable boundary without changing restart/property/store machinery.

The identical coupled-controller fixture passes after extraction with identical
case outputs, attempt/wait/release counts and result states. Compiled controls
admit a 131st attempt or omit terminal wake release; both must fail the intended
assertions. Existing full boot/lid, IPC, activation and security suites remain
required. No deadline, assertion or failure is waived.

## Acceptance limits

Wait adaptation proves policy/order/counts, not wall-clock cadence, Android thread
scheduling, process death, native timed locks or vendor CPU behavior. It does not
cover every `CpuFixController` responsibility or extract the updater. Android
unsigned build/emulator and exact source/combined-head CodeQL remain explicit CI
gates; native affected-path acceptance remains S6 with separate authorization.

Production paths changed: **`CpuFixController.java` and new
`CpuAttemptResumer.java` only**. No model/store format, early hook, helper shell,
restart timing, lock order, allowlist, display, Guard, boot gate, updater, IPC,
package/version, signing or release change. No main merge or real Thor contact.
The candidate remains isolated in #101 and requires independent review plus
physical acceptance before any approved main/release promotion. A smaller
controller responsibility is evidence of maintainability work, not an assigned
independent quality score.
