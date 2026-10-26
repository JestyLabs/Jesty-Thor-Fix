# v1.7.5 / code85 — release validation (20 October 2026)

Published [stable v1.7.5](https://github.com/JestyLabs/Jesty-Thor-Fix/releases/tag/v1.7.5). Host, full generic Android, CodeQL, protected signing and publication passed. Physical acceptance was **NOT RUN at publication**. The owner explicitly authorized software-gated publication followed by owner testing and a later supervised session; this timing exception is not hardware evidence.

**21 October update:** the maintainer reports ongoing Thor testing of v1.7.4 and v1.7.5 with no black screens or other issues observed so far, and normal background-service transitions without errors. [Observation and planned v1.7.6 supervised retest](evidence/OWNER-OBSERVATION-1.7.4-1.7.5-2026-10-21.md) are recorded separately from the formal S6 matrix.

## Artifact and gates

| Item | Verified result |
| --- | --- |
| Source/tag | `576919815bc1f0c54e5d399a8db8fcba507bfded`, squash integration [#125](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/125) |
| PR software gates | [CI39429364660](https://github.com/JestyLabs/Jesty-Thor-Fix/actions/runs/39429364660), complete build and Android PASS |
| Main host/build/Android/signing | [CI39432417954](https://github.com/JestyLabs/Jesty-Thor-Fix/actions/runs/39432417954), all jobs PASS |
| Main CodeQL | [39432367783](https://github.com/JestyLabs/Jesty-Thor-Fix/actions/runs/39432367783), PASS |
| Protected publication | [39452119887](https://github.com/JestyLabs/Jesty-Thor-Fix/actions/runs/39452119887), PASS |
| Published APK | `Jesty-Thor-Fix-1.7.5.apk`, 9,303,515 bytes, package `com.thor.displaypowertest`, code85 |
| SHA-256 | `0be026c9e81c81c33edbf655fe8b639dc907406933aa96ecd608f9e3797dfff6` |
| Signature/payload | Established production certificate; signing preserves unsigned payload; downloaded release bytes match signed candidate; four native ABI libraries unchanged from v1.7.4 |

## Demonstrated behavior

[Containment status](CONTAINMENT-STATUS-1.7.5.md) no longer reuses an earlier successful isolation result after a failed, unconfirmed or mode-changed retry. Pending retries avoid reporting confirmed isolation. Display commands, retry timing, back-off resets, locks, CPU, Guard and updater behavior are unchanged. This is a diagnostic correction, not a new hardware recovery mechanism.

The actual coordinator/hardware host suite passes 173 assertions. Three new regressions reject frozen v1.7.4 source; comparable command effects remain equal. Eighteen compiled mutants and six frozen baseline controls are detected. Seven Android display cases pass, including failed/unconfirmed retry status, rate-limited waiting and successful recovery; Android scheduling/power reads are real while panels remain synthetic.

Exact published v1.7.4 is added to the existing predecessor policy. Four frozen published origins (1.7.1–1.7.4) pass host characterization and eight installed Android/JNI cases (native available/unavailable). Authentication, retained-process identity, confirmed exit, successor READY and conservative refusal remain separate requirements. Existing installer, updater, dashboard and native process-binding checks also pass in the complete software run.

Earlier failed gates remain recorded: the first build rejected an outdated default artifact name; a later Android run passed seven display cases and six prior-origin cases but rejected v1.7.4 at an outdated fixture-only preparation list. The build metadata and disposable fixture admission were corrected. The initial main attempt passed the display cases but stopped at an emulator ADB operation in the separate process-binding gate; the log did not identify the exact failed operation. Its evidence was preserved and incomplete jobs were retried on unchanged source. No failed assertion was waived, no production timeout was relaxed, and the final complete suites passed before publication.

## Pending acceptance

- Supervised acceptance with captured artifact identity, actual vendor-bridge live transfer, visible healthy transitions and ordinary sleep/wake: **NOT RUN** for this revision. The ongoing maintainer observation above is additional physical feedback, not a completed S6 result.
- Underlying lower-panel/DSI failure, reliable escape from a permanently blocked compositor and absence of future blackouts: **UNKNOWN**. [v1.7.4 containment boundaries](BLACKOUT-CONTAINMENT-1.7.4.md) remain in force.
- [#95/S6](https://github.com/JestyLabs/Jesty-Thor-Fix/issues/95) retains supervised physical and performance work; S4/AppUpdater remains the next offline workstream. No new numerical quality score is asserted. Refresh research, brightness research and the lower-screen inactivity timer are excluded.

Source, reproducible commands and sanitized fixtures remain public. Raw captures, complete local review artifacts, session identifiers and signing material remain owner-local. No device contact was performed for this release preparation.
