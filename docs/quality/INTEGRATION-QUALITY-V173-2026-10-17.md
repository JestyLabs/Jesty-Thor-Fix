**20 October 2026 status update:** [v1.7.4 release record](../VALIDATION-1.7.4.md) supersedes current-status statements below. New live captures led to display-stall containment; physical acceptance is pending and #84 remains open. Earlier closure/automation attribution and integration gates are historical, not proof of the later incident's cause. The maintainer explicitly authorized release after software gates, with physical checks afterwards.

# Quality integration candidate — v1.7.3 baseline (2026-10-17)

**Integration branch:** `integration/quality-v173-offline-20261017` · **Base main:** `98ab7c342a028eaea8e43fb867900c0f9a3b5152` · **Programme:** JTF-QUALITY-2026-10-17, umbrella [#95](https://github.com/JestyLabs/Jesty-Thor-Fix/issues/95).

## What is merged INTO THIS BRANCH only

| Parent PR | Scope | Individual PR evidence before integration | Stage |
| --- | --- | --- | --- |
| [#96](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/96) | Contributor quickstart documentation | CI and CodeQL PASS | Integration inclusion, pending combined CI |
| [#97](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/97) | Governance, mandatory readings, S1 matrix and structural CI | Build + CodeQL PASS; Android emulator consent-settlement FAILED twice, **not waived** | Integration inclusion explicitly **PROVISIONAL** |
| [#98](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/98) | Pure production display-model host matrix | CI and CodeQL PASS | Pending combined CI |
| [#99](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/99) | Test-only, redacted native Android updater timeout evidence | CI and CodeQL PASS | Pending combined CI |
| [#100](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/100) | Real DisplayActionCoordinator inert-host characterization | New JDK17 coordinator host step and CodeQL PASS, full CI **not yet confirmed** at initial integration | Pending combined CI |
| [#94](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/94) | Harden handover authorization duplicate I/Q field parsing | CI and CodeQL PASS; exact new device behavior NOT VALIDATED | **Contains production runtime change; physical acceptance gate** |

All source PRs remain open/draft, and the **actual `main` branch and published v1.7.3 APK remain unchanged**. Each integration merge commit has both the integration HEAD and exact source PR HEAD as parents, preserving ancestry and provenance. On overlapping `.github/workflows/host-tests.yml` changes the combined workflow retains the S0 structural roadmap check, S1 real coordinator JDK17 test and S5 Python evidence-redaction test instead of overwriting any one.

## Validation contract

- [ ] Combined branch PR CI **build, all host tests and Android emulator lifecycle/updater** PASS at the **exact integration SHA**. A red emulator is **FAIL/UNRESOLVED**, not an automatic flake waiver. 
- [ ] Combined CodeQL PASS at exact integration SHA.
- [ ] Source-level audit of merged diff vs main, ensuring no unexpected feature changes, build identity, signer or release workflow drift.
- [ ] Review repeated native updater `native-consent-accepted` timeout: if reproduced, use PR #99 sanitized observations to diagnose, not increase timeouts or suppress assertions.
- [ ] Continue S1 offline late-event/mode-interleaving characterization as **separate scoped PR** targeting this integration branch; accept only after tests and review. 
- [ ] S2/#94 source and protocol compatibility reviewed independently; **any runtime handover behavior needs owner-supervised exact-APK physical acceptance before publishing a release**.
- [ ] Approve final versioning, production signing, release artifact provenance and publication independently from branch integration, after physical acceptance. Neither CI nor an integration PR alone satisfies this.

### Stop conditions

Block promotion to main/release on any test failure, unknown application state after Android consent, source conflict, loss of relevant safety checks, unreviewed security findings, or missing physical acceptance for runtime changes. Keep failed evidence visible. No automated merge, tagging, secret access, signing, physical device action or release is implied by this ledger.

### Evidence tiers

**HOST:** production Java may run with inert fixtures. **ANDROID EMULATOR:** generic Android/API33 integration without vendor hardware. **THOR:** actual authorized hardware/firmware/signed candidate; remains **NOT RUN** for this branch until explicitly performed. Never claim this branch is release-ready from the first two tiers alone.
