# Incremental engineering hardening

**State:** draft work plan. This document records engineering work items, not a release validation or an assessment of shipped quality.

## Scope and integration boundary

- Original workstream base: `main` at `281e8c9f1d0982bc766843d1be083cb93e19cb7d` (2026-10-12). The v1.7.1 code76 source, UI-only follow-up and physical evidence were integrated into `main` (published release commit `f929ff910bfa8e5179a2c6a63948a18fc3d6b252`); stable v1.7.1 was published on 2026-10-12. **The branch was reconciled with accepted `main` at `8f0e7ca815c72b2d66b2dc1762c235e247ab32bb` on 2026-10-13, using an ancestry-preserving merge (`60066a3152b8d10f1556a695a32d5be21f11ab2d`). Recheck for later `main` changes before future code/workflow edits.**
- RC3 draft #63 and old recovery #64 were closed unmerged; their accepted functionality was integrated via code76 #68. Code77 contingency #69 was archived unmerged. Mixed-refresh #37 remains an independent research PR. **Do not revisit or modify the published v1.7.1 APK through this maintenance track.**
- Following explicit authorization for more offline work on 2026-10-13, this draft now includes new **host-only updater/IPC tests**, a **test-only SystemClock fixture**, and one small **production client-side IPC response-check correction** in `SocketClient`, delegated to `DaemonResponsePolicy`. Boot/display/CPU/Lid Guard, daemon command handling, updater installation, CI policies, signing, releases and physical device are untouched. The IPC runtime change must not ship without final CI and affected-path acceptance.
- Before any implementation step, inspect the accepted RC3 baseline, identify overlaps with ongoing PRs, and update this branch from the approved target. Re-evaluate each work item against code that actually shipped, rather than refactoring an outdated snapshot.
- Implement and review **one small, independently testable step at a time**. Each step needs a focused diff, named tests, a brief risk assessment and explicit evidence level. Keep the PR in draft until the agreed acceptance gates are complete.
- No automatic merge, release, signing, device installation, privileged fault injection or physical testing. Those actions need separate authorization.

## Workstream checklist

### 1. Architecture and maintainability

- [x] Re-baseline current source ownership: `UpdateVersion` keeps candidate/asset policy, `UpdateCommitGate` owns the synchronized commit boundary, and `UpdateReadiness` owns live install eligibility; retained `AppUpdater` owns Android UI, PackageInstaller and lifecycle. Do not duplicate these boundaries.
- [x] **First narrow architecture extraction (draft PR #67):** move only deferred Android installer confirmation/session bookkeeping out of `AppUpdater` into pure `UpdateConfirmationStore<T>`, preserving the established single-slot/last-callback behavior. Two commits: [characterization tests](https://github.com/JestyLabs/Jesty-Thor-Fix/commit/90a21aa2fbe93f55a66e6bf718209d57b6133322) followed by [production wiring](https://github.com/JestyLabs/Jesty-Thor-Fix/commit/e3c28bf62140e873d018a0cad5253d4b53625d3b). **Local Java 21 host characterization passed; PR Java 17/build/emulator gates must pass before the extraction is considered accepted.** Do not infer stale-callback correctness from preserving current behavior.
- [ ] Finish the full integration characterization gate: host one-shot/pairing/80-race tests and the new session-ownership/receiver regression tests are wired into Windows CI. **The prior last-callback-wins store behavior remains within a given attempt**, but new active-session correlation rejects foreign/terminal callbacks. Complete the Codex-style coupling harness with actual AppUpdater plus receiver and verify pause/resume/process death and native Android PackageInstaller behavior before a release.
- [ ] Review `CpuFixController` and `BootCoordinator` for a narrow, independently testable extraction. Do not move live restart, boot-phase ordering, lock ownership, privileged calls or durable proof behavior without a separate validation plan.
- [x] Record [component ownership, locks, failure/recovery edges and safe extraction order](ENGINEERING-HARDENING-ARCHITECTURE-MAP-2026-10-13.md). The Windows `build.ps1` path is unchanged and its PR CI build passed; clean-checkout reproducibility remains for Codex.

**Gate:** preserved externally observable behavior, no unintended changes to privileged control paths, host suites pass. Code affecting boot/display/CPU requires separate physical-device acceptance before a release.

### 2. Android integration coverage

- [x] Inventory existing host, Android emulator, `PackageInstaller.Session` and update-gate tests; compare gaps in [offline audit](ENGINEERING-HARDENING-OFFLINE-AUDIT-2026-10-13.md). Keep integration implementation as a separate task.
- [ ] Exercise the **full production** app/receiver/PackageInstaller lifecycle, including cancellation before commit, denial, stale callbacks and cleanup, in an isolated Android emulator.
- [x] **Session ownership correction (host-tested; Android emulator E2E still open):** `UpdateSessionOwnership` now tracks one active generation and native session ID. The real `AppUpdater` registers the session after Android allocates it and invalidates it on failure; `UpdateInstallReceiver` accepts only callbacks for that session. A matching terminal callback removes only its pending confirmation and cleans only its own attempt; duplicates, missing IDs, stale session callbacks are ignored. Starting a replacement invalidates the prior deferred confirmation and attempts to abandon its native session. Added model tests and expanded the real-receiver/fake-Android regression tests. **This fixes the two pre-existing Codex-reproduced host cases at the explicitly tested state/receiver boundary, not yet proven in the integrated production AppUpdater/Android environment.**
 **Partial host coverage completed:** 11 local assertions on the actual `UpdateInstallReceiver` class with stub Android services, wired into `test-dashboard.ps1`; checks pending confirmation, success, aborted/generic failures, default failure, duplicate terminal callback behavior. Separate PR CI/emulator runs and end-to-end app wiring still required.
- [ ] Model reconnect and version handover races with deterministic seams, while keeping production socket credentials and daemon identity checks intact.
- [ ] Test concurrent lifecycle events and failure recovery in host/emulator environments, explicitly distinguishing simulated behavior from hardware outcomes.
- [x] Add host test coverage for 12 simultaneous Cancel/Commit contenders (30 repetitions) and the actual `UpdateReadiness` adapter with a test-only fake Android SystemClock (stale/paused state, boot hold across daemon loss, clock rollback). These do **not** prove Android PackageInstaller behavior.
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
- [x] Add strict client response-ACK boundary: `SocketClient.request` uses `DaemonResponsePolicy` rather than trusting any `ok=1` prefix; reject `ok=10`/`ok=1x`. Pure host tests wired into Windows CI. **Await actual CI and physical daemon-command regression before any release.**
- [ ] Add adversarial *host-only* tests for malformed/unknown one-byte commands, connection floods, interrupted reads, resource exhaustion and authenticated daemon identity/race conditions. **Partial test-only progress:** `IpcPeerPolicyTest` now exhaustively checks all 512 POSIX permission combinations and UID ownership extremes; locally compiled/executed on OpenJDK 21. The actual daemon/socket lifecycle and CI JDK 17 still require separate validation.
- [ ] Exercise failures through fakes/mocks, not by stopping or altering vendor services on a live device.
- [ ] Arrange separate independent security review if available; record its exact scope rather than assuming a static scan is an audit.

**Gate:** deterministic failures remain bounded and fail safely; public findings omit private device identifiers, vendor binaries and non-public implementation details.

### 5. Contributor and build reproducibility

- [ ] Inventory documented tool versions, checksums, operating-system requirements, build inputs and repeatability of the existing Windows/Apktool pipeline.
- [x] Add [credential-free contributor quick start](CONTRIBUTOR-QUICKSTART.md) and link from `CONTRIBUTING.md`: JDK 17, Android SDK API 34, Build Tools 35.0.0, SHA-pinned Apktool 3.0.3, all public test suites and unsigned `build.ps1`. **Fresh independent Windows checkout validation assigned to offline Codex; do not mark the clean-environment acceptance gate passed yet.**
- [ ] Consider containerized/toolchain bootstrap or Gradle feasibility only if measurable benefits outweigh migration and binary-compatibility risk; do not force a build-system rewrite.
- [ ] Verify that public source builds remain distinct from exact signed, hardware-accepted release artifacts.

**Gate:** a fresh environment can build an unsigned APK and run the supported host tests from written steps, with recorded tool versions and without privileged credentials.

### 6. TOP-mode idle CPU characterization — deferred post-v1.7.1 release

**Status:** analysis/planning only. Stable v1.7.1/code76 has been published; the measured TOP overhead is not a release blocker and this item is **not** authorization to tune watcher behavior or run new physical tests.

**Additional existing-window offline analysis:** the independent 2026-10-13 review recomputed per-thread process ticks. Across three ~182 s windows per mode, the thread labeled `Thread-1` had **BOTH median 0.087970%** and **TOP median 0.311525% of one core**; the total code76 daemon medians remained **BOTH 0.087931%** and **TOP 0.317188%**. Sampling spans/thread reuse and 10 ms CPU tick quantization prevent exact per-thread reconciliation. This **narrows the measured difference to one sampled thread**, but does not identify its Java method or prove waste. The default decision remains **no watcher change without a code-path attribution and wake-repair safety argument**.

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

- [x] Record release asset metadata SHA-256/size, manifest package/version, source SHA and successful CI/sign/publish run; [release integrity](RELEASE-INTEGRITY.md#171-stable-publication--verifiable-public-metadata-2026-10-12) distinguishes public workflow provenance, **independent offline rehash of the owner's archived published APK**, and separately reported offline signer/package checks. No new on-device install or fresh independent network re-download is claimed.
- [x] Add v1.7.1 stable release provenance to `docs/RELEASE-INTEGRITY.md`, explicitly separating the physically tested code76 candidate from the final UI-only-different stable APK.
- [x] Update the current-state `docs/VALIDATION-EVIDENCE.md` summary, annotate `docs/RELEASE-READINESS-1.7.1.md` as historical and clarify `docs/COMPATIBILITY.md` Guard limits. **Active Guard interception remains NOT OBSERVED**; historical unknown cases remain unchanged.
- [ ] Add a release-document consistency check if it can reliably detect stale current-version/status references without trying to mechanically rewrite historical documents.

**Gate:** verified release metadata, no changed APK, no fabricated physical claim, and no loss of historical audit trail. This is a documentation-only release housekeeping task and can be delivered separately; it must not become an excuse to alter the stable app.

### 9B. P1 — close the difference between CI coverage and enforced branch gates

**Observed gap:** the owner-private Codex snapshot of GitHub's actual `main` protections (2026-10-13) confirms required `build`, `Analyze (java-kotlin)`, and `Analyze (actions)` with up-to-date checks, but **not** `Android emulator lifecycle and installer sessions (no vendor bridge)`. That emulator job runs on PRs, not ordinary `main` pushes. The snapshot also reports main-scoped, reviewer-protected `thor-signing`/`thor-publication` environments without admin bypass. This is evidence of **that snapshot**, not a guarantee that later GitHub settings have not changed; no private config export is committed.

- [x] Reconcile the Codex admin policy snapshot with current workflow triggers and published v1.7.1 source run: `build`/both CodeQL contexts enforced for `main`, Android emulator **not required** and skipped on the signed main push, signer/publisher environments restricted in the captured configuration. Re-verify effective settings before changing policy; a captured admin export is not a durable guarantee.
- [ ] **Recommended next admin action, separate from this refactor:** after checking recent successful PR emulator runs, add the stable PR job `Android emulator lifecycle and installer sessions (no vendor bridge)` as a required `main` check. It currently runs on every PR through `host-tests.yml`, unlike path-filtered `updater-gate-emulator.yml`; **do not require the path-filtered check**, which may not exist on unrelated PRs. An admin must apply/verify the setting and then confirm it blocks a deliberately failing PR; this document does not change repository settings. A main-push pre-signing emulator gate would require its own carefully reviewed workflow/dependency change, not only an admin checkbox.
- [x] Codex captured owner-authorized policy snapshots showing both protected environments with required reviewers, main-only branch restrictions, and admin bypass disabled; however `prevent_self_review=false` and zero approving PR reviews do **not** establish independent two-person approval. Decide whether self-review and solo-maintainer release operation are acceptable before any settings mutation; verify live effective policy in the admin UI.
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
