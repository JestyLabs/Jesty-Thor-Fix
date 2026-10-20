# Display-stall cases — sanitized evidence, 20 October 2026

**Disposition:** the app-side containment and TOP cancellation corrections are implemented and released in [v1.7.4](../VALIDATION-1.7.4.md). Issue #84 is closed at the maintainer's request as software delivery. Physical acceptance and unknown hardware causality remain in [#95/S6](https://github.com/JestyLabs/Jesty-Thor-Fix/issues/95). This record does not declare the panel/DSI fault repaired.

These are sanitized descriptions of captured natural incidents, not raw logs or instructions to induce a blackout. Event order is retained; device/session identifiers, original timestamps, PIDs, file descriptors, fence identifiers, binary offsets and firmware are excluded.

## Case A — TOP wake, later blackout and delayed recovery

| Order | Evidence / interpretation |
| --- | --- |
| Wake and initial UI | The owner observed delayed upper-screen wake, followed by normal content before black presentation. Captured UI activity continued after an early app OFF command; that command alone does not establish onset of the blackout. |
| Captured lower resume | Lower display commands/commit completion failed within the observed window. Vendor reset work cycled displays while shared compositor work waited. Upper display completion alone did not establish visible frames reaching the screen. |
| Recovery attempts | The owner confirmed multiple Power/AYN attempts and connecting USB. These actions confound attribution of later wake/reset events; they must not all be classified as autonomous faults. |
| Image returned | Upper presentation later returned in TOP without a further reported touch at that moment. Lower OFF and reset-queue drainage support a containment hypothesis; neither USB nor one button press is proved to be the recovery cause. |

## Case B — live stalled compositor and a late app command

| Order | Evidence / interpretation |
| --- | --- |
| First captured wake | The lower path had a submitted commit without its matching completion. Upper completion progressed, while the vendor compositor waited on lower-display completion and held shared display/command locks. The captured loop began before a new app lower-panel command in that wake window. Earlier app state is not excluded as a contributing condition. |
| Blocked app caller | The watcher waited in the synchronous platform display-power operation. Platform mode could change independently while the app call was blocked. |
| TOP-to-BOTH crossing | A lower OFF admitted in TOP completed late after mode became BOTH; subsequent automatic BOTH ON could re-enable the failing lower path and prolong the loop. This is the app-controlled sequence targeted by back-off. |
| Later recurrence | Additional mode-button attempts were reported before another observed reset loop. This is not evidence of a completely input-free recurrence or independent proof of the initial electrical cause. |

## What the software tests establish

| Contract | Production-class proof / limit |
| --- | --- |
| Cancelled TOP repair | Pending state stays internal; cancellation does not leave the firmware-visible flag at OFF. Real Android sleep/wake and Battery Saver before/after wake use synthetic panels. |
| Late return across a mode change | A real JVM caller blocks at the synthetic platform boundary while an independent mode change occurs. The original code fails the no-re-ON assertion; the final code suppresses automatic ON after the slow return. This does not reproduce a vendor fence. |
| Slow call ending in error | Elapsed operation over five seconds still arms protection. It is a heuristic over the complete operation, not a unique vendor-fault diagnosis or enforced Binder deadline. |
| Lower isolation in BOTH | With back-off active and an admitted active lower CRTC, the existing OFF/readback path is used. Healthy BOTH, boot hold, disabled fix, unknown CRTC and stale mode retain their gates; refusal retains existing retry admission. |
| Real sleep/wake recovery | API33 PowerManager non-interactive to interactive clears protection in the tested sampled cycle. CRTC0/0 alone cannot clear it; a short or missed cycle can leave protection active. |

The [release record](../VALIDATION-1.7.4.md) links final host/Android/CodeQL and artifact provenance. The archived original and intermediate source controls fail for the intended assertions; compiled mutations demonstrate that the suite detects removal of safety conditions. No test claims a physical blackout was reproduced in an emulator.

## Closure and remaining boundary

The delivered app behavior is **resolved/implemented**: cancellation correctness, avoiding automatic re-ON after an observed stall, and admitted lower-panel isolation in BOTH. The first lower resume failure and the vendor reset mechanism remain outside the app's demonstrated correction. OFF can still wait behind a blocked compositor, and a call that never returns cannot arm this post-return logic.

The lower screen may remain off in BOTH during containment. Normal state can resume through the implemented mode/manual/observed-wake conditions and can encounter the underlying fault again. Exact released-APK presentation, recovery effectiveness and regression checks remain NOT RUN until the supervised physical session. Raw evidence remains preserved owner-local; closure does not erase uncertain or failed observations. The separate brightness-flash investigation is not reclassified as fixed by this release.
