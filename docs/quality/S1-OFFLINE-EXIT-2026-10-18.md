# S1 — offline coverage and unresolved boundaries

Programme **JTF-QUALITY-2026-10-17**. This records the S1 offline exit with
explicit unresolved status, as permitted by the original roadmap. It does not
close #84 or #65, accept a release, or authorize hardware work. The isolated
candidate remains #101; main and the published v1.7.3/code83 are unchanged.

## S1-11 increment: execute the watcher supervisor

Starting source: `87d2cf604bba993d5e792dbfc0365e53d9dcf9ef` (S1-10).
Existing real-model/coordinator tests already reject cancelled generations and
orphaned due timers. The producer contract checks the sole call site, sequential
dispatch, join-before-replacement and exclusive-lock-before-start topology, with
negative controls. Repeating those cases would not add evidence.

The remaining executable gap was the actual `WatcherSupervisor` loop. Run:

```powershell
./scripts/test-watcher-lifecycle-host.ps1
```

HOST **3 cases / 22 checks** execute the unchanged supervisor with real JVM
threads, its unchanged waits and a controlled monotonic clock. The Settings
watcher and cadence endpoint are inert fixtures. A blocked watcher prevents a
replacement; after it exits the supervisor replaces it without overlap; three
rapid exits terminate with FAILED. Health becomes STALLED beyond 5000 ms without
a sample and returns to RUNNING after a fresh sample. Cleanup joins all fixture
threads. Two compiled controls must fail: replacing join with a zero-duration
sleep, and admitting a fourth rapid retry. These detect behavioral drift rather
than merely the presence of source tokens.

This is a **watcher restart inside one JVM**, not a daemon process restart.
Existing coordinator generation tests plus the source-audited singleton startup
are partial S1-11 evidence. A watcher replacement does not itself invalidate the
coordinator generation. Java static generation state is process-local; a host
test does not prove that Android retired an old process or released a native
lock. Real daemon replacement and same-kernel handover remain S2/S3/S6 gates.
No persisted cross-process repair queue was identified in the audited path.

## Coverage reused, without duplicate suites

| Matrix point | Existing evidence | Residual boundary |
| --- | --- | --- |
| S1-01–05 | Real decision/generation matrix (#98), real coordinator admission (#100), boot/lid suite | Android/provider and vendor behavior; physical presentation |
| S1-06–08 | Cancel/mode-edge/orphan timer/duplicate cases (#102), producer topology (#103), sequential stale samples (#104) | Settings freshness cannot be inferred from known CRTCs; artificial concurrent callers are not the audited serial producer |
| S1-09 | Final mode/top/bottom confirmation (#108), including compiled negative controls | Non-atomic vendor readback and actual panel output |
| S1-10 | Real hardware wrapper and coordinator failure outcomes (#109) | Synchronous vendor duration is outside the CRTC window; retries have an interval, not a global count limit; rollback can fail |
| S1-11 | Existing generation/orphan cases and producer topology; new real-supervisor execution above | Native singleton lock, death/restart of a real daemon, actual smali watcher and scheduler execution |
| S1-12 | `LidIsolationTest`: real facade/runtime OFF path; coordinator Guard-OFF admission; boot/lid structural contract | Android/vendor/external sources of sleep cannot be attributed by these tests |
| S1-13 | Real `LidGuardModel` unknown-lid/environment, stale epoch, loop budget; coordinator invalid-epoch denial; runtime source checks | Hall/Binder/probe scheduling and the positive hardware sleep path remain unexecuted |
| S1-14–15 | Real diagnostics privacy, time ordering, bounded retention, observation gaps and process-loss tests (#105/#106) | Lost/unrecorded observations cannot be reconstructed; no invented event or causality |
| S1-16 | Read-only power-path review: no brightness override introduced; #65 remains separate | CRTC/power state is not pixel luminance; no measured flash mechanism |

Commands used across these existing boundaries include
`test-display-cpu-read-paths.ps1`, `test-display-coordinator-host.ps1`,
`test-boot-lid.ps1`, `test-app-diagnostics.ps1` and
`tests/test-watcher-producer-contract.py`. Exact source/combined CI and CodeQL
acceptance are recorded on the corresponding PRs and #95, never transferred
from another head. Generic emulator CI is not execution of the vendor fixtures.

## Exit decision

The requested offline S1 characterization now has a bounded, reproducible set of
real-class checks, targeted negative controls and explicit limits. No new
production correction or causal explanation of #84 is demonstrated. Additional
synthetic repetitions cannot close the listed native/hardware unknowns.

After source and exact combined-head checks pass, proceed to original **S2**:
review existing #94 and activation suites first, then fill only a concrete
handover/recovery fault gap. S3 follows privileged-boundary evidence, and S4
remains characterization before one neutral responsibility extraction. S5 stays
independent; actual enforcement changes require owner approval. S6 remains the
separately gated physical stage with the existing supervised capture/STOP plan.

No timing, generation, boot, allowlist, updater, CPU, display, Guard or IPC runtime
change. No main merge, signing, release or device test. No higher quality score
is asserted from test totals; independent assessment and physical evidence remain
necessary.
