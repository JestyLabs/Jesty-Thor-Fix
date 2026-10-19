# Updater Android validation — 2026-10-13

**Historical execution snapshot.** Its repeated-pending characterization describes
the policy at `8e78f75`, before the subsequent [confirmation deduplication](UPDATER-CONFIRMATION-DEDUP-2026-10-13.md).
Do not interpret that former limitation as the current implementation contract.

Baseline: PR #67 remote head `3e84c9e9850cb90ef408b50a06df65e31ab7e8ee`.
The existing P1-D suite was retained. This work exercises the real updater and
receiver against Android 13/API 33 on an isolated x86_64 disposable emulator,
using target SDK 25 and newly generated test certificates. No physical device,
production signing, publication, privileged bridge or IPC changes were involved.

## Results

The complete final native matrix passed **14/14 cases**:

| Case | Result |
| --- | --- |
| Native consent declined; installed version preserved and session released | PASS |
| Native consent accepted; APK replaced and process restarted | PASS |
| Download cancelled before commit; no native confirmation | PASS |
| Checksum mismatch rejected before installer session | PASS |
| HTTP failure released transfer and UI ownership | PASS |
| Different signer rejected by real Android archive inspection | PASS |
| Incompatible archive rejected before installer session | PASS |
| Test-only APK rejected by native installer after consent | PASS |
| Former-session and unknown terminal broadcasts replayed; later cache preserved | PASS |
| Old terminal broadcasts during newer transfer preserved its cache/reservation | PASS |
| Pending broadcast replay characterized; repeat launch, one native session | PASS |
| Background native confirmation deferred until Activity resume | PASS |
| Activity recreation during download; cancellation and subsequent retry | PASS |
| Process killed with consent pending; stale session reconciled and retry cancelled cleanly | PASS |

Complementary host validation passed: 197 assertions/33 lifecycle scenarios,
137 assertions/18 ownership scenarios, 15 receiver assertions, dashboard models,
commit gate, queue dispatch, confirmation/ownership stores and replacement policy.
Release-policy and public-content checks passed. The unsigned product build passed;
its DEX contains 231 class definitions and no emulator test Activity/harness.
The resulting unsigned APK was not installed.

## Small runtime correction

The native process-recovery test exposed a stale installer window: after startup
abandoned the previous session, starting a new confirmation with an added
`FLAG_ACTIVITY_NEW_TASK` could bring the previous system installer task forward.
Cancelling that window left the new session pending. This was reproduced against
the unmodified baseline, with native callbacks distinguishing the abandoned old
session from the newly pending session.

The receiver now passes the platform confirmation Intent without adding that flag.
`AppUpdater` already starts confirmation only from a resumed Activity, or defers it
until resume; an Activity context does not require a forced new task. The original
platform flags remain unchanged. The same process-recovery reproduction passed
after this isolated change, and the complete matrix also passed. This addresses
the observed API 33 emulator failure; it does not establish every firmware's UI
behavior. See Android's [task and back-stack documentation](https://developer.android.com/guide/components/activities/tasks-and-back-stack).

No updater ownership, transfer/commit gate, endpoint validation, signer policy,
manifest, service, IPC or privileged runtime behavior was changed by the fix.

## Validation boundary

These are native Android updater tests, not physical Thor acceptance. Production
sources are compiled into an independent test APK with a test-only Activity/Host.
APK bytes come from a controlled URL handler and release metadata from the
validated cache. Live HTTP/TLS, redirects and release discovery are untested.
Source-install permission is pre-granted; the Settings permission-grant journey
is untested. Native per-install consent, archive verification, installer sessions
and callbacks remain real.

Duplicate callbacks are explicit injected broadcasts with completion acknowledgements.
The pending-callback replay currently launches the same native confirmation again;
this PASS characterizes that behavior and does not claim deduplication. Recovery
abandons an old native session and permits retry; it does not restore consent
after death. The test Host permits installation and cannot validate the actual
dashboard readiness gate or physical daemon handover. No firmware, display/wake,
production certificate rotation or hardware support claim follows.

The PR emulator job now builds these fixtures with disposable keys and runs the
matrix after the existing lifecycle/installer tests. Local PASS does not imply a
remote CI run has completed. GitHub check enforcement/settings are unchanged.
Raw captures, process/session identifiers, APKs and keys are retained owner-local
outside Git; this document contains only sanitized engineering results.

Runner and reproduction instructions: [test harness](../tests/android/updater-e2e/README.md).
