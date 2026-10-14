# Native dashboard UI regression fixes

This follow-up addresses the two measured gaps from the
[earlier emulator audit](ENGINEERING-HARDENING-UI-EMULATOR-2026-10-13.md).
It is a small UI change within the draft maintenance workstream, with separate
characterization and fix commits. It is not complete accessibility acceptance
or physical Thor validation.

## Changes and local validation

| Step | Evidence | Result |
| --- | --- | --- |
| Native characterization (`766f8f3`) | Actual product APK, separate instrumentation; view bounds, active Android dialog trees, keyboard input and screenshots | Baseline reproduces undersized targets at all three font scales and clipped Diagnostics buttons at 150/200% in the narrower viewport |
| Diagnostics layout (`3865e55`) | Save Report/Close remain in the native footer; Sleep Trial entry moves into scrollable content; initial Close focus prevents the selectable preview from scrolling the entry out of view | Focused 200% regression passes Diagnostics/navigation/text; touch targets still fail before the next fix |
| Dashboard targets (`31fcdcc`) | Switches and action views now measure at least 48dp; existing click/long-click listeners are retained | Final matrix: 12/12 native UI checks PASS at 100%, 150%, 200% |

The final product was rebuilt with JDK 17 and signed only with a fresh disposable
test key. Dashboard host tests, public-content validation and release workflow
contracts passed locally. Exported report formatting and the Save Report/Sleep
Trial handlers are unchanged; no trial was armed and no report was exported.
The instrumentation opened and dismissed the actual Passive Sleep Trial entry.
No hardware switch, update download/installation or external link was activated.

The immersive Activity decor measures 1920 x 1080px, while its resource display
metrics report 1794px landscape width (density 2.625); native dialogs expose
software navigation and have narrower bounds. This explains why the original three-button
dialog also clips at 150% in this regression, while the prior 1920px-wide audit
showed clipping only at 200%. Both are pinned observations, not contradictory
results. Acceptance uses measured viewport/bounds rather than physical display
resolution. Hardware values are unavailable placeholders.

The [reproducible native regression](../tests/android/dashboard-ui/README.md)
runs against the exact CI unsigned product APK and an independently built
instrumentation APK. The existing Android job runs it last, after updater/TLS
tests, because it disables emulator network transports and replaces only its
disposable product installation. No production signing or publication job was
changed. Every new head needs its own CI result; a local PASS is not a CI PASS.

## Prior CI failure retained

The first Android execution at documentation head `7e6b59c`, run
[38432368167](https://github.com/JestyLabs/Jesty-Thor-Fix/actions/runs/38432368167),
timed out in `native-consent-accepted` while waiting for the app-local settled
state. No product source had changed in that documentation step. Three focused
local repetitions passed; the same-head failed-job rerun (attempt 2) passed the
full existing Android job. The original failure remains evidence, and its exact
cause is unisolated. No updater change or weakened oracle was introduced to
obtain a PASS.

## Enforcement and remaining acceptance

Read-only inspection found main's classic protection active with strict
up-to-date checks and admin enforcement. It currently requires `build`,
`Analyze (java-kotlin)` and `Analyze (actions)`, but not the stable Android job
or aggregate `CodeQL` check. Successful analysis execution does not establish
resolution of the separate security finding. A narrow required-check proposal
has been prepared owner-locally; no administrative setting was changed. Re-read
the live policy before applying any owner-approved change.

TalkBack speech, quantitative contrast, physical controllers, real telemetry,
report export, native daemon handover and affected physical paths remain separate
gates. The reserved security finding and IPC review were not inspected or changed.
The workstream remains draft: finish the independent security/enforcement gates,
freeze an exact tested head, and prepare one explicitly approved physical session
with rollback before any release decision.

Raw captures, failed intermediate layouts, logs, keys and source hashes remain
owner-local outside Git. Public evidence is limited to this sanitized engineering
summary and the reproducible tests.
