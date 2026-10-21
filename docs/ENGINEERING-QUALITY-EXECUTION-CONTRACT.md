# Execution contract — JTF-QUALITY-2026-10-17

This is an **operational checklist**, not an assurance that a document alone can control contributors. The safeguard is the combination of narrowly scoped PRs, executable tests, maintainer/code-owner review, **actually configured** GitHub required checks and separately authorized physical/release actions. Automated checks cannot substitute for engineering judgment.

## 0. Authority and branch hygiene

- Read root [AGENTS.md](../AGENTS.md), the [quality roadmap](ENGINEERING-QUALITY-ROADMAP-2026-10-17.md), [existing central tracker #95](https://github.com/JestyLabs/Jesty-Thor-Fix/issues/95), [parallel-work ledger](quality/PARALLEL-WORK-INTEGRATION-2026-10-17.md), current HEAD, issues #84 and #65, open PRs (#94, #96, #97, #98 among others) and affected production code. Treat older summaries as historical until reconciled.
- Before editing, write the exact **workstream S1–S6**, objective, allowed touched paths, forbidden paths, behavioral invariants, starting SHA and intended tests in the PR or work note. If there is an overlap with an open PR, resolve the overlap read-only before writing; never cherry-pick over accepted changes blindly.
- One behavior/one responsibility per implementation PR. Use a draft PR until the relevant offline gates are complete. Re-check current main before integration and rerun exact-head CI after any rebase.

## 1. Test-first engineering loop

1. **Characterize the real system.** Map ownership (app, daemon, vendor), lock boundaries, lifecycle and persistent states. Distinguish read-only observations from commands; separate logical CRTC state from actual panel presentation.
2. **State falsifiable safety invariants.** Examples: unknown/asleep state never authorizes a speculative panel ON; stale generation never applies actions; failed authentication cannot reach Binder/kill; old installer callbacks cannot claim new sessions; an incomplete handover never records success.
3. **Build negative and interference cases.** Test duplicates, reordering, stale callbacks, timeout, restart, race, unknown peer/process and idempotence. Prefer production classes/adapters to independent reimplementations. Demonstrate the pre-change failure for actual bug fixes.
4. **Change the minimum.** Keep the original system behavior unless the PR explicitly authorizes a corrected invariant. No dynamic sleep fixes, broad reboot/kill helpers or hidden vendor writes inserted during a characterization PR.
5. **Run and label every tier separately.** HOST (real classes vs models), ANDROID EMULATOR (generic OS only), PHYSICAL THOR (exact firmware/candidate and supervised logs). Record explicit PASS / FAIL / NOT RUN / UNKNOWN for each. Save exact commands, JVM/SDK versions, SHA and reproducible fixture.
6. **Review the entire patch.** Check unintended changes to Android package identity, version code, signatures, manifest/permissions, target SDK, release files, CPU restart, modes, updater persistence, IPC access, diagnostic privacy and release workflows.
7. **Gate delivery.** Require exact-head relevant CI and CodeQL results, tracked failed tests, independent review of security paths, signed candidate provenance and owner-supervised Thor gates if behavior was affected. No unapproved merge or release.

## 2. Non-negotiable stop and escalation triggers

Immediately stop the *affected* work and report the observed state when any condition is true:

- A previously green characterization fails, evidence cannot be reproduced, or there is an unexplained behavioral delta.
- New code could cause persistent black screens, unexpected device reboot, unwanted panel power change, runaway CPU, a dead daemon or unrecoverable installer state.
- Privileged identity is ambiguous (UID, PID, socket ownership, Binder availability, verified daemon incarnation), source/SHA differs from tested artifact, or a check is being relaxed to pass CI.
- A request would require out-of-scope files or workstream reordering; write a **separate scope change proposal** and obtain maintainer approval. Do not add unrelated fixes to the same PR.
- A private log, signer, firmware extract, personal path/ID or vulnerability detail might enter public Git history; keep it owner-local and sanitize before any publication.

When stopped, produce a concise evidence note: observation; expected invariant; source location and revision; command/config; test tier; safe next step; explicit NOT RUN/UNKNOWN. Never disguise a STOP as an accepted fix.

## 3. Privileged and hardware boundaries

- App root/kernel/vendor permissions are high consequence. Preserve 0600/private socket, SO_PEERCRED identity checks, singleton lock semantics, fixed command admission, process-image/UID checks and fail-closed handling.
- Preserve BOTH/TOP transitions, CRTC verification, generation invalidation, boot hold and justified sleep/wake cadence unless source-linked evidence demonstrates the minimum different behavior is safer.
- Hardware/session work needs **separate prior owner authorization**, known-good recovery, exact baseline/firmware and device monitoring. Do not intentionally reproduce persistent blackout by repeated screen toggles. Stop on presentation loss; execute only the pre-agreed recovery.
- No physical trial, production signing, main merge, required-check change, secret/env change or release is implied by approval of an offline PR.

## 4. Evidence ledger and change control

In every programme PR, use the template's "Roadmap execution" section; paste the [roadmap evidence block](ENGINEERING-QUALITY-ROADMAP-2026-10-17.md#repeatable-pr-evidence-block). Link the tracker, exact commit, tests and scope exclusions. Re-evaluate after each meaningful review comment.

**Changes to programme scope:** propose a distinct governance PR that shows old vs new workstream, risk, motivation, alternatives, acceptance gates and explicit maintainer decision. No agent/contributor may amend AGENTS.md, roadmap, this contract or policy checks solely to bypass a failed invariant.

**Enforcement limitations:** root AGENTS.md is guidance automatically surfaced by some coding agents, not a security boundary. PR templates can be edited, CI jobs can be skipped when not required, and CODEOWNERS only enforces review if branch rules require it. The CI roadmap-policy script is a structural tripwire (missing/uncoupled instructions), **not** semantic proof that future changes follow every objective. Verify branch rules separately before asserting mandatory checks.

## 5. Definition of DONE for this governance PR

- Baseline v1.7.3/code83 and unresolved #84/#65 stated correctly.
- Contract + roadmap + S1 offline matrix linked from root and contributor docs; CODEOWNERS entries cover governance files.
- Existing host CI runs a structural roadmap-policy test; no Android runtime, build identity, signing, release or installed-device behavior changed.
- CI PASS is documented once obtained, with any skipped/NOT RUN jobs identified.
- PR left DRAFT for maintainer review; no signing/merge/publication/test on Thor.