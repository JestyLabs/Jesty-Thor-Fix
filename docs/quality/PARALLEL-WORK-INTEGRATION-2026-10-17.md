**v1.7.5 status (20 October 2026):** [release validation](../VALIDATION-1.7.5.md) records current containment-status correction and exact 1.7.4 upgrade support. All software gates passed; physical acceptance remains NOT RUN under explicit owner timing approval. S4/AppUpdater and #95/S6 remain pending; other workstreams are not certified complete by publication.

**20 October 2026 status update:** [v1.7.4 release record](../VALIDATION-1.7.4.md) supersedes current-status statements below. New live captures led to display-stall containment; physical acceptance is pending. #84 is closed as delivered software mitigation at the maintainer's request; physical follow-up remains in #95/S6. Earlier closure/automation attribution and integration gates are historical, not proof of the later incident's cause. The maintainer explicitly authorized release after software gates, with physical checks afterwards.

# Parallel-work integration ledger — 2026-10-17

**Programme:** JTF-QUALITY-2026-10-17. **Single central tracker:** https://github.com/JestyLabs/Jesty-Thor-Fix/issues/95.  
**Scope:** synchronize pre-existing work with the new programme and identify conflicting changes, not auto-merge or claim device acceptance.  
**Baseline:** published v1.7.3/code83; checked against main SHA 98ab7c342a028eaea8e43fb867900c0f9a3b5152. Status below is a historical read at integration; **re-query the current PR HEAD and CI before acting**.

## Dependencies and ownership

| PR / issue | Owner workstream | Exact scope | CI evidence at recorded head | Next acceptance gate |
| --- | --- | --- | --- | --- |
| [#94](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/94) | S2/S3 — handover and privileged parser | `LiveHandoverPolicy`, `PreviousSecureDaemonIdentity`, their existing tests; reject duplicate I/Q authorization fields even if identical. Defensive hardening, **not a demonstrated exploit**. | Head `614f09a3f9210c4406bff3fad59a0bee71b1532b`: build PASS and CodeQL PASS; first emulator job CANCELLED during generic emulation; safe rerun requested, not yet an accepted PASS at integration. | Recheck rerun result, review legitimate daemon response formats and fail-closed compatibility; physical affected-path acceptance before any release. |
| [#96](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/96) | S5-DX — contributor documentation | One-line update to `docs/CONTRIBUTOR-QUICKSTART.md`: current unsigned artifact naming from `build.ps1` rather than hardcoded v1.7.1. | Head `60c7cf7bee2351cd61f8ad1d28ab68276b63dece`: CI and CodeQL PASS. | Independent review, accept documentation only; no new APK or signing. |
| [#97](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/97) | S0 — quality contract | `AGENTS.md`, roadmap, governance templates, CODEOWNERS and structural CI check, S1 plan and THIS ledger. | Prior head `f7eddd25d65b52d7eaffe725aa02d55522ba446a`: build and CodeQL PASS, emulator FAIL during updater Android-state wait. This did **not** modify runtime; failure classification requires verification and fresh exact-head CI. | Rerun/review exact HEAD; do not waive emulator merely because no app code changed. |
| [#98](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/98) | S1 — model-level sleep/wake | Deterministic combinatorial safety tests of production display-decision and repair-generation classes. | Head `51e9587ca05f8d9286a8b8c685d737a267e2e83f`: full CI and CodeQL PASS; no Thor. | Review real-class test breadth vs model limitations; extend to coordinator only in separate accepted increment. |
| [#82](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/82) / [#84](https://github.com/JestyLabs/Jesty-Thor-Fix/issues/84) | S1 — blackout | Sanitized incident record and open tracker; owner saw persistent black presentation on v1.7.1. | Draft docs; no causal pre-reboot capture. | Bound source causality, passive diagnostics, separately authorized physical evidence; **keep issue open**. |
| [#65](https://github.com/JestyLabs/Jesty-Thor-Fix/issues/65) | S1-adjacent observation ONLY | Brief BOTTOM bright flash. Distinct from #84; no common cause shown. | Owner observations, no luminance measurement. | Read-only correlated capture; no speculative brightness override. |
| [#37](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/37) | OUT OF SCOPE | Mixed refresh research. | Independent DRAFT PR, no new physical proof from this programme. | Do not merge into S1/S2 by association. |

## Concurrent development guard

- #97 is the ONLY governance contract PR; #95 is the ONLY umbrella tracker. Do not open copies.
- #94 controls its own **handover parsing runtime** paths. Another S2/S3 contributor must review #94 diff and test branch before writing overlapping authorization/daemon logic; integrate as an independently accepted slice, **not** by copying an outdated patch into #97.
- #96 controls contributor quickstart edits. S5 must recheck whether #96 is merged before touching the same file; never overwrite its correct version-dynamic guidance.
- #98 controls display-model test matrix. S1 should progress to production coordinator interleavings after reviewing #98, not duplicate its pure-model cases.
- #97 should not cherry-pick #94/#96/#98 into a single PR; keep test tiers, approvals and release risks independent. After separate merges, refresh this ledger and live checklist from actual main.
- Single source of truth for scope/priority amendments = issue #95 plus an approved review in the relevant PR. Roadmap changes require a dedicated governance PR and a visible diff.

## CI finding: do not conceal failing checks

At the original PR #97 head, Android emulator installed and ran lifecycle and installer confirmation steps, then `scripts/test-updater-android-e2e.py` raised `AssertionError: Timed out waiting for Android state`; this is an **unclassified emulator/native-updater test failure**, not evidence that S0 documentation caused the issue and not proof it is harmless. Keep the failed run visible, rerun on the exact current head, and investigate if repeatable. #94 first CI run was **cancelled**, not PASS, despite successful host/CodeQL.

## Administrative enforcement gap

Repository account `SirJesty` is reported as **admin**, but connected GitHub App/API operations for branch protection are *not authorized*: `GET /repos/JestyLabs/Jesty-Thor-Fix/branches/main/protection` returned HTTP **403 Resource not accessible by integration**. Readable rulesets showed only active tag protection. This does **not** prove main has no rules (legacy protection may be inaccessible). No protection was altered. Review/enforce this via authorized GitHub repository admin settings; record the actual branch rules, required check names, CODEOWNERS review, skipped job behavior and force-push/deletion restrictions in #95 before claiming guaranteed mandatory protection.

No release, signing, merge, on-device work or production admin mutation is authorized by this ledger.
