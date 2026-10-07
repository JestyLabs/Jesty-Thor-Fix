# Branch prune ledger — 2026-10-07

Purpose: reduce branch clutter **without losing unique project information**.

This ledger was prepared after PR #34 merged into `main` as v1.6.0 integration commit `e4542adb9fe47cccb5bc8ee5b3cffdeeec702513`.

## Rule used

A branch is eligible for deletion only when at least one of these is true:

1. its result is already represented on `main`;
2. its exact work is retained in a merged/closed PR, with the branch head SHA pinned here;
3. unique orphaned material has first been copied into `docs/archive/`.

Active PR branches are not pruned.

## KEEP

- `main`
- `chore/pre-prune-archive` — temporary; delete only after this archive PR is merged

## SALVAGED BEFORE DELETE

- `work/cpu-diagnostic-evidence-model` — head `86f460bece2c1b197fd3bf8f53d6b87361004ac3`
  - had no PR;
  - exact model, test, and dashboard-test harness are preserved in [CPU-PINNING-EVIDENCE-MODEL.md](CPU-PINNING-EVIDENCE-MODEL.md).
- `test/thor-cpu-restart-fault-injection` — head `cd985361deff09e0b220e116745c623cd37908ac`, PR #19
  - exact research document and test-only fault-injection class are preserved in [CPU-RESTART-FAULT-INJECTION.md](CPU-RESTART-FAULT-INJECTION.md).

## SAFE TO DELETE AFTER THIS ARCHIVE IS MERGED

### Already merged / superseded documentation and tooling

- `docs/device-compatibility-matrix` — head `9872c5dc67b409dc94da1115fa627dd218e78b5a` — PR #26 merged
- `docs/project-roadmap` — head `97f0f620e78e3356da21a135a1bfaafddcb380c5` — PR #24 merged
- `docs/provenance-record` — head `674a4b103f0003a9ee34081910203f292a421543` — PR #35 merged
- `docs/readme-evidence-story` — head `c8e2c7de01c1ee4bd03db8e544afd0e2e1d90179` — PR #25 merged
- `work/thor-security-automation` — head `fd83473130938288df2d36250aabfdc8ef803ead` — PR #8 merged
- `work/thor-sign-apk-inspection-fix` — head `dc89cb5e53badc0b3be42acd85be808bd336dcb4` — PR #14 merged
- `work/thor-sign-pr-artifact-fix` — head `57772e155fd780516e198092b34b3b790eee1c41` — PR #13 merged
- `work/thor-signed-test-candidates` — head `df082646d7ee09a97009a931adf9574fa9b4d39b` — PR #6 merged

### CPU-fix development branches superseded by current main

- `work/thor-early-cpu-gate-model` — head `5edba6f0e093f119eefe6ce9311745946a1f8055` — PR #10 closed; later gate model exists in current path
- `work/thor-early-cpu-restart` — head `70a134d03a22af498b798e1f685e68518a3c53a7` — PR #7 lineage; current `main` contains the later durable model/store and docs
- `work/thor-early-cpu-restart-phase2` — head `c2337a7971e6b7b84efb4264044a4d6de5228314` — PR #12 merged
- `work/thor-early-cpu-restart-runtime` — head `5565d3b7c4b65aac92d76a776da84b0a1bf22293` — PR #11 merged
- `work/thor-precomposer-cpu-proof` — head `417b5f01baa98306619c81891d1c1b6dfb83b0f2` — PR #31 merged; proof/model/script exist on current `main`

### Closed recovery-splash / double-boot research

See [RECOVERY-SPLASH-RESEARCH.md](RECOVERY-SPLASH-RESEARCH.md) for conclusions, PR mapping, and exact head SHAs.

- `research/thor-double-boot-phase3a`
- `research/thor-recovery-splash-instrumentation`
- `research/thor-recovery-splash-integration`
- `research/thor-recovery-splash-surface-prototype`
- `work/thor-bootanim-suppression-current`
- `work/thor-recovery-native-bootanim-prototype`
- `work/thor-recovery-splash-current-prototype`
- `work/thor-recovery-splash-curtain-prototype`
- `work/thor-recovery-splash-model-current`
- `work/thor-recovery-splash-top-exclusive-prototype`
- `work/thor-recovery-top-only-probe`

## pservice prototype provenance now safe to prune

PR #33 is now closed as superseded by merged PR #34 / stable v1.6.0. Its physical-test record is preserved in:
- closed PR #33 comments and diff;
- `PROVENANCE.md`;
- production integration in `main`;
- this ledger's pinned SHAs.

The two old branch refs are therefore eligible for deletion after this archive PR is merged:

- `work/thor-pservice-early-cpu-restart-model` — head `4045a4958361242f45e006edc16a2457272a715b`
- `work/thor-pservice-early-cpu-restart-prototype` — head `cb7d7b19e9227674d316459458277b911f331cd4`

Stable v1.6.0 was published from `e4542adb9fe47cccb5bc8ee5b3cffdeeec702513`.
Release APK digest:
`sha256:2cf04fc692898a2a582781838f3026555bc7cc4e233226bfad677840e329cf00`.

Deleting these branch refs does not delete the closed PR record or the stable release.

## Important

Deleting a branch is a ref cleanup operation, not permission to delete the corresponding PR, release, provenance document, release note, SHA256 record, or archived research evidence.
