# S1-09 — admission and final display readback

**Programme:** JTF-QUALITY-2026-10-17 / S1-09. **Base:** integration `dba2733f48bf5102ae3e57ba4ab20dfa6b1755f2`. **Scope:** host characterization only; production source unchanged. Main and published v1.7.3 are unchanged by this work.

## Real path and explicit boundary

The existing coordinator suite compiles the production `DisplayActionCoordinator`, `DisplayDecisionModel` and `DisplayGenerationModel` unchanged and passes 139 checks. Its inert dependencies reject unexpected vendor requests; ordinary host execution cannot supply Android's Settings process.

The additional suite compiles a temporary coordinator copy with exactly one adaptation: the private `readCurrentMode()` OS process-read method returns a scripted mode. The production file is never rewritten. Admission, `requestFix`, `applyConfirmed`, generation handling, final confirmation and returned receipt logic are retained. CRTC reads and hardware outcomes are explicit host doubles. Scripted hardware requests only record a direction and return; no Android process, vendor function, property write or screen operation executes. The original suite keeps its fail-fast hardware default and runs in a separate JVM.

This adaptation does not test the real Settings subprocess, peer/vendor behavior or the provenance/age of supplied observations. The scripted mode reader checks the exact expected read count, including the final mode read.

## Cases

| ID | Scripted change / control | Observed host outcome |
|---|---|---|
| RB01 | BOTH already ON throughout | Confirmed; no hardware request |
| RB02 | BOTH requires ON, matching final reads | One inert ON request; confirmed |
| RB03 | TOP requires OFF, matching final reads | One inert OFF request; confirmed |
| RB04 | Mode changes before apply admission | MODE_CHANGED; no hardware request |
| RB05 | Mode changes at final read | UNCONFIRMED; no successful receipt/state commit |
| RB06 | Bottom CRTC contradicts an earlier successful poll | UNCONFIRMED; no successful receipt/state commit |
| RB07 | Top CRTC becomes incompatible | UNCONFIRMED; no successful receipt/state commit |
| RB08 | Final bottom CRTC becomes unknown | UNCONFIRMED; no successful receipt/state commit |
| RB09 | Top CRTC unknown before command | No speculative ON request |
| RB10 | Both CRTCs dark before command | No speculative ON request |
| RB11 | Initial CRTC unknown | DISPLAY_TRANSITION; no hardware request |
| RB12 | TOP final bottom contradicts earlier OFF poll | UNCONFIRMED; no successful receipt/state commit |
| RB13 | No command needed, then final CRTC drifts | UNCONFIRMED; no successful receipt/state commit |

Failure cases verify that the requested enabled state and success generation are not committed. Existing pending repair is preserved in the exercised final-failure path. The suite observes the existing failure alignment request as a counter; it does not execute or validate actual property alignment.

**Important limit:** a final rejection cannot undo a hardware command already submitted before a later change. These tests establish rejection of a false success receipt, not atomic read/apply/read behavior, guaranteed absence of all stale vendor writes, or physical screen luminance. The tested `requestFix` entry exercises the shared confirmation routine; it is not full watcher/repair/restart integration coverage.

## Reproduction and negative controls

Run on JDK17 with PowerShell:

```powershell
./scripts/test-display-coordinator-host.ps1
```

Observed: unchanged-source suite **139 checks PASS**; additional suite **13 cases /126 checks PASS**; two compiled negative controls detected. The controls mutate only the temporary copy: removing the final bottom check fails RB06; removing the final mode check fails RB05. The latter runs its targeted case so a prior positive control's read-count assertion cannot substitute for the intended false-receipt rejection. Expected JVM failures do not leak a nonzero status from a successful runner.

The temporary source/classes are removed only after checking the resolved cleanup path is directly inside the expected temporary parent and is not a reparse point. Test doubles remain under `tests/`; they are excluded from the product build.

**Evidence:** HOST PASS. Exact-head CI/CodeQL links and Android result are recorded in the source PR and subsequent combined acceptance. Local Android and **PHYSICAL THOR NOT RUN**. No signing, main merge, package identity, runtime timing, generation policy, updater, CPU or Guard behavior change. No cause or correction of issue #84 or #65 is claimed.

Next original matrix points: S1-10 failed/timeout hardware outcomes and bounded retry policy, then S1-11 lifecycle/restart. Those are not declared covered by this increment.
