# Incremental engineering hardening

**State:** draft work plan. This document records engineering work items, not a release validation or an assessment of shipped quality.

## Scope and integration boundary

- Original workstream base: `main` at `281e8c9f1d0982bc766843d1be083cb93e19cb7d` (2026-10-12). The v1.7.1 code76 source, UI-only follow-up and physical evidence were integrated into `main` (published release commit `f929ff910bfa8e5179a2c6a63948a18fc3d6b252`); stable v1.7.1 was published on 2026-10-12. **The branch was reconciled with accepted `main` at `8f0e7ca815c72b2d66b2dc1762c235e247ab32bb` on 2026-10-13, using an ancestry-preserving merge (`60066a3152b8d10f1556a695a32d5be21f11ab2d`). Recheck for later `main` changes before future code/workflow edits.**
- RC3 draft #63 and old recovery #64 were closed unmerged; their accepted functionality was integrated via code76 #68. Code77 contingency #69 was archived unmerged. Mixed-refresh #37 remains an independent research PR. **Do not revisit or modify the published v1.7.1 APK through this maintenance track.**
- With v1.7.1 now published, this PR remains **documentation/planning-only until separately approved, re-baselined steps are selected**. No daemon, boot, CPU Fix, Lid Guard, display, IPC, updater, workflow, signing or install changes are authorized by this update.
- Before any implementation step, inspect the accepted RC3 baseline, identify overlaps with ongoing PRs, and update this branch from the approved target. Re-evaluate each work item against code that actually shipped, rather than refactoring an outdated snapshot.
- Implement and review **one small, independently testable step at a time**. Each step needs a focused diff, named tests, a brief risk assessment and explicit evidence level. Keep the PR in draft until the agreed acceptance gates are complete.
- No automatic merge, release, signing, device installation, privileged fault injection or physical testing. Those actions need separate authorization.

## Workstream checklist

### 1. Architecture and maintainability

- [ ] Re-baseline the component map after RC3 acceptance; identify existing isolation and tests before choosing extractions.
- [ ] Start with a low-risk separation of responsibilities in `AppUpdater` (release metadata/selection, transfer verification, installer orchestration or UI lifecycle), *only if* it avoids duplicating `UpdateVersion` and `UpdateCommitGate`.
- [ ] Add focused characterization tests for unchanged updater decisions, cancellation semantics and install gating before and after an extraction.
- [ ] Review `CpuFixController` and `BootCoordinator` for a narrow, independently testable extraction. Do not move live restart, boot-phase ordering, lock ownership, privileged calls or durable proof behavior without a separate validation plan.
- [ ] Record dependency/ownership boundaries and ensure the package layout remains buildable by the existing public build script.

**Gate:** preserved externally observable behavior, no unintended changes to privileged control paths, host suites pass. Code affecting boot/display/CPU requires separate physical-device acceptance before a release.

### 2. Android integration coverage

- [x] Inventory existing host, Android emulator, `PackageInstaller.Session` and update-gate tests; compare gaps in [offline audit](ENGINEERING-HARDENING-OFFLINE-AUDIT-2026-10-13.md). Keep integration implementation as a separate task.
- [ ] Exercise real platform installation-session callbacks for cancellation before commit, installation denial, stale callbacks and session cleanup in an isolated emulator.
- [ ] Model reconnect and version handover races with deterministic seams, while keeping production socket credentials and daemon identity checks intact.
- [ ] Test concurrent lifecycle events and failure recovery in host/emulator environments, explicitly distinguishing simulated behavior from hardware outcomes.
- [ ] Make additional CI gates mandatory only after stability and runtime cost are measured.

**Gate:** repeatable tests with documented assertions and evidence limits. No claim that emulator installation validates Thor privileged functionality.

### 3. Device acceptance matrix

- [ ] Reconcile RC3 device-test results and already completed coverage first; avoid repeating captures without a specific unanswered question.
- [ ] Prepare scoped cases for BOTTOM-only, docking/external displays, closed-lid states and supported-firmware transitions.
- [ ] Define setup, preconditions, observed hardware state, safe stop conditions and rollback/recovery path for each case.
- [ ] Obtain explicit device-owner approval before each new physical write/installation/restart test; maintain private raw evidence and public sanitized summaries.

**Gate:** version-pinned, physically observed acceptance for each scenario claimed. Unsupported firmware/revisions remain unclaimed unless directly exercised.

### 4. Privileged IPC and lifecycle robustness

- [ ] Review Unix-socket peer identity, permissions, stale endpoint ownership and bounded concurrency/timeout paths.
- [ ] Add adversarial *host-only* tests for malformed/unknown one-byte commands, connection floods, interrupted reads, resource exhaustion and authenticated daemon identity/race conditions. **Partial test-only progress:** `IpcPeerPolicyTest` now exhaustively checks all 512 POSIX permission combinations and UID ownership extremes; locally compiled/executed on OpenJDK 21. The actual daemon/socket lifecycle and CI JDK 17 still require separate validation.
- [ ] Exercise failures through fakes/mocks, not by stopping or altering vendor services on a live device.
- [ ] Arrange separate independent security review if available; record its exact scope rather than assuming a static scan is an audit.

**Gate:** deterministic failures remain bounded and fail safely; public findings omit private device identifiers, vendor binaries and non-public implementation details.

### 5. Contributor and build reproducibility

- [ ] Inventory documented tool versions, checksums, operating-system requirements, build inputs and repeatability of the existing Windows/Apktool pipeline.
- [ ] Provide a clean contributor quick-start for unsigned local builds and tests, without requiring a production signing key or device.
- [ ] Consider containerized/toolchain bootstrap or Gradle feasibility only if measurable benefits outweigh migration and binary-compatibility risk; do not force a build-system rewrite.
- [ ] Verify that public source builds remain distinct from exact signed, hardware-accepted release artifacts.

**Gate:** a fresh environment can build an unsigned APK and run the supported host tests from written steps, with recorded tool versions and without privileged credentials.

### 6. TOP-mode idle CPU characterization — deferred post-v1.7.1 release

**Status:** analysis/planning only. Stable v1.7.1/code76 has been published; the measured TOP overhead is not a release blocker and this item is **not** authorization to tune watcher behavior or run new physical tests.

**Why:** On the physical AYN Thor, the established RC2 → code76 awake-idle daemon CPU medians were **BOTH 0.289403% → 0.087931%** (−69.6%) and **TOP 0.322314% → 0.317188%** (−1.6%) of one CPU core. TOP retained low overhead and passed the predeclared budget, but differs from BOTH in steady state. Historical watcher optimization samples recorded similarly low BOTH (~0.093%) and higher TOP (~0.350%) medians. The RC2 BOTH result is higher than both of those OTHER BOTH measurements, so do not infer that the code76 CPU Fix adoption patch caused a 69.6% performance improvement.

**Code-path hypothesis, not measured attribution:** in TOP, the display coordinator additionally checks wake/panel state and retains verified wake-repair safety. The watcher uses a 500 ms idle settings check and approximately 1 s DRM safety cadence; observed CPU is primarily watcher work. The contribution of any particular helper or vendor property read has not been isolated.

- [ ] With code76 physical CPU, BOTH/TOP quiet-suspend and basic Lid Guard ON recovery smoke now completed, reconcile existing owner-private trace/counter snapshots with the exact integrated code76 source and RC2 reference. The optional Guard's *active* blocked-wake path was not observed and must not be marked validated by inference.
- [ ] Inspect existing sample counts, DRM-read deltas, event/burst activity, property reads, thread CPU ticks and relevant TOP/BOTH control paths. Compare actual stable windows, not only source-level invocation estimates.
- [ ] Distinguish hardware-mode-specific safety work from avoidable redundant execution. Establish a baseline with no unnecessary Thor interaction; profile offline first.
- [ ] If a real, significant avoidable cost is isolated, propose **a separate narrow change** with host characterization, regression tests and unchanged physical display states and wake-repair correctness.
- [ ] Any change to sampling interval, DRM safety read, compositor/display interaction or wake repair needs **separate approval and physical A/B validation** before acceptance. Do not weaken the 500 ms/1 s safety fallbacks solely to achieve parity with BOTH.
- [ ] Report the outcome as either *observed necessary safety overhead — no code change* or *demonstrated avoidable work — reviewed optimization with unchanged correctness*.

**Gate:** measured attribution, preserved no-event fallback and real wake-repair behavior, no deterioration in bounded response or daemon stability. **No promise that TOP will reach the ~0.09% BOTH level**, and no performance-based release blocker from the current 0.317% TOP result.

**Evidence:** [physical validation index](VALIDATION-EVIDENCE.md) and candidate76 [V6 report (documentation PR #70)](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/70). Keep all raw device and proprietary-vendor traces private.

## Per-step review record

Use this small template in a PR comment or commit description for each completed step:

- **Scope / changed paths:**
- **Baseline / related PRs:**
- **Expected behavior:** unchanged or explicitly described
- **Evidence:** code review, host test, emulator, physical device (identify only what was actually run)
- **Failure and rollback approach:**
- **Next step:** separate and gated

## Public evidence and safety

Follow [public evidence policy](PUBLIC-EVIDENCE-POLICY.md), [research workflow](RESEARCH-WORKFLOW.md) and [publication security](../SECURITY-PUBLICATION.md). Keep third-party proprietary material, firmware extracts, sensitive telemetry, identifiers and signing data out of the repository. Avoid speculative claims about untested behavior. This document is a forward work plan, not an acceptance report.

## 7. Historical-branch audit: candidates not automatically merged

**Audit date:** 2026-10-13. GitHub reported **41** branches, of which only #37 and #67 had open PRs. No archived research or recovery was merged into the stable app during this audit. The full pinned snapshot is in [branch archive manifest](https://github.com/JestyLabs/Jesty-Thor-Fix/blob/archive/branch-prune-snapshot-2026-10-13/branch-audit/manifest.json). Git history preserves old tip commits even when the live branch names are retired.

### A. One-off lower-screen bright-flash investigation — preserve as optional follow-up

- Source: closed research [#62](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/62), branch tip `a3e1ef9179130ca92245654f7afbea6f1f735e14`.
- A bright flash was **seen once**; the owner confirms **no recurrence** across later device tests or normal use. The offline log review observed an automatic BOTH panel-ON path during lid sleep; it does **not** establish that this action caused the visual event.
- If the problem returns, first compare the historical timeline and current `DisplayDecisionModel`/coordinator OFF/ON gates, isolate a plausible wake-interactive predicate with host-only model tests, then request a separate owner-controlled hardware A/B if justified. No speculative ON suppression in v1.7.1 and no claim that a root cause is resolved.

### B. Local event-history prototype — optional product decision

- Source: closed unmerged [#53](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/53), branch tip `6d6e8d8cd97785b1b429afd75f6a366a23abc58a`.
- Proposed a 64-item sanitized change-only journal, app-local persistence, copy and clear functions. **Current App Diagnostics already offers bounded history and export.** Compare exact coverage, retention and UX before deciding whether any of the older design materially improves support. No automatic new action button, additional polling, or duplicate data store.

### C. Experimental characterization tools — potential narrow tests

- `research/watcher-drm-cost-measurement`: historical `WatcherCostMetrics` and polling notes, tip `80f8cc65743120c8db100fb40c5f21a1806274e0`; consider only for offline TOP CPU attribution (item 6), keeping production watcher safety unchanged.
- `work/cpu-diagnostic-evidence-model`: standalone, time-based CPU pinning evidence estimator and host tests, tip `829962198832e9fbf783f86ab28ed641afd55a26`; compare current CPU status logic and tests before proposing a narrow characterization-only change.
- `test/thor-cpu-restart-fault-injection`: historical deliberate-crash test fixture, tip `c8d703dbec654c0924ad12274b7e1e4a6a34f22a`; read-only historical evidence, **never enable or execute on a physical device by default**.

### D. Old recovery/splash prototypes — archive, not release

- Numerous bootanimation/SurfaceControl/recovery-splash branches contained alternative pre-composer visual paths and unvalidated process/driver interactions. They are not equivalent to current shipping behavior and must **not** be bulk-merged.
- Old recovery code75 and emergency code77 carry version/install-order risks. The code77 research was not installed or certified as a public release.
- Prior signing and policy branches were independently integrated or superseded; do not copy historical workflow YAML over the hardened release policy.

**Gate for all candidates:** compare the exact archival commit against today's code, assess relevance and privacy, create a **new, small scoped PR** only for a demonstrable improvement, run focused tests, and require separate approval for any privileged runtime or device-side changes. A historical branch containing files absent from `main` is not automatically a missing release feature.

## 8. Branch-hygiene follow-through

- A temporary [archive branch](https://github.com/JestyLabs/Jesty-Thor-Fix/tree/archive/branch-prune-snapshot-2026-10-13) pins **all 38** non-main/non-#37/non-#67 branch tips via explicit Git merge parents. Independent readback verified **38/38** tips with original exact SHAs. Its tree holds only an inventory and the main snapshot; **it is not a runtime merge**.
- After creating and verifying an immutable **archival tag** at the archive commit, and once v1.7.1 release signing/publication is settled, delete only the manifest-listed stale branches whose remote tip SHA still matches its pinned SHA and which are not sources of any newly opened PR.
- The expected final active GitHub branch set is **`main` + #37 + #67**. Tags retain historical recovery/prototype code without cluttering active branches. Stop, do not force, on any changed ref.

## 9. Follow-ups from independent code/release audit (2026-10-13)

**Static offline inventory and verified release references:** [2026-10-13 offline audit](ENGINEERING-HARDENING-OFFLINE-AUDIT-2026-10-13.md). The release provenance, published-status index, Guard compatibility claim and CI inventory were updated in this PR. Physical and GitHub-admin checks remain separate.

This section records **only additional actionable gaps** not adequately tracked by items 1–6; it does not re-score the project or widen the already published v1.7.1 release. Existing work already covers architecture/controller boundaries (1), updater + lifecycle integration (2), device matrix and active Guard validation (3), adversarial IPC review (4), reproducible contributor builds (5), and TOP-only daemon CPU attribution (6). The isolated bright-flash question remains in [issue #65](https://github.com/JestyLabs/Jesty-Thor-Fix/issues/65) / refresh research [#37](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/37); do not duplicate or imply a proven root cause.

### 9A. P1 — reconcile shipped-release documentation and provenance

**Observed gap:** the stable [v1.7.1 GitHub release](https://github.com/JestyLabs/Jesty-Thor-Fix/releases/tag/v1.7.1) was published on 2026-10-12, but some retained readiness/evidence material still describes code76 final approval/publication as pending, and `docs/RELEASE-INTEGRITY.md` has no v1.7.1 stable record corresponding to the v1.7.0 record. Historical pre-release plans must remain traceable, not be silently rewritten as though every proposed gate was physically exercised.

- [x] Record release asset metadata SHA-256/size, manifest package/version, source SHA and successful CI/sign/publish run; [release integrity](RELEASE-INTEGRITY.md#171-stable-publication--verifiable-public-metadata-2026-10-12) labels GitHub-metadata and workflow-verified facts separately. **Independent local APK rehash/certificate verification remains not performed**; do not mark that additional check as complete.
- [x] Add v1.7.1 stable release provenance to `docs/RELEASE-INTEGRITY.md`, explicitly separating the physically tested code76 candidate from the final UI-only-different stable APK.
- [x] Update the current-state `docs/VALIDATION-EVIDENCE.md` summary, annotate `docs/RELEASE-READINESS-1.7.1.md` as historical and clarify `docs/COMPATIBILITY.md` Guard limits. **Active Guard interception remains NOT OBSERVED**; historical unknown cases remain unchanged.
- [ ] Add a release-document consistency check if it can reliably detect stale current-version/status references without trying to mechanically rewrite historical documents.

**Gate:** verified release metadata, no changed APK, no fabricated physical claim, and no loss of historical audit trail. This is a documentation-only release housekeeping task and can be delivered separately; it must not become an excuse to alter the stable app.

### 9B. P1 — close the difference between CI coverage and enforced branch gates

**Observed gap:** PR CI includes Android 13 emulator/installer sessions, but the public `main` branch metadata currently advertises `build` and both CodeQL analyses as required checks; the emulator job was not listed there. The presence of signing/publishing environment checks in YAML alone does not prove every GitHub-side reviewer/branch restriction is active.

- [ ] Map build, CodeQL, emulator, installer, release-policy and signer/publisher jobs to **actual required branch checks**, conditional triggers, skipped states, and repository rules; do not assume all green jobs are merge-blocking.
- [ ] Decide which emulator/integration checks should be required on relevant PRs, and adjust workflows and rules coherently only after checking runtime/flakiness and avoiding permanently pending/skipped required checks.
- [ ] Verify GitHub-side signing/publication environment protections and approvals using an account with sufficient permissions; if administration settings cannot be inspected, record them as **unverified**, not enabled.
- [ ] Keep test-key emulator artifacts separate from protected production signing and preserve exact-artifact publication and certificate/size/hash validation.

**Gate:** documented evidence of which checks truly block unsafe merges/releases, not just intended YAML policy. Any GitHub settings mutation or workflow hardening is a separate reviewed change.

### 9C. P2 — discoverability and accessibility: scoped UI-only review

**Observed gap:** updater settings are reached through a long-press on the GitHub action, an interaction without an obvious visible affordance. The hand-built, landscape-only Android dashboard lacks documented systematic TalkBack, font scaling and alternate input validation.

- [ ] Audit every settings/diagnostics/update entry point for obvious affordances; specifically consider a visible and clearly named updates/settings access path instead of relying solely on the GitHub long-press. Preserve user consent and all updater safety gates.
- [ ] Run a targeted manual accessibility checklist: TalkBack content descriptions/focus order, actionable hit areas, contrast, larger fonts/text clipping, keyboard/controller navigation and landscape behavior. Record what was actually tested.
- [ ] Treat UI changes as independent opt-in product work: no privileged daemon, install gate, watcher or display-power changes; retain screenshot or on-device visual verification for any altered UI.

**Gate:** a documented accessibility/discoverability baseline and, if warranted, a separate small UI PR; no claim of complete accessibility certification from code inspection.

### 9D. P2 — Android/firmware compatibility and contributor independence

**Observed gap:** `targetSdkVersion=25` is intentional but carries future compatibility cost. Hardware display/CRTC/Hall constants and the vendor `PServerBinder` are specific to tested Thor firmware. A generic Android build or another dual-screen device cannot establish support.

- [ ] Maintain a **non-executing feasibility audit** of target SDK migration: boot/background service behavior, hidden API/bridge access, install-source permissions, PackageInstaller callbacks, exported components and tests needed before any target change. Do **not** raise the target in this track without explicit physical approval.
- [ ] Clearly separate validated Thor firmware/device revisions from untested dock, external display, BOTTOM-only or other hardware variants, cross-referencing item 3 rather than re-running that matrix.
- [ ] Reduce single-maintainer dependency with precise clean-checkout/test/build instructions, public troubleshooting, artifact/test provenance and contributor-friendly failure messages; link to item 5 instead of a build-system rewrite.

**Gate:** an accurate support boundary and executable contributor setup; no new compatibility claim without the exact firmware/device evidence.

### 9E. P2/P3 — quality evidence beyond daemon CPU and static checks

**Observed gap:** measured daemon CPU and Android suspend are valuable but do not quantify total battery-runtime improvement or gameplay impact. Power proxies, short snapshots and CPU-tick percentages must not be presented as controlled battery-life or frame-time results.

- [ ] Define an **optional, separate** energy study with repeatable workload, brightness, wireless/USB state, environment, instrumentation and matched A/B(A) configurations; use calibrated electrical measurements when possible. Preserve the distinction between proxy readings, suspend duration and measured energy.
- [ ] Define an optional gameplay performance sanity matrix (frame times, input/frame pacing and background daemon behavior) if a real user-facing regression is suspected; do not add a continuous runtime diagnostic solely for this study.
- [ ] Record test-tier/branch coverage explicitly (host state models, Android emulator, physical Thor, and **not tested**); investigate quantitative coverage only if it improves assurance on the boot/updater/IPC paths, rather than pursuing a cosmetic percentage.
- [ ] Prefer independent review of privileged behavior and real failure cases over additional documentation or feature count (cross-reference items 2 and 4).

**Gate:** meaningful, repeatable measurements with transparent limitations. Optional performance/energy investigations are **not** retroactive release blockers for v1.7.1.

**Order of execution:** 9A and 9B are the first new engineering hygiene priorities; 9C–9E are follow-ups to schedule only when the core 1–6 safety/correctness tasks justify them. Each proposed code, CI, settings or hardware change remains separately scoped and explicitly authorized.
