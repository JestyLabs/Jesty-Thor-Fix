# Engineering quality roadmap — post-v1.7.3

**Programme ID:** JTF-QUALITY-2026-10-17  
**Status:** proposed execution baseline; no implementation, physical acceptance, signing or release is authorized by this document.  
**Reference:** published v1.7.3/code83; base-main 98ab7c342a028eaea8e43fb867900c0f9a3b5152, 2026-10-17. Rebase each future PR against current main; never assume this SHA remains current.  
**Execution rules:** [contract](ENGINEERING-QUALITY-EXECUTION-CONTRACT.md) · [agent entry point](../AGENTS.md) · [current operational roadmap](ROADMAP.md)

## Outcome, not score-chasing

The independent engineering assessment estimated **7.7/10** overall. This is a qualitative baseline, **not** a measured objective or a CI metric. The stretch objective is evidence-based quality above **9/10**, only if actual physical and engineering gaps close. Do not claim a higher score solely because tests/docs were added.

| Dimension | Baseline | Aspirational gate | Proof required |
| --- | ---: | ---: | --- |
| Reliability: boot/sleep/wake/recovery | 6.3 | 8.8 | Reproduced failure mechanism, failing regression -> passing minimal fix, supervised affected-path device acceptance |
| Physical validation | 6.8 | 9.0 | Exact signed APK/firmware/mode matrix, separate long TOP trials and safe recovery; unresolved NOT RUN stays open |
| Code maintainability | 7.0 | 8.7 | Small characterized extractions, lower coupling, no behavior regression |
| Architecture | 7.8 | 9.0 | Explicit ownership/locks/states and measured complexity reduction |
| Privileged security | 8.0 | 9.2 | Independent adversarial boundary review, tested failures, accepted remediation |
| Automated tests | 8.6 | 9.3 | Real-class negative tests and deterministic Android integration; avoid test-double-only confidence |
| CI/release governance | 8.8 | 9.4 | Verified checks, required-branch protection reviewed by authorized admin, provenance and approval separation |

The other dimensions (feature usefulness, overhead, diagnostics/privacy, UI/accessibility, documentation) must **not regress** in pursuit of the table. No target date, feature release or major architectural rewrite is implied.

## Locked workstreams and order

### S0 — Governance and offline preflight (this PR)

**Deliverables:** authoritative plan, execution contract, root AGENTS.md, contributor/PR checklist, simple CI policy-presence check, S1 test matrix.  
**Boundaries:** documentation and build-time policy checks only; **zero application/runtime changes**. No physical validation claim.  
**Done when:** exact diff reviewed; roadmap linked from normal contributor entry points; build CI and CodeQL green at PR head where applicable; owner accepts governance. Open as DRAFT; no auto-merge.

### S1 — #84 sleep/wake transition safety and observability (first engineering task)

**Risk:** prolonged TOP black presentation after wake on published v1.7.1, root cause still unknown; the separate #65 BOTTOM flash is not a confirmed same defect.  
**Offline tasks:** inspect actual DisplayActionCoordinator / DisplayDecisionModel / boot hold / mode-generation invalidation / bounded repair; build a deterministic interleaving harness and tests using real production decision paths as far as possible; audit passive monotonic logging, redaction and whether relevant transition evidence could survive without panel output.  
**Output:** classified transition matrix, replayable tests, measured log overhead/retention if modified, documented competing hypotheses, physical capture plan with STOP/recovery criteria. Start from [S1 matrix](quality/S1-SLEEP-WAKE-OFFLINE-MATRIX.md) and the [existing evidence review](ISSUES-65-84-ENGINEERING-REVIEW-2026-10-17.md).  
**Forbidden:** brightness overrides, blindly repeated ON/OFF, suppression of sleep, wake-timing changes or claiming black screen fixed from an emulator.  
**Exit:** verified regression/causal explanation OR explicit unresolved status; same-version vendor hardware behavior needs separately authorized real-Thor acceptance before release.

### S2 — v1.7.3 handover and recovery hardening

**Risk:** exact predecessor identity, process/liveness races, stale socket/lock, delayed Binder effects and new daemon READY proofs.  
**Offline tasks:** fault-matrix for old daemon disappearing between checks, PID reuse, duplicate requests, partial handover, lock retention, ack-without-effect, failed receipt write, stale/cancelled work, rollout fallback. Validate production policy/adapter boundaries and conservative failure results.  
**Forbidden:** broaden 1.7.1/1.7.2 allowlist, disable credentials or kill an incompletely authenticated process, infer success from a success-shaped ACK.  
**Exit:** exact negative-test evidence, preserved rollback/fallback invariants. A real same-kernel handover from v1.7.3 is **not yet** an established hardware PASS in this programme; physical acceptance is separate.

### S3 — privileged IPC and vendor security assessment

**Risk:** filesystem socket peer validation, resource exhaustion, Binder transport, helper process identity/ownership, race windows.  
**Offline tasks:** independent threat model, malformed/truncated replies, slow-read/queue pressure, credential mismatch, stale endpoints, process-identity reassessment; real-class harness where feasible; sanitize and privately retain sensitive findings.  
**Forbidden:** changing trust-boundary behavior for convenience, exposing detailed exploitation/identifiers in the public repo.  
**Exit:** evidence-backed report, negative controls, small scoped fixes only after separately reviewed risk decision; no claim Linux kernel/vendor validation from host tests.

### S4 — architecture simplification (characterization first)

**First candidates:** CpuFixController (restart/early hooks/attempt proof/locking), then AppUpdater (Android sessions/confirmation/download/UI responsibilities).  
**Tasks:** map ownership/lock ordering, test existing states and errors with real-class adapters; isolate at most one behavior-neutral responsibility per PR. Preserve UpdateVersion, UpdateCommitGate, UpdateReadiness and existing extracted catalog boundaries.  
**Forbidden:** simultaneous boot/CPU/updater rewrite, changing persisted formats, restart timing or release selection implicitly.  
**Exit:** demonstrably smaller responsibility and equivalent tests, CI green, affected device semantics separately accepted if changed.

### S5 — CI/CD and contribution gates (parallel independent PR)

**Tasks:** inspect ACTUAL GitHub required-check protections with authorized admin, identify whether Android emulator is required for affected merges, ensure pinned JDK17 real-class suites, meaningful negative controls, exact HEAD reporting and release-signing provenance.  
**Forbidden:** assuming a green optional/skipped job blocks merge; unapproved edits to production secrets, required checks, signing environments or publication.  
**Exit:** documented admin decision and configured enforcement only after separate owner authorization; existing gated publisher must not weaken.

### S6 — physical matrix and performance evidence (last, owner supervised)

**Tasks:** on one exact authorized signed candidate, control firmware/config, BOTH/TOP/optional BOTTOM, sustained TOP sleep/wake, Guard positive block (if safely inducible), external display/dock, app update same-kernel handover, black-screen stop/recovery; collect monotonic evidence without repeated harmful trials. Independently profile idle CPU, energy proxy/battery resolution and frame-time percentiles using matched workloads.  
**Exit:** completed results categorized PASS / FAIL / NOT RUN / UNKNOWN per candidate and firmware; separate publication decision. Do not re-run already validated historical trials merely for optics.  
**Forbidden:** conflating historical code76/RC78 data with current v1.7.3 behavior, treating energy proxy as battery life.


## Integration with pre-existing parallel engineering work (no duplicate tracks)

**Single public issue ledger:** [#95 — Engineering quality gates after v1.7.3](https://github.com/JestyLabs/Jesty-Thor-Fix/issues/95). This roadmap is the executable scope/acceptance contract for that existing issue; **do not open a competing umbrella issue**. Detailed cross-PR dependencies, evidence and overlap are recorded in [the integration ledger](quality/PARALLEL-WORK-INTEGRATION-2026-10-17.md).

| Existing work | Mapped programme workstream | Boundaries and acceptance |
| --- | --- | --- |
| [#94](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/94) — duplicate/ambiguous I/Q authorization fields | **S2 + S3**, pre-existing scoped runtime/security change | Retain independent DRAFT PR. Baseline host/CodeQL passed; original Android job **CANCELLED**, retried separately. Review legitimate daemon replies and any device-path release gate; do not claim physical authorization from host tests. Do not rewrite, transplant or silently merge its runtime diff into S1/S0. |
| [#96](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/96) — unsigned build-output documentation | **S5 — contributor DX**, separate documentation-only change | CI and CodeQL succeeded on pinned head at time of integration; no build-system behavior change. Preserve as independently reviewable documentation PR, not duplicate work. |
| [#97](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/97) — this governance implementation | **S0** | Roadmap, agent rules, CI contract and S1 plan only; no application runtime. Emulator run at original head failed during generic updater Android state wait; do not mark fully green without valid exact-head retest. |
| [#98](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/98) — real production model transition matrix | **S1**, first test-only slice | Host, emulator and CodeQL CI completed successfully on its first head. Model-only evidence, NOT root cause, no vendor display validation. Remains separate DRAFT PR. |
| [#82](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/82) and [#84](https://github.com/JestyLabs/Jesty-Thor-Fix/issues/84) | **S1** evidence/tracker | Incident documentation only; root cause, physical recovery and fix remain unknown. Keep #65 brightness independent. |
| [#37](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/37) | **Outside programme** | Mixed-refresh research stays independent, no stealth addition to S1 or S6 acceptance. |

**Non-duplication rule:** anyone starting a task must check #95 and the above live PRs before editing. On overlap, reconcile with the existing PR head (read-only first); do not create competing fixes. Different workstreams may run concurrently only when their changed paths and privileged behavior do not overlap. A completed green CI run on a different SHA is not the accepted final-head proof. Programme **scope decisions** are recorded in #95 and surfaced in the relevant PR; a new feature requires a separately approved change-of-scope, not a checklist edit.

## Administrative enforcement — unresolved external gate

The **SirJesty repository account** has GitHub admin permission according to repository permission metadata. **The connected GitHub integration does not have administration API access:** reading `branches/main/protection` returned **HTTP 403, Resource not accessible by integration**; the accessible ruleset listing showed one active *tag* ruleset, not confirmed `main` protection. Account-level admin is **not** equivalent to the connected app holding repository-administration API permission. No branch rule was altered here and none may be claimed configured.

Required follow-up for an authorized repository administrator using GitHub's native settings: review the actual current branch rules without weakening them; require the successful build, both CodeQL analyses and applicable Android integration check with stable names; require CODEOWNERS review where appropriate; consider preventing force pushes/deletions and requiring up-to-date PR checks. Confirm merge queue behavior and whether emulator jobs can be skipped before designating them required. Record exact settings and evidence in #95. A CI script or CODEOWNERS file **cannot** enable those protections. Do not insert a workflow with privileged secrets or request new bypass authority as a workaround.

## Scope controls

- Each S1–S6 implementation increment uses a separate PR tagged with exactly one workstream ID and small testable behavior. S5 may run in parallel but MUST NOT bypass S1–S3 safety gates.
- The PR must state explicitly what it will NOT touch. If a failure genuinely calls for an out-of-order fix, write a proposed roadmap amendment with evidence and seek owner approval *before implementation*. Emergency safety issues can be documented/contained immediately, but not silently released.
- Keep #84 open until attributable reproduction + acceptance; keep #65 open separately. #37 mixed-refresh and new-device support are **not part of this programme**.
- Governance edits cannot themselves authorize signing, publication, merge, physical testing or changed GitHub admin settings.
- This document complements, not replaces, [ROADMAP.md](ROADMAP.md). Past [PR #67 ledger](ENGINEERING-HARDENING-TRACK.md) is historical evidence, not instructions to reimplement closed work.

## Repeatable PR evidence block

Copy this into every programme implementation PR:

~~~text
Programme: JTF-QUALITY-2026-10-17 / S?
Objective and explicit non-goals:
Base SHA / head SHA:
Production paths changed:
Pre-change failure or characterization:
Test commands + observed results + negative controls:
HOST / EMULATOR / PHYSICAL evidence (mark each NOT RUN where relevant):
Impact: display BOTH/TOP; lid; CPU; boot; updater; daemon; IPC:
Residual risks and rollback/fallback:
Needs physical owner approval before release: YES/NO (rationale)
Signing / device installation / publication: NOT AUTHORIZED unless separately approved
Change-of-scope decision: NONE or separate approved reference
~~~