# Physical validation & performance evidence

## Pre-release: v1.7.6 / code86 (28 October 2026)

[Candidate acceptance and both physical rounds](VALIDATION-1.7.6-PENDING.md): F1/F2, E2, E4, D2, C1 and C3 PASS on the published signed APK; the live transfer from published 1.7.5 in TOP was held and completed by one normal restart; the sleep-order race fix is host-tested but not yet exercised on hardware. Published as a pre-release; the current stable release below is unchanged.

## Current release: v1.7.5 / code85 (20 October 2026)

[Release validation and artifact identity](VALIDATION-1.7.5.md) record host, generic Android, CodeQL, signing and downloaded-asset checks as PASS. **Physical acceptance of this revision is NOT RUN at publication**, under an explicit maintainer timing exception. [Blackout containment](BLACKOUT-CONTAINMENT-1.7.4.md) is a tested software mitigation awaiting device acceptance; the earlier physical results below retain their original version boundaries.

**21 October maintainer feedback:** ongoing device testing of v1.7.4 and v1.7.5 has shown no black screens or other issues so far; background-service transitions have worked without observed errors. [The dated observation](evidence/OWNER-OBSERVATION-1.7.4-1.7.5-2026-10-21.md) also records the planned complete supervised physical retest for v1.7.6. This does not mark the formal S6 matrix complete.


## RC78 hardening session — 15–16 October 2026

The [scoped physical closure](PR67-PHYSICAL-VALIDATION-2026-10-16.md) records **15 PASS / 1 historical FAIL** in T00–T15, plus separate A1 BOTH CPU acceptance PASS. A1 median was **0.088457127% of one core** (ceiling **0.188455954%**); T10 TOP median **0.324422937%** (ceiling **0.424110373%**). Original T09 BOTH remains **FAIL**, median **0.285730014%**; variability is unexplained. [Sanitized RC78 window inputs](evidence/PR67-CPU-WINDOWS-2026-10-16.csv) preserve the separate series.

Normal short lid recovery and UI/diagnostic/final-state checks passed only in their documented scope. Recovery79 was staged/abandoned, not installed; active blocking, prolonged TOP sleep and distribution remain outside acceptance. See [integration and release gates](PR67-INTEGRATION-GATES-2026-10-16.md). Earlier measurements below retain their original versions and conditions.

**Jesty Thor Fix — engineering evidence index** · Updated 2026-10-16

Jesty Thor Fix is developed and tested against real AYN Thor hardware. This page collects the strongest reproducible findings, separates released behavior from experimental candidates, and links each conclusion to its test method. The primary criteria are **functional correctness, efficient background operation, dependable sleep/wake, and safe recovery**.

## Validated results at a glance

| Area | Physical result | Evidence |
| --- | --- | --- |
| **True Bottom Screen Off** | In TOP mode, the lower display pipeline changes from active to inactive; normal state is restored in BOTH and repaired after wake. | [Device validation](DEVICE-VALIDATION.md), [live hardware results](LIVE-RESULTS.md), [A/B/A benchmark](BENCHMARKS.md) |
| **Dashboard CPU Fix** | Sustained high CPU-cluster frequencies were reproduced under light load; after enabling the fix, the focused BOTH run recorded 0/25 simultaneous maximum-cluster samples. | [1.1.0 physical validation](DEVICE-VALIDATION.md#110-two-fix-validation), [live results](LIVE-RESULTS.md#110-dashboard-cpu-fix) |
| **Watcher optimization — v1.7.0** | Experimental predecessor measured **89.41% less daemon CPU in BOTH** and **89.06% less in TOP** versus v1.6.0, with approximately 95% fewer voluntary watcher context switches. The exact signed v1.7.0 release separately passed device acceptance. | [Controlled A/B](WATCHER-VALIDATION-2026-10-08.md), [production-candidate acceptance](VALIDATION-1.7.0-PENDING.md) |
| **RC2 awake-idle CPU** | **0.2894% of one core** (BOTH median); **0.3223%** (TOP median), from three physical windows of ~183 s per mode. | [RC2 physical baseline](evidence/RC2-PHYSICAL-BASELINE-2026-10-12.md), [recalculation data](evidence/RC2-CPU-IDLE-2026-10-12.csv) |
| **RC2 suspend** | Estimated **743.2–743.6 s in BOTH** and **750.6–751.0 s in TOP** over two controlled unplugged sleep trials, with the fixes active and continuous boot/daemon identity at sampled endpoints. | [RC2 physical baseline](evidence/RC2-PHYSICAL-BASELINE-2026-10-12.md), [clock-derived intervals](evidence/RC2-SUSPEND-2026-10-12.csv) |
| **Code76 — physical CPU Fix recovery** | Signed in-place code74→76 upgrade **PASS**; authenticated daemon handoff, verified CPU Fix confirmation, normal BOTH/TOP display and short sleep/wake checks. No unintended compositor restart observed during handoff. | [Candidate76 V6 hardware report](evidence/CODE76-PHYSICAL-VALIDATION-2026-10-12.md), [PR #68](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/68) |
| **Code76 — Lid Guard ON basic recovery** | Owner-supervised BOTH/TOP close/Power/open cycles recovered normally, Screen/CPU Fix stayed healthy, and Guard OFF was restored. **PASS for normal-cycle recovery; active blocked-wake interception NOT OBSERVED** (counter 0, no matching sleep action). | [Code76 physical report](evidence/CODE76-PHYSICAL-VALIDATION-2026-10-12.md) |
| **Code76 — sustained suspend** | **PASS** in BOTH (**812.828–813.138 s**) and TOP (**776.449–776.860 s**) measured as bounded Android suspend time. No >5 pp decline in suspend share versus the RC2 control; normal wake confirmed. | [Physical report](evidence/CODE76-PHYSICAL-VALIDATION-2026-10-12.md), [recalculation CSV](evidence/CODE76-SUSPEND-2026-10-12.csv) |
| **Code76 — daemon idle CPU** | In six ~182 s physical windows, **0.0879% one-core median in BOTH** (−69.6% vs RC2) and **0.3172% in TOP** (−1.6% vs RC2). Both pre-declared CPU budgets **PASS**. | [Physical report](evidence/CODE76-PHYSICAL-VALIDATION-2026-10-12.md), [recalculation CSV](evidence/CODE76-CPU-IDLE-2026-10-12.csv) |
| **Update and wake recovery — v1.7.0** | Exact signed release verified after in-place handoff, BOTH/TOP transitions, two TOP wake repairs and a supervised TOP reboot. | [Signed release validation](VALIDATION-1.7.0-PENDING.md), [release notes](RELEASE-NOTES-1.7.0.md) |

These are **physical tests with observed outcomes**, not estimates from source-code complexity. The linked studies give the test configuration and raw or sanitized input data.

## What the measurements say

### Background CPU

The watcher A/B demonstrates that significant reductions in daemon CPU and scheduling overhead were achieved **without abandoning display-state safety checks**. The RC2 baseline measured roughly **0.3% of a single CPU core** during steady awake idle; the later, separately identified code76 physical trial recorded **0.0879% in BOTH** and **0.3172% in TOP**. Both datasets contain three matched daemon CPU windows per mode, with input ticks publicly available. Individual windows and the calculation are public.

### Suspend and normal background operation

RC2 completed both quiet-sleep experiments with **743.2–743.6 s** (BOTH) and **750.6–751.0 s** (TOP) of calculated suspend. The later code76 candidate also **passed BOTH** (**812.8–813.1 s**) and **TOP** (**776.4–776.9 s**) under its separately measured windows. The observed device/daemon continued to allow normal Android suspend in both modes. Percentages include awake endpoint handling, and neither the time nor share alone establishes a battery-energy gain.

### Correctness and safety

The project's release records cover exact-APK identity, established signing, in-place updates, display state verification, boot-phase CPU Fix recovery and normal sleep/wake repair. Candidate76 subsequently passed physical recovery of a CPU Fix confirmation problem seen with code74. Software tests and CI cover additional failure paths; physical conclusions are attached to the exact version actually installed.

## Version boundaries

| Version | Status |
| --- | --- |
| **v1.7.1 / code 76 — [published stable](https://github.com/JestyLabs/Jesty-Thor-Fix/releases/tag/v1.7.1)** | Published 2026-10-12; [release identity and CI provenance](RELEASE-INTEGRITY.md#171-stable-publication--verifiable-public-metadata-2026-10-12) recorded. Final release includes approved UI-only changes relative to the separate physically tested signed code76 candidate; active Guard wake interception remains unobserved. |
| **v1.7.0 / code 71** | Previously published stable; physically accepted |
| **v1.7.1-rc2 / code 73** | Signed device-test baseline; CPU and suspend measurements completed on 2026-10-12 |
| **v1.7.1-rc3 / code 74, [PR #63](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/63)** | Installed physically; BOTH recovered, but CPU Fix confirmation failed after handoff. Superseded by the reviewed candidate76 fix for ongoing testing |
| **1.7.1 / code 76 test candidate, [PR #68](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/68)** | Separate physically tested signed candidate: **smoke, BOTH/TOP idle CPU, BOTH/TOP sustained suspend and basic Guard ON recovery PASS**. The later v1.7.1 stable release was published with approved UI-only differences; active blocked-wake interception remains unobserved. |
| **1.7.1-rc6 / code 77, [PR #69](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/69)** | Signed forward-only recovery prepared, **not installed** |
| **Earlier recovery / codes 75, [PR #64](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/64)** | Historical candidate only; not a normal rollback from code76 |

Physical acceptance is recorded per tested build. Code76's matched Guard OFF CPU and quiet-suspend comparisons and isolated Guard ON **normal-recovery smoke** are complete. An effective closed-lid wake interception was not observed, so no active-blocking guarantee is inferred. Final v1.7.1 stable release publication completed on 2026-10-12; [public asset digest, signer-policy verification and exact source/CI run](RELEASE-INTEGRITY.md#171-stable-publication--verifiable-public-metadata-2026-10-12) are recorded separately. Physical acceptance remains scoped to the tested signed candidate; the later released APK was not byte-identical due to reviewed UI-only changes.

## Evidence standard

We publish clear outcomes, enough conditions to reproduce calculations, and sanitized numerical data. Test states distinguish **PASS**, **FAIL** and **NOT RUN**. A limitation is recorded only where it changes interpretation: for example, **CPU ticks measure process work; suspend clocks measure time, not energy; gameplay frame-time and calibrated energy experiments are separate tests**. That does not reduce the completed CPU and suspend results to inconclusive findings.

For publication and third-party IP boundaries, see the [public evidence policy](PUBLIC-EVIDENCE-POLICY.md). Raw device identifiers, proprietary firmware extracts and private debugging captures are deliberately excluded from this evidence hub.
