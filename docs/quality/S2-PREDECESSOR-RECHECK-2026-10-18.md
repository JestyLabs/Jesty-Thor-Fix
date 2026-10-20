# S2 — predecessor recheck boundary

Programme **JTF-QUALITY-2026-10-17**, S2. Source base:
`da35fc0c2cfdc35bd6b8150fec72e4aea214a226` (S1 offline exit). Test-only;
no runtime, allowlist, protocol, helper command or activation format change.

## Gap

The existing activation harness executes real `AutoService`, `PServer` and
activation classes. It already covers guarded version admission, unsafe initial
state, failed bridge submission, stop-without-effect, no duplicate successor,
missing/corrupt persistence and STARTING versus READY. #94 already tests ambiguous
I/Q fields. Those cases are reused rather than copied.

It did not vary observations **between** the two calls to
`PServer.safeLivePredecessor` inside `stopLivePredecessorIfSafe`. Policy-only tests
cannot show whether that second confirmation gates bridge submission.

## New executable evidence

```powershell
./scripts/test-reboot-activation-host.ps1 -RunNegativeControls
```

With JDK17, **5 cases / 25 checks** execute the unchanged `PServer` helper:
stable two-round I/Q observations permit one inert stop submission; changed PID,
missing second identity, changed second mode or missing second snapshot deny all
stop submissions. The helper never launches a successor. Tests verify the exact
I/Q request order as well as side effects.

One compiled negative control removes only the second predecessor confirmation
from a temporary copy. The changed-PID case must reject it for an unexpected
bridge submission, before checking read counts. Existing service-gate and
same-kernel authorization controls remain enabled. Existing activation suites
continue to pass independently.

The fixtures script responses and simulate Binder submission; they do not run a
shell, send signals, authenticate native peers or retire a real process. The
production class and admission policies are compiled from the candidate source.
The pre-existing harness adapts only the documented legacy transport probes and
service polling wait; the handover helper is unchanged.

## Quality impact and limits

The second identity/state check now has a behavioral regression test at its
actual bridge-admission caller. This improves fault coverage without changing
privileged behavior or expanding predecessor support.

Two consistent observations are **not atomic process-incarnation proof**. A PID
change can be rejected; PID reuse with indistinguishable observed fields is not
excluded by these tests. A bridge ACK still is not process exit. The existing
service harness characterizes no-effect ACK handling, while real lock retention,
peer credentials, process death, authenticated successor identity and firmware
compatibility remain native/physical acceptance requirements. Legitimate
predecessor response-format compatibility for #94 is a separate gate; synthetic
reply strings do not close it.

HOST PASS. Generic Android CI and exact source/combined-head checks are recorded
on the PR and #95; they do not elevate this fixture to a native handover trial.
PHYSICAL NOT RUN. No signing, release, main merge or Thor contact. Any new
privileged finding must follow the private evidence and independent review gates
before a runtime correction. S3 follows without treating this slice as complete
kernel/vendor assurance.
