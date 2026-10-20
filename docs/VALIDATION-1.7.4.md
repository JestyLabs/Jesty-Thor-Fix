# v1.7.4 / code84 — release validation (20 October 2026)

Published [Latest stable v1.7.4](https://github.com/JestyLabs/Jesty-Thor-Fix/releases/tag/v1.7.4). This record separates the completed software gates from physical acceptance, which is **NOT RUN for this revision at publication**. The maintainer explicitly authorized publication after host/emulator verification, followed by overnight observation and supervised Thor checks. This timing exception does not establish device acceptance or change future gates.

## Exact artifact and gates

| Item | Verified result |
| --- | --- |
| Source/tag | `8fb93cbd67f0fde4b35859fb7ddc4eab067ab24c`, squash integration [#101](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/101) |
| Accepted candidate before squash | `d80607f16fa8757283281874f612b8914eaba9bb`; complete repository tree identical to integrated source |
| Main host, build, Android and protected signing | [CI39366170358](https://github.com/JestyLabs/Jesty-Thor-Fix/actions/runs/39366170358), all jobs PASS |
| Main CodeQL | [39366169047](https://github.com/JestyLabs/Jesty-Thor-Fix/actions/runs/39366169047), PASS |
| Protected publication | [39368033874](https://github.com/JestyLabs/Jesty-Thor-Fix/actions/runs/39368033874), PASS |
| Published APK | `Jesty-Thor-Fix-1.7.4.apk`, 9,303,515 bytes, package `com.thor.displaypowertest`, versionCode 84 |
| Published SHA-256 | `895f2e980c193c520afe5426e7a8bdda6a14b2f24e84cf22344d63f29b29da9d` |
| Signature/payload | Established production certificate verified independently; all 16 unsigned payload entries unchanged by signing; downloaded release hash matches signed candidate |

## Demonstrated changes

- **S1:** pending TOP repair cancellation preserves the firmware-visible state; delayed return replay and stalled-display containment use the production coordinator/hardware classes. [Containment behavior, investigation and limits](BLACKOUT-CONTAINMENT-1.7.4.md).
- **S2/S3:** [process-bound retirement](quality/S2-PROCESS-BOUND-RETIREMENT.md) admits exact published 1.7.1/1.7.2/1.7.3 predecessors only under the existing safety gates. Instance ownership, old-process exit and successor READY remain distinct checks; parser hardening rejects duplicate identity fields. Unsupported or unverified paths retain conservative recovery.
- **S4:** [CPU attempt resumer](quality/S4-CPU-ATTEMPT-RESUMER-2026-10-18.md) extraction is covered against the actual controller; this is not a new frequency/governor change or a fresh performance claim.
- **S5:** updater lifecycle, trust, diagnostics/privacy and failure evidence retain their host and Android test coverage. The offline programme records remain dated; release publication does not certify S6 complete or establish a new numerical quality score.

## New display evidence

The real coordinator/hardware host suite covers 69 back-off/degradation assertions, alongside 29 wake-coordinator, 17 bottom-wake and 31 stall-observation assertions. The frozen original fails the intended back-off and blocked-return replay assertions; the intermediate back-off-only source fails the degradation assertion. Sixteen compiled negative mutations are rejected for the intended behavioral assertions. The threaded replay reproduces an app command admitted in TOP, platform transition to BOTH while blocked, and late return without automatic re-ON. It does **not** reproduce the panel fault or the vendor compositor.

Five generic API33 Android cases passed: ordinary sleep/wake, Battery Saver restoration before and after wake, slow failed-call back-off followed by a real power cycle, and degraded-BOTH followed by a real power cycle. Android PowerManager Binder and sleep/wake are real; panels are synthetic. Existing full-app installer, updater, dashboard, native process binding and six installed predecessor retirement cases passed in the same final run. JNI tests do not exercise the Thor vendor bridge.

Earlier failed CI remains visible: a historical-source control initially depended on unavailable shallow Git history; frozen hash-verified fixtures removed that dependency. A second Android scenario initially retried inside the unchanged 1.5-second admission interval; an isolated real-class proof identified the harness setup error, and the fixture now waits beyond that interval. Neither failure was waived and no product timeout or assertion was relaxed. The corrected full suites passed before publication.

## Pending / unknown

- Exact released APK on the Thor, same-boot upgrades, visible healthy transitions, physical sleep/wake and containment effectiveness: **NOT RUN for this revision**.
- Root electrical/DSI/panel/firmware cause, reliable recovery from an already blocked compositor and absence of future blackouts: **UNKNOWN**. A call that never returns cannot trigger this post-return containment.
- Active Guard interception, BOTTOM/dock coverage and new CPU/energy/frame-time measurements remain outside this release's evidence. Historical PASS/FAIL records are preserved.
- [#84](https://github.com/JestyLabs/Jesty-Thor-Fix/issues/84) remains open for physical follow-up; [#65](https://github.com/JestyLabs/Jesty-Thor-Fix/issues/65) brightness research is separate. Refresh research and the lower-screen inactivity timer are excluded.

Reproduce the software suite with the repository's documented commands and pinned CI toolchains; display-specific entrypoints are `python scripts/test-display-backoff-host.py --output <fresh-directory>` and `scripts/test-display-wake-coordinator.ps1`. Raw captures, archived firmware, session identifiers and private signing material remain owner-local.
