# Engineering boundary map — PR #67 (offline, 2026-10-13)

**Source state:** v1.7.1 shipped runtime is the accepted baseline. This is a static review of the PR #67 branch, not a new hardware, firmware, battery, or Android updater acceptance run. The PR stays draft; any later concurrent Codex changes require comparison before integration.

## Ownership and concurrency map

| Component | Current responsibility / observable boundary | Lock, state, or failure invariant | Test tier |
| --- | --- | --- | --- |
| `AppUpdater` (~794 source lines before extraction) | UI/update preferences, version lookup and cached GitHub releases, HTTPS download/hash, archive signer checks, PackageInstaller write/commit and lifecycle | One worker, atomic `busy/checking/cancelled`, synchronized `UpdateCommitGate`, UI-only `setInstallReserved` before download; `PackageInstaller` owns post-commit changes | Host: `UpdateVersionTest`, `UpdateCommitGateTest`, `UpdateReadinessTest`; Android emulator session harness; real complete production updater **not** proven |
| `UpdateVersion` | Existing pure selection, version, digest/URL, signer compatibility and install-gating policy | No Android or I/O; reject unverifiable assets and incompatible signers | Host |
| `UpdateReadiness` | Receives latest dashboard sample; conservatively preserves a known boot hold if the daemon becomes unavailable | Multiple volatile fields and monotonic sample time, but no claim of a fully atomic multi-field snapshot | Host fixture with clock; emulator/app lifecycle remains incomplete |
| `UpdateConfirmationStore<T>` (new) | One deferred confirmation+session pair until Activity resume | `defer/take` remain one-slot; `discardSession(id)` removes **only its own** terminal confirmation; `discardAll()` invalidates an older prompt when explicitly starting a replacement | 80 host concurrency trials, targeted-invalidation tests, receiver host test; Android callbacks not complete |
| `UpdateSessionOwnership` (new) | In-process attempt generation and Android session-ID association; only current session owns cleanup | `beginTransfer/registerSession/finishSession` synchronized; rejects unknown/stale/duplicate callbacks; process death resets in-memory ownership and startup may abandon stale sessions | Host tests and actual receiver/spy tests; real Android/production AppUpdater coupling still must be revalidated |
| `UpdateInstallReceiver` | Android's pending/success/abort/failure callback dispatch | Non-exported receiver; guards on `isActiveInstallSession(sessionId)` before any status, so old/malformed statuses cannot clear a subsequent transfer or show an obsolete confirmation | Actual class compiled with fake Android services; emulator E2E with real updater still pending |
| `BootCoordinator` (~316 lines) | Boot CPU and display reconciliation sequencing, held/ready transitions and failure timeouts | CPU phase runs before display phase; BootSafety hold prevents unsafe reconciliation; display consistency must be observed before ready and Lid Guard restoration | Pure gate-model host tests; vendor/display semantics only physically provable |
| `CpuFixController` (~852 lines) | Desired CPU state, persisted early-attempt proof, thread/helper ownership, compositor restart and recovery | Instance `lock`; `restartScheduled`, `restartFailed`, durable CPU-attempt state, timed wake lock and helper observation are coupled. Do not move restart, proof writes or recovery ordering piecemeal | Host gate/proof/recovery models; full vendor Binder/restart validation only on Thor |
| `WatcherCadence` / `WatcherCadenceModel` | Event-assisted polling, mode/DRM/wake repair cadence and counters | `LOCK` synchronizes runtime cadence; pending vs due repair and no-event safety fallbacks must survive idle optimization | Model/runtime host tests + pinned physical idle/sleep windows |
| `SecureChannel` / `DaemonIpcServer` / `DaemonCommandHandler` | Kernel peer UID/inode permissions, singleton filesystem socket, fixed one-byte protocol, 2 workers+4 queue, 1500 ms per-connection read timeout, allowed command set | App/daemon authentication is from OS peer credentials and inode, **not from `ok=1`**. Recent `DaemonResponsePolicy` strengthens **client** reply parsing only | Host policy and ACK tests, no real-UID kernel/socket saturation test |

## Characterization-first integration order

1. **Already in #67:** isolate only deferred confirmation state in `UpdateConfirmationStore`, with one-slot semantics preserved; the full JDK 17/Android emulator CI proved this refactor builds, not that real stale callbacks are rejected.
2. **Next updater extraction candidate, not started:** separate network release transport/metadata loading from Activity/dialog/session state only after constructing deterministic tests for missing digests, redirects, size bounds, exceptions, cache eviction, and ensuring all trusted-release rules continue to delegate to `UpdateVersion`. Do **not** fork `UpdateVersion` or `UpdateCommitGate` into another policy implementation.
3. **Updater correctness now has a draft implementation:** actual `AppUpdater`/receiver callback correlation is keyed by current native session ID, deferred prompts can be invalidated per session, and stale terminal statuses cannot clean a new transfer. A replacement attempt tries to abandon the superseded native session. **Still pending:** execute Codex's original coupled-real-AppUpdater reproductions against this exact PR head and test actual Android PackageInstaller callback routing in an isolated emulator, including process death. Host tests alone do not close these gates.
4. **CPU/boot:** first record the exact ownership and recovery sequence from `CpuFixController.applyLocked`, `runRestart`, `onHelperExit`, persisted marker transitions, and `BootCoordinator.reconcileCpuPhase/reconcileDisplayPhase`; characterize with existing models before selecting any helper extraction. No restart/boot/DRM code moves in the present offline increment.
5. **IPC:** a deterministic fake transport can model queue rejection, interrupted reads and malformed/truncated replies, but model results must not be described as Linux peer credential, vendor PServerBinder or on-device liveness proof.

## Shipping and measurement boundaries

- Existing code76 physical BOTH/TOP CPU windows and sleep/wake baselines are **not** a valid A/B energy or gaming FPS comparison. `Thread-1` tick attribution does not identify a Java stack.
- Normal Guard ON smoke has been observed; positive interception of an unintended closed-lid wake was **not observed**. Dock/external/BOTTOM-only cases remain pending.
- PR #67 has two client/app runtime changes (`SocketClient` strict ACK and `AppUpdater` confirmation state); both require affected-path acceptance before any signed release, despite Java/emulator CI success.
- The CI test contract can ensure the PR emulator context is instantiated for every PR, but it **cannot** make it required in GitHub branch protection. That final administrative policy remains a separately authorized change.
- Do not merge, sign, publish, install or run vendor fault injection as part of this map.

## Offline Codex validation to request (without the Thor)

- Rebuild on clean **Windows JDK 17** with the exact pinned Apktool and Android SDK; execute host suites and both emulator integrations. Report command, SHA, reproducible result, and fixture tier.
- Compare the Codex local head to current PR #67; do not cherry-pick outdated `main`/`PR67` source files or overwrite newly added tests.
- Confirm `UpdateConfirmationStore` preserves existing intent/session lifetime and does not accidentally allow cross-Activity presentation; list precise stale callback/session failure scenarios instead of silently changing rules.
- Read-only inspect current GitHub branch requirements and env protection; do not mutate settings. Return private evidence separately from public-facing findings.
