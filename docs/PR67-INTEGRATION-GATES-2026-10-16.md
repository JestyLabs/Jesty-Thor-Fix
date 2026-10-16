# PR67 — integration review and remaining release gates

**Pre-integration review snapshot: 16 October 2026.** The tested-candidate source commit is `8d2935736080567c6c538035d8925519a083ac41`; accepted main is `7cff7cf49bc4261157fc69cd735a15dbbd2461ef`. Their automatic local combination produced tree `cd24c4acdb691310aa2548d40288b60b94f2eef5` without conflicts. This identifies the reviewed combination before branch integration. Any later integration head is a separate commit: the physical evidence remains attached to the tested-candidate source and original signed APK. An integration commit or CI result does not imply a rebuilt APK received physical acceptance.

Relative to the tested-candidate source commit, the combination changes only CI and adds the approved physical-pair workflow/policy test. Runtime sources and candidate identity are unchanged. The actual combined workflow preserves:

- Main signing only by `workflow_dispatch`, explicit opt-in defaulting to false, authorized actors and a runtime guard; pushes do not sign.
- Frozen-pair provenance and encrypted delivery policy.
- The PR67 controlled updater lifecycle and strict TLS shutdown/framing test steps.

| Local verification | Result | Scope |
| --- | --- | --- |
| Release workflow policy | PASS | Existing static workflow/release checks |
| Frozen signing/delivery policy | 111 checks PASS | Existing real workflow blocks with mocks/disposable fixtures, including opt-in positive and event/default/ref/actor negative cases; no production signing |
| Controlled updater lifecycle | 198 assertions / 33 scenarios PASS | Existing host Android adapters and selected worker bodies; no device, download or installation |
| Strict TLS shutdown/framing | 6/6 PASS | Existing non-forwarding loopback fixture; no external endpoint or device |

Existing CI/CodeQL success covers the frozen PR head, **not** this local combination or future documentation commits. Effective classic protection was read successfully: required checks were `build`, `Analyze (java-kotlin)` and `Analyze (actions)`, with an up-to-date branch required. Earlier failed access is historical; the settings are known at this snapshot. Recheck at merge; API mergeability does not satisfy protection.

Before merge:

1. Independently review the [physical closure](PR67-PHYSICAL-VALIDATION-2026-10-16.md), preserving T09 FAIL, A1 separate PASS and exclusions. The approved short-session matrix is closed; universal release acceptance is not implied.
2. Review the proposed docs and apply an approved integration that retains PR83 policy. Validate the actual resulting tree, policy tests and required CI/CodeQL. Keep signing default-off; no production signing is needed to test policy.
3. Obtain explicit merge authorization. Local tests do not authorize publication, merge or release.

Stable `1.7.2 /code80`, final APK identity/provenance, predecessor migration, recovery numbering and public updater discovery/download/install remain separate distribution decisions and evidence. Recovery79 was not physically installed; a higher version code does not prove safe handover. No speculative runtime patch, broad refactor or replay of accepted CPU series is justified by this review. Raw captures, detailed security evidence and signing records remain owner-local.

The PR description and checks on its current integration head record subsequent branch and CI status; this dated snapshot preserves the reviewed inputs and local test scope.
