# Branch archive snapshot — 2026-10-13

**Historical reference preservation only.** This archive is NOT a product integration, not a release, not a verified old branch merge and not a device-test authorization. The archive commit(s) retain the **exact old branch-tip commits as additional parents**, while the tree is the then-current `main` plus only this README and `manifest.json`.

- Snapshot: **41 remote branches**, including protected `main`, active PR #37, active PR #67, and **38 cleanup candidates**.
- Keep **`main`**, **`work/thor-120hz-refresh-investigation` (#37)** and **`work/engineering-hardening-post-rc3-20261012` (#67)**.
- Before removing cleanup branches: create and verify a permanent archive **tag** at the archive branch's final commit; verify reachability of all recorded branch SHAs and re-check current remote heads / open PRs. **No deletes occur merely by creating this archive.**
- Retain code77 recovery and unmerged prototype ancestry via the archive tag; do not copy unvalidated prototypes into `main`.
- The source of truth for current release and user-visible functionality is `main`, not this archive. Backlog notes belong to #67; refresh research stays under #37.

## Unique work still requiring independent assessment

- **Event history (#53, closed):** extra UI, persistence, copy/clear features, not part of the current diagnostics UX; do not merge blindly. Review overlap with bounded App Diagnostics/exports.
- **One-off bright-flash investigation (#62, closed):** report documents a single observed event and a possible ON-during-sleep guard issue; no proven root cause, no recurring issue. Preserve evidence for an optional future targeted review, not a silent display fix.
- **Recovery splash / bootanimation prototypes (#23–#30 and related):** experimental graphics and process-lifecycle work remains unaccepted. Archive; do not merge.
- **Watcher cost profiling, CPU evidence model and fault-injection probes:** candidate test/model techniques for future scoped #67 work only; production behavior requires fresh review.
- **Code77 forward-only recovery (#69, closed unmerged):** emergency-only code; not the published app and never installed. Archive safely until recovery policy changes.

## Audit scope and limitation

An archived branch may report Git commits "ahead" of `main` even when the equivalent code was independently merged or squashed. Conversely, a filename already present in `main` does not prove the branch's *content* was fully integrated. The review checked actual tree path existence and reviewed outstanding designs; it did **not** assert semantic equivalence for all old source files. All archived code remains reachable for focused future comparisons without occupying active branches.

See [the pinned reference manifest](manifest.json). Never change the stable app's source, release tags or protected signer as part of pruning.
