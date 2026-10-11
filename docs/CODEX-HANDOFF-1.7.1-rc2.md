# Codex handoff — v1.7.1-rc2 diagnostics + read-path prerelease and Thor acceptance

**Source of truth:** GitHub repo `JestyLabs/Jesty-Thor-Fix`. Do not assume the local Windows checkout is current. This task is to verify, prepare the **exact signed release candidate**, assist owner-supervised physical acceptance, and publish the **pre-release only** through the established protected pipeline when prerequisites and owner approvals are satisfied.

## Current provenance (2026-10-11)

- PR #55 (diagnostics) merged to `main` as `086ecb42b72bfbcd237cfeb1e34eb555a0b56b91`. The merged code has the gear icon, bounded app-event history, readable `AppDiagnosticPreview`, and unchanged raw V1 export.
- `v1.7.1-rc1` was **already** published before that merge, with `Jesty-Thor-Fix-1.7.1-rc1.apk` and Android versionCode **72**; it does not represent the completed diagnostics UI. Never replace the existing tag/asset with different APK bytes.
- This release-prep branch `release/1.7.1-rc2-diagnostics-20261011` bumps versionName to **`1.7.1-rc2`**, code **73**, `DaemonIdentity.VERSION` and default build artifact name. It admits previous daemon `1.7.1-rc1` **only** through the existing authenticated, healthy-watcher, BOTH/dual-active-CRTC gate, with negative tests.
- Existing `v1.7.0` stays recommended stable. Closed-Lid Wake Guard remains optional and OFF by default.
- PR #58 read-path optimization is included in this candidate **subject to exact-head CI and independent review**. PR #57 Lid maintenance, #59 passive energy and #37 refresh research are NOT included.

## Phase 0 — offline repository and CI review (no Thor / no ADB)

1. Fetch latest `main`, RC-prep branch and all open PRs before any local operation; confirm an updated `main` has not superseded this branch. Verify no uncommitted local or private research material would be overwritten.
2. Independently review the RC diff and `docs/RELEASE-NOTES-1.7.1-rc2.md`. Ensure versionName/code are aligned across manifest, `DaemonIdentity`, `build.ps1`, tests and release notes. Check the new safe handoff from 1.7.1-rc1 and that unknown/similar identities, unhealthy daemons or non-BOTH states still fail closed.
3. Verify the paired DRM read-path changes and Q `system_load_fix` compatibility from #58, including conservative unknown states and the original CPU Fix boot/restart path. Verify new docs and log-view code from merged #55. Check specifically: accessibility label of cogwheel, newest-first readable events, app-only exit reasons, CPU desired vs actual property wording, V1 export byte/schema compatibility, payload bounds, no raw identifiers, and no new polling.
4. Run the full repo host test suite and release-policy/public-content checks, including `scripts/test-display-cpu-read-paths.ps1`, diagnostic preview and CPU property fixtures, build unsigned APK and Android-emulator lifecycle/installer CI. Verify CodeQL on the **exact** candidate commit and review annotations, not just workflow status.
5. If a code or policy mismatch is found, **stop and report the specific diff**. Do not sign/publish an unverified build or silently amend production functionality. Changes must go through a reviewed PR and fresh CI.

## Phase 1 — protected GitHub merge and signing

1. Confirm the RC-prep PR has passed the three required branch checks (`build`, `Analyze (java-kotlin)`, `Analyze (actions)`) **and** the full emulator job. If it is behind `main`, synchronize safely and re-run checks; never weaken protections or force-push over others.
2. Merge #58 into the already-versioned RC2 `main` only after the PR CI (including its new read-path model step), CodeQL, emulator and independent review pass. Do not merge #57/#59/#37 into this candidate. A source SHA changed after signing requires fresh CI and signing.
3. On the resulting `main` push, use **CI → Sign main candidate** in the protected `thor-signing` environment. The signer is allowed only after the required `SirJesty` approval. Do not request, print, cache or commit keystore secrets.
4. Get the resulting signed `signed-candidate-v1.7.1-rc2-<commit>` Actions artifact and `candidate-metadata.json`. Confirm:
   - source `main` commit / run / attempt, signed and unsigned SHA-256;
   - APK exactly `Jesty-Thor-Fix-1.7.1-rc2.apk`, package `com.thor.displaypowertest`, code **73**, version **1.7.1-rc2**;
   - zipalign and APK signing verification; expected certificate SHA-256 `727D4850779BED1E51018108E13BC399D4DA38CFC68F4F7504120AD5E2DAD6FC`;
   - no code/resource changes after signing. Keep private files outside GitHub source.
5. Show owner exact signer, source SHA, run ID, APK hash, device expectations, rollback and stop criteria. **Do not install or access ADB without new, separate owner authorization.**

## Phase 2 — Thor acceptance (only on separate owner authorization)

- Preflight: battery adequate; normal BOTH, lid open and Lid Guard OFF by default; note current firmware, app version, daemon/version/identity, CPU Fix selection and actual state. Do **not** toggle CPU Fix (it restarts composer) simply to get test coverage without distinct approval.
- Install the **exact signed candidate** in place, without uninstalling/clearing app data. Check installed package/cert/version and daemon handoff; if old daemon is in TOP, a guarded in-place handoff may not be permitted—do not kill vendor/root processes by guess or force replacement.
- Functional: BOTH → TOP (lower CRTC actually inactive when fix enabled) → BOTH; ordinary TOP and BOTH sleep/wake; restore of BOTTOM and wake repair, preserving existing policy. Compare paired TOP/BOTTOM CRTC confirmations and fail-closed behavior against published baseline.
- UI: the bottom-right gear opens readable event log. Verify newest-first order, bounded history, UNKNOWN states, no false claim that Activity destruction is a crash, and requested CPU fix vs reported property. Check screen rotation, picker cancel, return from picker, unavailable picker, Activity recreation.
- Export: save report through Android document picker, check V1 schema, privacy allowlist and no raw device identifiers, timestamps, logcat, PID, paths, keystore, or accidentally exposed app/private data. The readable preview and machine export intentionally differ.
- Diagnose a controlled **app-only** force-stop/relaunch and Android exit-history reporting only after distinct owner consent. Do not stop the root daemon or firmware services for this test.
- Q parity: compare every existing Q key/type, particularly `system_load_fix` (0/1/?), across the published build and RC2; check direct-property/fallback behavior and Q latency without manually changing the CPU Fix property.
- Resource: compare matched idle/background daemon CPU to installed stable/rc1 if existing trusted measurements are available. Separately record 1 Hz foreground Q overhead with dashboard open, event-write rate, and UI responsiveness. Additional CPU cost is **not zero by proof**; if measurements are noisy/absent report UNKNOWN.
- Stop immediately for unexpected panel states, compositor/system-server restart, unauthorized process death, permission/privacy issue, no authenticated daemon, bad wake repair, unexpected sleep or regression. Do not retry destructive steps automatically.

Use `docs/APP-DIAGNOSTICS-RC-VALIDATION.md` as the detailed hardware test contract and privately record pass/fail/unknown with exact APK SHA.

## Phase 3 — prerelease publication

The owner requested publication of a **prerelease candidate**, **not** an unvalidated stable build. Reconfirm exact signed artifact and approval to use the protected `thor-publication` gate. Until device checks are complete, release notes MUST say **untested on physical Thor**, with v1.7.0 recommended stable.

1. GitHub Actions → **Publish tested candidate** → Run workflow from trusted `main`.
2. `candidate_run_id`: **the successful main push CI run that built AND signed this rc2 APK**; NEVER reuse rc1 or PR-only test signing artifacts.
3. `channel`: **prerelease** (never `stable` at this stage).
4. Wait for `thor-publication` environment approval by `SirJesty`. Workflow verifies exact run SHA, signer, version, release notes, artifact hash and tag before publishing.
5. Verify published `v1.7.1-rc2` tag points to the candidate source SHA; download its APK and compare SHA-256 byte-for-byte to the tested/signed artifact; verify it is marked prerelease and does not become Latest stable.
6. If any constraint fails, stop, inspect and do **not** upload a substitute APK manually.

## Phase 4 — after physical testing and any prerelease

- Record factual results, exact source SHA, APK SHA-256, signer, test platform and regressions in release integrity and RC validation docs via normal reviewed PRs.
- Do not promote to stable without owner acceptance of the **same exact artifact**; an APK change requires another version and retest.
- Handoff to ChatGPT the sanitized pass/fail matrix and non-sensitive provenance; retain raw logs, private ADB evidence and local keystore on owner PC.

## Concurrent research PRs — coordination, not merge

- **#37** research-only 120 Hz / tearing: retain draft; separate read-only 60 Hz control with a new owner authorization. Rebase docs/test harness against main without altering display modes or vendor binaries.
- **#57** Lid implementation OFF-path isolation: retain draft and review latest concurrency/CodeQL before physical tests; no Lid code in rc2.
- **#58** read-path optimization: included as an experimental candidate change after successful review/merge. Track CPU and Q parity outcomes separately from #55 and #57. If #57 is rebased afterwards, reconcile the shared `DisplayActionCoordinator` hunk with already-integrated #58 rather than discarding either.
- **#59** passive energy: after merge of #55, base now targets main, but branch still includes older diagnostics code/docs; reconcile it to preserve the cogwheel, `AppDiagnosticPreview` and latest raw export. Only passive sleep/battery experiment files should remain in its eventual diff. Never merge it into rc2.

**STOP GATES:** No physical execution, APK installation, forced refresh/clock, daemon kill, release publication or branch-protection bypass without appropriate owner approval and verified exact artifact. This document alone is not a device-access permission.
