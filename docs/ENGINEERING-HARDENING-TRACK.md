# Incremental engineering hardening

> **Post-merge status, 2026-10-16:** PR #67 was merged into `main` as `229df903384ca3dfa0796005b5e697c3c58676af`. This ledger preserves dated planning and offline checkpoints, not the current PR state. The [RC78 physical record](PR67-PHYSICAL-VALIDATION-2026-10-16.md) remains **15 PASS / 1 historical FAIL**, with separate A1 PASS. Statements below about pending CI, merge, signing or physical tests reflect their historical checkpoint. The [integration review](PR67-INTEGRATION-GATES-2026-10-16.md) is a pre-merge snapshot. Stable 1.7.2 is not published.

**State:** archived work-plan ledger for the merged PR #67. This document records the original engineering work items, not stable 1.7.2 release validation or an assessment of shipped quality.

## Scope and integration boundary

- Original workstream base: `main` at `281e8c9f1d0982bc766843d1be083cb93e19cb7d` (2026-10-12). The v1.7.1 code76 source, UI-only follow-up and physical evidence were integrated into `main` (published release commit `f929ff910bfa8e5179a2c6a63948a18fc3d6b252`); stable v1.7.1 was published on 2026-10-12. **The branch was reconciled with accepted `main` at `8f0e7ca815c72b2d66b2dc1762c235e247ab32bb` on 2026-10-13, using an ancestry-preserving merge (`60066a3152b8d10f1556a695a32d5be21f11ab2d`). Recheck for later `main` changes before future code/workflow edits.**
- RC3 draft #63 and old recovery #64 were closed unmerged; their accepted functionality was integrated via code76 #68. Code77 contingency #69 was archived unmerged. Mixed-refresh #37 remains an independent research PR. **Do not revisit or modify the published v1.7.1 APK through this maintenance track.**
- Following the owner's offline-only authorization, this draft now contains **multiple scoped runtime changes** (updater session ownership/receiver, strict IPC ACK, listener rejected-close recovery, conservative external-display parser and UI access-labeling). Boot trace formatting is extracted without boot decisions changing; CPU controller, compositor restart, display-power commands, signing, release APK and physical Thor remain untouched. The final PR head, independent updater coupling, IPC and UI gates are still required before any release.
- Before any implementation step, inspect the accepted RC3 baseline, identify overlaps with ongoing PRs, and update this branch from the approved target. Re-evaluate each work item against code that actually shipped, rather than refactoring an outdated snapshot.
- Implement and review **one small, independently testable step at a time**. Each step needs a focused diff, named tests, a brief risk assessment and explicit evidence level. Keep the PR in draft until the agreed acceptance gates are complete.
- No automatic merge, release, signing, device installation, privileged fault injection or physical testing. Those actions need separate authorization.

## Offline execution ledger — 2026-10-13

**Current updater evidence:** the [native updater baseline](UPDATER-ANDROID-VALIDATION-2026-10-13.md)
passed 14/14 API 33 emulator cases locally and in CI at `8e78f75`, including
actual process death, native consent, archive/signature verification and stale
session reconciliation. The subsequent [confirmation policy](UPDATER-CONFIRMATION-DEDUP-2026-10-13.md)
coalesces deferred callbacks and claims one launch per session. Older historical
host-only statements below describe their pinned commits, not current coverage.
Controlled download transport, cached metadata and a test Host do not establish
live HTTP/TLS, full dashboard readiness or physical daemon handover.

**Local HTTPS extension:** [transport validation](UPDATER-TRANSPORT-VALIDATION-2026-10-13.md)
adds actual native Android HTTPS against a non-forwarding loopback fixture:
13 cases cover framing/size failures, real headers/body read timeouts, redirects,
hostname/certificate rejection and same-process recovery. Public CDN/DNS and
production trust chains remain separate; the product transport and DEX are unchanged.

**Subsequent metadata extraction:** [catalog validation](UPDATER-METADATA-EXTRACTION-2026-10-13.md)
characterizes 36 native Android JSON/cache cases (100 assertions) before and after
moving release records/selection, cache codec and bounded response reading into
`UpdateReleaseCatalog`. The earlier transport-only DEX identity claim applies to
its pinned step. This refactor changes product class structure; HTTPS, installer,
preferences and lifecycle ownership stay in `AppUpdater`, and release policy
stays in `UpdateVersion`. Final-head host/native/TLS CI remains required.

**Fixture shutdown follow-up:** [TLS investigation](UPDATER-TLS-FIXTURE-SHUTDOWN-2026-10-13.md)
records a host-reproduced missing orderly TLS EOF, Android A/B/A recovery trials
and a fixture-only close-notification fix with bounded peer acknowledgement.
Deliberate truncation/timeouts are preserved. Product runtime/TLS policy and
the historical failed executions remain unchanged; exact-head CI is required.

**Implemented/characterized:** narrow updater confirmation and session ownership; BootCoordinator trace-only formatter; strict IPC ACK and listener close-failure fix; fail-closed external-display parser; host stress/CPU/boot matrices; switch labels/keyboard semantics; contributor quick start; documented release, SDK, energy, accessibility and final physical matrix.

**UI emulator follow-up:** [native dashboard validation](ENGINEERING-HARDENING-UI-EMULATOR-2026-10-13.md) at `acbc3dd` covers seven-control keyboard traversal, Diagnostics activation, the native settings long-press, accessibility nodes and 100/150/200% text scaling. Its measured target/layout gaps are addressed by [separate UI fix commits](ENGINEERING-HARDENING-UI-FIXES-2026-10-14.md), with 12/12 final local native checks and permanent CI coverage. TalkBack speech, contrast, real telemetry and physical controller behavior remain unvalidated; final-head CI remains required.

**Remaining independent gates:** exact-final-head JDK17 CI, updater host/native regressions and separate security review; live GitHub admin required-check review; native peer credential/SELinux/timeout and independent security review; remaining Android accessibility/layout gates; live updater transport/discovery and full dashboard/daemon handover; optionally measurable battery/gameplay effects. The tested API 33 updater lifecycle includes actual process death; it is not physical-device acceptance. **Thor physical tests, merge, signing and publication come LAST and require owner approval.**

## Workstream checklist

### 1. Architecture and maintainability

- [x] Re-baseline current source ownership: `UpdateVersion` keeps candidate/asset policy, `UpdateCommitGate` owns the synchronized commit boundary, and `UpdateReadiness` owns live install eligibility; retained `AppUpdater` owns Android UI, PackageInstaller and lifecycle. Do not duplicate these boundaries.
- [x] **First narrow architecture extraction (draft PR #67):** move only deferred Android installer confirmation/session bookkeeping out of `AppUpdater` into pure `UpdateConfirmationStore<T>`, preserving the established single-slot/last-callback behavior. Two commits: [characterization tests](https://github.com/JestyLabs/Jesty-Thor-Fix/commit/90a21aa2fbe93f55a66e6bf718209d57b6133322) followed by [production wiring](https://github.com/JestyLabs/Jesty-Thor-Fix/commit/e3c28bf62140e873d018a0cad5253d4b53625d3b). **Local Java 21 host characterization passed; PR Java 17/build/emulator gates must pass before the extraction is considered accepted.** Do not infer stale-callback correctness from preserving current behavior.
- [x] Integrate host one-shot/pairing/80-race tests plus a Codex-style **real production AppUpdater and UpdateInstallReceiver** coupling harness with disposable Android UI/PackageInstaller adapters into Windows JDK17 CI. Original canceled9/stale9-vs11 regressions and native abandonment policy are covered; commit 178211f logged **119 assertions/16 scenarios PASS** in GitHub build. Commit d93ad79 adds native inventory-error and silently-retained-session cases; its Windows JDK17 build, CodeQL and Android emulator all passed with **137 assertions / 18 real-updater-under-host-adapters scenarios**. **At that historical head, native Android process death and verification were not covered. Current native evidence is recorded in the ledger above; live transport and physical vendor handoff remain separate.**
- [ ] Review `CpuFixController` and `BootCoordinator` for a narrow, independently testable extraction. Do not move live restart, boot-phase ordering, lock ownership, privileged calls or durable proof behavior without a separate validation plan.
- [x] Record [component ownership, locks, failure/recovery edges and safe extraction order](ENGINEERING-HARDENING-ARCHITECTURE-MAP-2026-10-13.md). The Windows `build.ps1` path is unchanged and its PR CI build passed; clean-checkout reproducibility remains for Codex.

**Gate:** preserved externally observable behavior, no unintended changes to privileged control paths, host suites pass. Code affecting boot/display/CPU requires separate physical-device acceptance before a release.

### 2. Android integration coverage

- [x] Inventory existing host, Android emulator, `PackageInstaller.Session` and update-gate tests; compare gaps in [offline audit](ENGINEERING-HARDENING-OFFLINE-AUDIT-2026-10-13.md). Keep integration implementation as a separate task.
- [x] Exercise production updater/receiver sources with actual Android UI/PackageInstaller in a disposable API 33 test APK: native consent/denial, cancellation before commit, archive/signer rejection, stale/duplicate callbacks, Activity recreation and real process death. Baseline 14/14 passed locally and in exact-head CI at `8e78f75`; see the [native report](UPDATER-ANDROID-VALIDATION-2026-10-13.md). Full product dashboard, live transport and daemon handover remain separate.
- [x] Add source-level R1/R2/R3 regressions: replacement/native inventory is fail-closed, PowerShell release-policy normalizes CRLF, and five malformed closed external viewport fixtures resolve to UNKNOWN. JDK17 build at 178211f passed replacement policy (17 checks), external display adversarial (25), production updater coupling (119/16), unsigned packaging and public release contracts; **d93ad79 subsequently passed Windows JDK17 suite (137 updater coupling checks/18 cases), CodeQL and emulator; newer heads still need exact-SHA checks**.
- [x] **Session ownership correction (historical host correction, now complemented by native tests):** `UpdateSessionOwnership` now tracks one active generation and native session ID. The real `AppUpdater` registers the session after Android allocates it and invalidates it on failure; `UpdateInstallReceiver` accepts only callbacks for that session. A matching terminal callback removes only its pending confirmation and cleans only its own attempt; duplicates, missing IDs, stale session callbacks are ignored. Starting a replacement now first enumerates and attempts to abandon **all** app-owned native sessions, then confirms the installer inventory is empty. On an abandonment/inventory failure it blocks the new transfer without discarding the old prompt, cache, owner or commit-gate reuse. The rule applies after simulated process-local state loss as well; the production runner still needs final-head CI. Added model tests and expanded the real-receiver/fake-Android regression tests. **The original regressions were reversed under host adapters; native consent, verification and process-death evidence is now recorded above. Full product dashboard, live transport and physical handover remain open.**
 **Historical receiver harness:** the original 11 local assertions used stub Android services. The expanded receiver and real-class host/native runners now execute in CI; their latest reports distinguish simulated Android from actual PackageInstaller and physical acceptance.
- [ ] Model reconnect and version handover races with deterministic seams, while keeping production socket credentials and daemon identity checks intact.
- [x] Exercise concurrent commit/cancel, actual receiver callbacks, startActivity failures, failed installer open/fsync/commit, a simulated process-local reset, late terminal statuses and native session abandonment with host adapters. The source runner explicitly labels the lifecycle reset as simulated, not Android process death.
- [x] Define and implement one pending confirmation launch per native session, including repeated paused callbacks, pause/resume, reentrant dispatch and launch failure followed by a fresh-session retry. See [confirmation policy and validation](UPDATER-CONFIRMATION-DEDUP-2026-10-13.md). Native physical handover and full-dashboard readiness remain unverified.
- [x] Add host test coverage for 12 simultaneous Cancel/Commit contenders (30 repetitions) and the actual `UpdateReadiness` adapter with a test-only fake Android SystemClock (stale/paused state, boot hold across daemon loss, clock rollback). These do **not** prove Android PackageInstaller behavior.
- [ ] Make additional CI gates mandatory only after stability and runtime cost are measured.

**Gate:** repeatable tests with documented assertions and evidence limits. No claim that emulator installation validates Thor privileged functionality.

### 3. Device acceptance matrix

- [x] Reconcile existing RC2/code76 physical observations in [the published validation evidence](VALIDATION-EVIDENCE.md); positive closed-lid Guard interception remains NOT OBSERVED. **No device was touched for PR #67.**
- [x] Prepare [one consolidated future physical acceptance matrix](ENGINEERING-HARDENING-PHYSICAL-EXIT-MATRIX-2026-10-13.md) with separately gated BOTTOM-only, dock/external, closed lid and firmware variants. **All PR #67 cases remain NOT RUN.**
- [x] Document entry requirements, safe stops, exact APK provenance, scoped measurements and final state restoration in the consolidated physical matrix. It is a plan, not approval to run.
- [ ] Obtain explicit device-owner approval before each new physical write/installation/restart test; maintain private raw evidence and public sanitized summaries.

**Gate:** version-pinned, physically observed acceptance for each scenario claimed. Unsupported firmware/revisions remain unclaimed unless directly exercised.

### 4. Privileged IPC and lifecycle robustness

- [x] Perform [static socket/identity/queue/lifecycle review](ENGINEERING-HARDENING-IPC-REVIEW-2026-10-13.md) and real DaemonIpcServer host-fake stress tests. **Native kernel peer credentials, SELinux, timeout and independent security review remain pending.**
- [x] Add strict client response-ACK boundary: `SocketClient.request` uses `DaemonResponsePolicy` rather than trusting any `ok=1` prefix; reject `ok=10`/`ok=1x`. Pure host tests wired into Windows CI. **Await actual CI and physical daemon-command regression before any release.**
- [ ] Complete privileged IPC adversarial acceptance beyond host simulation. **Implemented in PR #67:** `IpcPeerPolicyTest` exhaustively covers 512 POSIX permission combinations and UID extremes; `DaemonIpcServerHostTest` compiles the real listener against disposable Android socket, UID and command fakes and tests unauthorized-before-read, EOF, unknown commands, I/O and handler errors, 2 running + 4 queued + 1 rejected clients, and continuation after a rejected client's `close()` throws. The latter was reproduced failing before [the narrow listener fix](https://github.com/JestyLabs/Jesty-Thor-Fix/commit/80be3009dcb030e1e866920bffbf8a0e2a7de521) and passing after in local JDK 21 tests. **Pending:** final PR Java 17 CI, real kernel UID/SELinux/socket inode enforcement, Android timeout behavior, and independent security review; these are not established by fakes.
- [x] Exercise queue saturation and read/write/handler/close faults with host-only socket fakes; **no vendor service was stopped or manipulated**. No trusted native socket/peer-credential claim made.
- [ ] Arrange separate independent security review if available; record its exact scope rather than assuming a static scan is an audit.

**Gate:** deterministic failures remain bounded and fail safely; public findings omit private device identifiers, vendor binaries and non-public implementation details.

### 5. Contributor and build reproducibility

- [x] Inventory Windows, JDK 17, Android API 34 / Build Tools 35.0.0, SHA-pinned Apktool 3.0.3 and unsigned build inputs in [quick start](CONTRIBUTOR-QUICKSTART.md); Codex previously reported clean unsigned build/JDK17 suite PASS through head 0b268e1. The **final head** still requires its own CI.
- [x] Add [credential-free contributor quick start](CONTRIBUTOR-QUICKSTART.md) and link from `CONTRIBUTING.md`: JDK 17, Android SDK API 34, Build Tools 35.0.0, SHA-pinned Apktool 3.0.3, all public test suites and unsigned `build.ps1`. **Fresh independent Windows checkout validation assigned to offline Codex; do not mark the clean-environment acceptance gate passed yet.**
- [ ] Consider containerized/toolchain bootstrap or Gradle feasibility only if measurable benefits outweigh migration and binary-compatibility risk; do not force a build-system rewrite.
- [x] Add [source-only DEX compilation and test-fixture isolation contract](../scripts/test-build-source-boundary.ps1): production APK classes must come from `src/`, while host Android fixtures stay under test paths. This is a **static policy**, not a fresh DEX bytecode audit; final-head CI pending. Existing release signing and exact unsigned/signed artifact policies are unchanged.

**Gate:** a fresh environment can build an unsigned APK and run the supported host tests from written steps, with recorded tool versions and without privileged credentials.

### 6. TOP-mode idle CPU characterization — deferred post-v1.7.1 release

**Status:** analysis/planning only. Stable v1.7.1/code76 has been published; the measured TOP overhead is not a release blocker and this item is **not** authorization to tune watcher behavior or run new physical tests.

**Additional existing-window offline analysis:** the independent 2026-10-13 review recomputed per-thread process ticks. Across three ~182 s windows per mode, the thread labeled `Thread-1` had **BOTH median 0.087970%** and **TOP median 0.311525% of one core**; the total code76 daemon medians remained **BOTH 0.087931%** and **TOP 0.317188%**. Sampling spans/thread reuse and 10 ms CPU tick quantization prevent exact per-thread reconciliation. This **narrows the measured difference to one sampled thread**, but does not identify its Java method or prove waste. The default decision remains **no watcher change without a code-path attribution and wake-repair safety argument**.

**Why:** On the physical AYN Thor, the established RC2 → code76 awake-idle daemon CPU medians were **BOTH 0.289403% → 0.087931%** (−69.6%) and **TOP 0.322314% → 0.317188%** (−1.6%) of one CPU core. TOP retained low overhead and passed the predeclared budget, but differs from BOTH in steady state. Historical watcher optimization samples recorded similarly low BOTH (~0.093%) and higher TOP (~0.350%) medians. The RC2 BOTH result is higher than both of those OTHER BOTH measurements, so do not infer that the code76 CPU Fix adoption patch caused a 69.6% performance improvement.

**Code-path hypothesis, not measured attribution:** in TOP, the display coordinator additionally checks wake/panel state and retains verified wake-repair safety. The watcher uses a 500 ms idle settings check and approximately 1 s DRM safety cadence; observed CPU is primarily watcher work. The contribution of any particular helper or vendor property read has not been isolated.

- [x] Reconcile RC2/code76 measured BOTH/TOP and quiet-suspend evidence plus Guard ON normal recovery; explicit Guard active blocked-wake path remains NOT OBSERVED.
- [x] Review existing physical thread-tick CSV and daemon windows; TOP Thread-1 sampled CPU predominates but Java method attribution is not available. Current measured results meet the declared low-overhead budget.
- [x] Distinguish proven daemon CPU from **hypothesized** TOP safety work; absent Java stack or workload attribution there is no demonstrated avoidable work, so **no watcher code change is justified**. See [optional measurement protocol](ENGINEERING-HARDENING-MEASUREMENT-PROTOCOL-2026-10-13.md).
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
- [x] Add [current release/manifest provenance contract](../scripts/test-current-release-contract.ps1) to CI: version/tag/code76 source SHA, target SDK 25 baseline, non-exported installer receiver, active Guard and historical readiness caveats. This deliberately ignores unrelated historical versions. **CI on final head still required.**

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

- [x] Complete [offline accessibility entry-point review](ENGINEERING-HARDENING-UI-A11Y-2026-10-13.md); static labels added to empty-text switches, actions made keyboard focusable, GitHub long-press documented to TalkBack, decorative background excluded. **Separately proposed visible Update Settings action remains a UI product/visual review decision.**
- [ ] Run a targeted manual accessibility checklist: TalkBack content descriptions/focus order, actionable hit areas, contrast, larger fonts/text clipping, keyboard/controller navigation and landscape behavior. Record what was actually tested.
- [ ] Treat UI changes as independent opt-in product work: no privileged daemon, install gate, watcher or display-power changes; retain screenshot or on-device visual verification for any altered UI.

**Gate:** a documented accessibility/discoverability baseline and, if warranted, a separate small UI PR; no claim of complete accessibility certification from code inspection.

### 9D. P2 — Android/firmware compatibility and contributor independence

**Observed gap:** `targetSdkVersion=25` is intentional but carries future compatibility cost. Hardware display/CRTC/Hall constants and the vendor `PServerBinder` are specific to tested Thor firmware. A generic Android build or another dual-screen device cannot establish support.

- [x] Publish [non-executing target SDK 25 migration feasibility matrix](ENGINEERING-HARDENING-SDK-FEASIBILITY-2026-10-13.md) covering boot services, Binder reflection, install consent and exported components. **No target, minSDK, manifest or signing-policy change.**
- [x] Separate documented Android 13 Thor tested firmware from unverified dock, external display, BOTTOM-only and other devices in [compatibility](COMPATIBILITY.md) and the [future physical matrix](ENGINEERING-HARDENING-PHYSICAL-EXIT-MATRIX-2026-10-13.md); no expanded support claim.
- [x] Provide documented pinned toolchain, sanitized troubleshooting instructions, unsigned build and test suite in [contributor quick start](CONTRIBUTOR-QUICKSTART.md), plus release provenance contract; **no Gradle migration**. Independent final-head clean checkout remains optional confirmation.

**Gate:** an accurate support boundary and executable contributor setup; no new compatibility claim without the exact firmware/device evidence.

### 9E. P2/P3 — quality evidence beyond daemon CPU and static checks

**Observed gap:** measured daemon CPU and Android suspend are valuable but do not quantify total battery-runtime improvement or gameplay impact. Power proxies, short snapshots and CPU-tick percentages must not be presented as controlled battery-life or frame-time results.

- [x] Define [optional energy A/B/A protocol](ENGINEERING-HARDENING-MEASUREMENT-PROTOCOL-2026-10-13.md) with calibrated meter/Wh distinction, fixed workload and temperature/USB controls. **Protocol only; NOT RUN.**
- [x] Define optional game performance sanity matrix (fixed workload, median/p95/p99 frame time, throttling, A/B/A) in [measurement protocol](ENGINEERING-HARDENING-MEASUREMENT-PROTOCOL-2026-10-13.md); **NOT RUN**, no new telemetry polling.
- [x] Record host-model, production-with-fakes, Android emulator, pinned physical, and **NOT RUN** tiers in [measurement protocol](ENGINEERING-HARDENING-MEASUREMENT-PROTOCOL-2026-10-13.md). Do not invent coverage percentages.
- [ ] Prefer independent review of privileged behavior and real failure cases over additional documentation or feature count (cross-reference items 2 and 4).

**Gate:** meaningful, repeatable measurements with transparent limitations. Optional performance/energy investigations are **not** retroactive release blockers for v1.7.1.

**Order of execution:** 9A and 9B are the first new engineering hygiene priorities; 9C–9E are follow-ups to schedule only when the core 1–6 safety/correctness tasks justify them. Each proposed code, CI, settings or hardware change remains separately scoped and explicitly authorized.
