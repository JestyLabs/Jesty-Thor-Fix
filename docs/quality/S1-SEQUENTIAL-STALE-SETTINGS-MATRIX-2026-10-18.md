# S1 — sequential stale Settings observations, read-only characterization

**Programme:** `JTF-QUALITY-2026-10-17` / **S1**. **Base integration HEAD:** `3bfd7760e0441ff0cd08d03b552a99259ca32835` ([#101](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/101)). **Scope:** new host-only tests in the existing *real production* `DisplayActionCoordinatorHostTest`; **no application/runtime code modified**.

## Why these tests follow #102 and #103

[#102](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/102) showed a stale TOP overwriting a newer BOTH **only when two coordinator samples were artificially concurrent**. [#103](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/103) checked that the ordinary production watcher is a single serial Settings reader protected by one supervisor and an exclusive daemon process lock. That considerably narrows the synthetic parallel case. But **serial delivery is not equivalent to perfect temporal freshness**, nor do DRM/CRTC status bits prove displayed pixels.

The next bounded question is what happens when a Settings mode sample is delayed or inconsistent with observed CRTCs, **without creating new actual vendor power operations**.

| Case | Serialized fixture inputs | Confirmed by assertions | What cannot be concluded |
| --- | --- | --- | --- |
| S1-S01 | Known BOTH 1/1 → sampled TOP with lower CRTC UNKNOWN → BOTH 1/1 | Middle observation PENDING; next BOTH confirms; no power writes | Cannot show which Settings mode was physically current |
| S1-S02 | Known TOP 1/0 → sampled BOTH with upper CRTC UNKNOWN → TOP 1/0 | Middle observation PENDING; TOP recovery confirmed; no power writes | CRTC alone never proves illumination |
| S1-S03 | TOP due repair → **unknown/garbled Settings sample** → delayed display-on | Repair generation invalidated, pending cleared, no repair/DRM/power write | Does not run genuine Android callback interleaving |
| S1-S04 | TOP pending repair → BOTH mode edge while DRM read skipped → duplicate BOTH | Cancellation occurs *before* DRM snapshot; no stale repair/power action | Does not prove vendor scheduler behavior |
| S1-S05 | Production `DisplayDecisionModel.reconcile` receives syntactically valid but *possibly stale* TOP/1/1 or BOTH/1/0 | Model would classify OFF and ON respectively; unknown upper CRTC instead returns NONE | **NOT** proof of a real stale Settings read or unsafe panel command |

## Explicit risk classification

The coordinator only has the sampled Settings value and CRTC snapshot. Its potential vendor action path calls `applyConfirmed`, which performs a **second Settings query** with `settings get system dual_screen_display_mode` before attempting to change power, and validates again afterwards. The test harness intentionally **does not invoke or spoof the external Settings process or vendor panel API**. Therefore S1-S05 is a **conditional insufficiency of information**, not a confirmed safety vulnerability, not a proof the command would execute, and not a fix for [#84](https://github.com/JestyLabs/Jesty-Thor-Fix/issues/84). A later real Android/emulator capture would have to validate whether the two Settings access paths disagree and how the final confirmation behaves.

## Constraints and next gate

- Run `./scripts/test-display-coordinator-host.ps1` in pinned JDK17 CI. Existing test code uses real coordinator/decision/generation classes, inert host dependencies and fail-fast unexpected `DisplayHardware.apply`. No physical display or UI measurements.
- Preserve S0/S1/S5 CI checks, production source, daemon locking, 400/700 ms repair timing, boot hold, user selections and all IPC trust boundaries.
- Classify **HOST** outcomes separately from **Android emulator** generic CI and **Thor** physical validation.
- Continue with S1-14/S1-15 *existing passive diagnostics* monotonic ordering, bounded retention/redaction review, reusing existing suites instead of adding always-on logs.
- Maintain [#84](https://github.com/JestyLabs/Jesty-Thor-Fix/issues/84) and [#65](https://github.com/JestyLabs/Jesty-Thor-Fix/issues/65) as **unresolved independent issues**. No speculative brightness/power fixes.

**At PR creation:** S1 host / generic Android emulator / CodeQL tests on this exact branch still need CI evidence. **Physical Thor NOT RUN**. No change to `main`, version, signer, APK installation or release.
