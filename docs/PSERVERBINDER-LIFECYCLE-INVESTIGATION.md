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
device serial and a **new private output directory outside every Git checkout**.
Both parameters are mandatory. The collector rejects Git output paths, symlink or
junction ancestors, existing output directories, and a selected model that is not
Thor. The default is one snapshot. A short passive sequence may be used during a
naturally occurring anomaly. `-AdbPath` may select a locally verified ADB binary.
Optional `-LocalPservice` and `-ExpectedPserviceSha256` are supplied locally; no
device-specific binary path or hash is embedded in this public collector.

The collector records boot/process identities, ServiceManager visibility, hashes,
readable Binder state and bounded investigation context. Each command's exit code
is recorded. Missing files, permission denials and unavailable reads are UNKNOWN,
not successful negative observations. Samples are sequential, not atomic.

Service check/list are read-only ServiceManager Binder IPC. The collector does not
invoke the vendor command handler, restart services, signal processes, change
properties/settings/SELinux or mutate device files. Raw logs can contain personal
data and must remain local. Only sanitized summaries may be published.

### Collector validation

Host fixtures passed for empty inputs, Git checkout/worktree output rejection,
wrong-model and offline stops, pinned ADB calls, nonzero probe preservation,
UNKNOWN local comparison and overwrite refusal. These fixtures invoke no device.
A single supervised read-only Thor capture completed with the Binder found;
process-identity and Binder-state probes returned nonzero and their output was
preserved for private inspection. This confirms collection and failure recording,
not full access to those sources or a diagnosis of the historical anomaly.

## Next work

1. Use boot/PID/starttime identity for both pservice and ServiceManager.
2. Record process ancestry, groups, cgroups and OOM metadata when readable.
3. Preserve each missing/denied read explicitly.
4. Use the client classification and helper-exit context delivered by PR #50;
   correlate application events with this collector's process/registry samples.
5. Capture a natural incident before any recovery; do not deliberately trigger one.

PR #50 distinguishes lookup-null, rejected transact and sanitized exception
categories. See [implemented diagnostics](PSERVER-HANDOFF-DIAGNOSTICS.md).
These observations do not prove Binder death or select a root cause. Do not add
automatic retries or replay an ambiguous side-effecting command.

Stop if further progress requires service restarts, policy changes, destructive
lifecycle testing or a speculative production fix. Any future implementation
requires a selected cause, bounded design, host validation and supervised physical
testing. See [publication policy](../SECURITY-PUBLICATION.md).
