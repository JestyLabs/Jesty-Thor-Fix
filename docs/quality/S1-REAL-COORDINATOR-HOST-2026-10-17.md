# S1 — actual DisplayActionCoordinator host characterization

**Programme:** JTF-QUALITY-2026-10-17 / **S1**. **Scope:** test-only. **Baseline:** main `98ab7c342a028eaea8e43fb867900c0f9a3b5152`, stable v1.7.3/code83. **Status:** code and assertions prepared; CI execution must be checked at the final PR HEAD. No product or device acceptance is claimed.

## Why this is the next increment

PR [#98](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/98) tested the **real pure production decision/generation models**. The next missing tier is their caller: [`DisplayActionCoordinator.java`](../../src/com/thor/displaypowertest/DisplayActionCoordinator.java), including mode-edge cancellation, stable TOP wake scheduling, Guard deferral and failure admission.

We compile the **production coordinator source unchanged** together with the real `DisplayDecisionModel` and `DisplayGenerationModel`, replacing Android/vendor surfaces with small **inert host fixtures**. Any unexpected vendor display power request raises an assertion. The fixture does **not** execute a real Settings process, Android Binder, SurfaceControl, actual DRM, Hall hardware, or Android sleep. It cannot prove black-screen recovery.

## Scenarios executed by the harness

| Case | Real coordinator method | Invariant / expected result | Tier |
| --- | --- | --- | --- |
| S1-C01 | `onWatcherSample(null)` | Unknown mode never reads DRM or issues panel command | Host with inert boundaries |
| S1-C02 | `onWatcherSample`, `requestFix` | Boot hold prevents panel work and rejects manual request before shell | Host |
| S1-C03 | `onWatcherSample`, `scheduleWakeRepair` | Disabled fix cancels/avoids repair | Host |
| S1-C04 | `scheduleWakeRepair`, `noteDisplayOn`, `cancelWakeRepair` | Only active TOP state admits a repair; cancelled repair ignores later display ON | Host |
| S1-C05 | `onWatcherSample` | Stable TOP wake defers repair; already-OFF due repair completes without vendor writes | Host |
| S1-C06 | `onWatcherSample` | TOP→BOTH mode edge invalidates a pending TOP repair | Host |
| S1-C07 | `onWatcherSample` | Unknown CRTC / skipped DRM sample cannot trigger vendor commands | Host |
| S1-C08 | `onWatcherSample` | Guard deferral does not schedule a normal repair | Host |
| S1-C09 | `onWatcherSample`, `cancelWakeRepair` | Guard callback blocked outside hardware lock does not prevent cancellation | Host thread interleaving |
| S1-C10 | `requestGuardSleep` | Invalid lid epoch denies external sleep action | Host |

The test suite deliberately avoids opening any `settings`, `input` or vendor process; **only safe/fail-closed branches** are exercised. Production paths that would need an actual binder, property writes or process execution remain NOT RUN. At each asserted path `DisplayHardware.apply()` is an inert fail-fast fixture, not an implemented display operation.

## Limits / further work

- This does NOT reproduce [#84](https://github.com/JestyLabs/Jesty-Thor-Fix/issues/84) persistent black presentation, whose cause is unknown, nor the independent [#65](https://github.com/JestyLabs/Jesty-Thor-Fix/issues/65) BOTTOM flash.
- Android/Thor/vendor validation, power ON/OFF confirmation, actual delayed timers, screen luminosity, process lifecycle, and long TOP sleep/wake are **NOT RUN**. Lock behavior within the artificial Guard callback is host-characterized only.
- A later S1 increment may use a disposable emulator fixture for complete `applyConfirmed` paths and capture generation vs observed mode edge; it must not assert simulated outcomes as physical CRTCs. **No speculative runtime display fix** is authorized by these tests.

## Operator gates

The host workflow runs `./scripts/test-display-coordinator-host.ps1` using JDK17. Record exact PR SHA, result, CodeQL, CI and whether the emulator is skipped. Do not merge with S5 diagnostic PR #99 or S2 handover PR #94; keep separate scopes and resolve workflow overlap before integration. No APK identity, production Java, Smali, signing, deploy or real device work is touched.
