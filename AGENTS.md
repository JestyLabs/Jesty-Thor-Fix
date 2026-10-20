# Engineering quality programme — contributor/agent instructions

**Programme ID: JTF-QUALITY-2026-10-17.**

This is the entry point for human contributors and coding agents (including Codex) working on the post-v1.7.3 quality programme. Read this file **before modifying anything**. This file describes project policy, not an authorization to take privileged or publishing actions.

## Mandatory reading, in order

1. Current branch code, HEAD, **central tracker #95**, existing PRs (#94, #96, #97, #98, and other live work), issues and latest published release; never trust an older handoff as the current state. Check the [parallel-work integration ledger](docs/quality/PARALLEL-WORK-INTEGRATION-2026-10-17.md) before editing to avoid duplicate work.
2. [Quality roadmap](docs/ENGINEERING-QUALITY-ROADMAP-2026-10-17.md) — ordered workstreams, scope, objectives and acceptance.
3. [Execution contract](docs/ENGINEERING-QUALITY-EXECUTION-CONTRACT.md) — non-negotiable delivery and safety gates.
4. [S1 offline test matrix](docs/quality/S1-SLEEP-WAKE-OFFLINE-MATRIX.md) when touching sleep, mode transitions or display repair.
5. Existing [ROADMAP](docs/ROADMAP.md), [CONTRIBUTING](CONTRIBUTING.md), [security publication policy](SECURITY-PUBLICATION.md) and [physical validation evidence](docs/VALIDATION-EVIDENCE.md).

Historical PR #67 hardening notes are historical, not the active execution plan. Current stable is **v1.7.4/code84**; [the release record](docs/VALIDATION-1.7.4.md) explicitly separates passed software gates from pending physical acceptance. The original v1.7.3 programme baseline remains historical.

## Hard rules

- **No feature drift.** Implement only one defined roadmap work item per implementation PR. State its ID, exact behavior permitted, affected files, tests, evidence level and stop conditions *before coding*. If a task does not map to an approved item, stop and propose a separate scope decision; do not smuggle it into a refactor.
- **Characterization first.** Read the real production path, define falsifiable invariants and add failing/negative tests where practical before changing behavior. Avoid tests of invented replica logic when the real class can be exercised.
- **Do not change runtime semantics in the roadmap/contract PR.** No modified Android, display, boot, CPU, daemon, updater, Binder, vendor or APK runtime; only governance documentation and lightweight CI policy check are in scope.
- **Fail closed.** Never weaken UID/process identity, socket permissions, exclusive lock, Binder admission, boot holds, CRTC confirmation, generation checks, timeouts, signing validation or installer ownership for test convenience.
- **No speculative display patches.** #84 prolonged TOP black presentation is not attributed to the app; #65 brief BOTTOM brightness flash is distinct. No unconditional screen ON/OFF, brightness override, sleep suppression, reboot, daemon kill, timing retune or broad recovery attempt without demonstrated failure mechanism, minimal patch, regression and authorized physical acceptance.
- **Offline means offline.** Host and Android emulator tests may run. No Thor/ADB device interventions, external sleep automation changes, kernel/vendor fault injection or forced black-screen experiments without separately approved supervised test and independent recovery procedure.
- **No side effects without approval.** Do not merge, tag, sign, publish, push a stable release, alter production secrets/environments/required checks, delete branches, or install any candidate on a real Thor. Opening a scoped draft PR is not permission for those actions.
- **Preserve privacy and provenance.** No signing keys, raw device traces, serials, personal identifiers, local paths, firmware blobs or undisclosed vulnerability mechanics. Use sanitized synthetic evidence and follow SECURITY-PUBLICATION.md.
- **Report exactly what was proved.** Host fixture != Android emulator != AYN Thor. Do not claim a bug fixed, security reviewed, release safe or device-tested from green CI alone. Record NOT RUN, FAIL and UNKNOWN, not just PASS.
- **Follow the stop gates.** If a test reveals a new privileged/display risk, reproducibility is lost, the branch diverges, scope expands or evidence contradicts the baseline, stop the affected work and escalate with evidence.

## Required PR handoff

Every implementation PR in this programme must record: roadmap item; base and head SHA; in-scope and explicitly excluded paths/behavior; pre-change characterization; exact commands and results; negative controls; impact on startup, BOTH/TOP, sleep, Guard and updater; evidence tier; unresolved risk; physical validation required; and an explicit statement **NO SIGNING / NO RELEASE / NO DEVICE TEST** unless those were separately approved and performed.

Use the project PR template and the execution contract. A checklist is not a bypass: protected branch/required checks and maintainer review must actually be configured and observed. Do not rewrite these instructions to make an unrelated change pass; propose a dedicated governance amendment with explicit rationale and owner review.