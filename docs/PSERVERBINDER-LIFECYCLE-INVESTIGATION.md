# PServerBinder lifecycle investigation

Status: read-only research; no runtime or release behavior change.

## Problem

A historical Thor observation found stock pservice alive while PServerBinder was
missing. A fresh pservice instance restored the bridge while the display-process
identities remained unchanged. This proves recovery, not the cause of disappearance.
Detailed session evidence and binary/security analysis are retained locally.

## Cross-device update (2026-10-08)

The verified Thor / Flip 2 comparison establishes close Binder-core derivatives,
not identical servers. See the [public comparison](PSERVER-CROSS-DEVICE-EVIDENCE.md).
Client compatibility does not transfer startup, policy or cleaner behavior.
No production recovery mechanism is justified by the current evidence.

## Hypotheses and discriminants

| Hypothesis | Evidence needed |
|---|---|
| H1: initial registration failure | Startup registration status/denial with exact process identity |
| H2: registered service later lost | Found then absent with unchanged boot/PID/starttime |
| H3: ServiceManager generation changed | New ServiceManager identity while pservice identity stays fixed |
| H4: add/find policy visibility | Existing policy/denial evidence and caller-specific visibility |
| H5: client lookup failure | Simultaneous shell and application lookup observations |

Do not collapse these hypotheses into a single cause. A stalled Binder worker is
not sufficient by itself to explain an absent ServiceManager registry entry.

## Read-only collector

Use `scripts/collect-thor-pserverbinder-readonly.ps1` with the explicitly selected
device serial and an output directory outside the repository. The default is one
snapshot. A short passive sequence may be used during a naturally occurring anomaly.

The collector records boot/process identities, ServiceManager visibility, hashes,
readable Binder state and bounded investigation context. Each command's exit code
is recorded. Missing files, permission denials and unavailable reads are UNKNOWN,
not successful negative observations. Samples are sequential, not atomic.

Service check/list are read-only ServiceManager Binder IPC. The collector does not
invoke the vendor command handler, restart services, signal processes, change
properties/settings/SELinux or mutate device files. Raw logs can contain personal
data and must remain local. Only sanitized summaries may be published.

## Next work

1. Use boot/PID/starttime identity for both pservice and ServiceManager.
2. Record process ancestry, groups, cgroups and OOM metadata when readable.
3. Preserve each missing/denied read explicitly.
4. Add isolated client diagnostic classification if a concrete failure needs it.
5. Capture a natural incident before any recovery; do not deliberately trigger one.

Possible client classifications are LOOKUP_NULL, LOOKUP_EXCEPTION, BINDER_DEAD,
TRANSACT_FALSE and TRANSACT_EXCEPTION. These are diagnostic requirements, not a
reason to add automatic retries or replay an ambiguous side-effecting command.

Stop if further progress requires service restarts, policy changes, destructive
lifecycle testing or a speculative production fix. Any future implementation
requires a selected cause, bounded design, host validation and supervised physical
testing. See [publication policy](../SECURITY-PUBLICATION.md).
