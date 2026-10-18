# S1 — watcher producer and call-order evidence (offline)

**Programme:** JTF-QUALITY-2026-10-17 / S1. **Reference:** integration candidate `b7e2994e3e65998728793db7ad1b3c2393c0ad7c`. **Status:** source-level caller audit; the actual native/Android/Thor scheduling still requires separate evidence.

## Question addressed

[PR #102](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/102) demonstrated with the real `DisplayActionCoordinator` that **artificial simultaneous invocations** can overwrite a newer BOTH state with an older TOP state while one invocation is held in a simulated Guard callback. It did **not** prove that the production watcher actually dispatches parallel invocations.

The production `DaemonWatchThread.smali` loops synchronously: Settings `IContentProvider.call` returns, then exactly one invocation of `onWatcherSample(mode)` completes before the thread sleeps or begins another poll. `WatcherSupervisor` creates a watcher, starts it, then waits with `join()` before any retry. `DaemonRuntime.run()` obtains `SecureChannel.listen()` before starting `WatcherSupervisor`; `SecureChannel.listen()` takes a filesystem `tryLock()` **before** binding the private Unix socket. The event callback notifies `WatcherCadence` / `noteDisplayOn()` rather than invoking `onWatcherSample` directly.

These facts support **serialized delivery along the audited primary production path**, and materially narrow S1-L04's practical applicability. They **do not prove impossibility** of extra reflection/callers, extraordinary process states, stale Settings observations, vendor races or physical panel failures. #84 and #65 stay open; do not infer their cause.

## CI regression contract

The script `tests/test-watcher-producer-contract.py` scans **all runtime Java under `src` and all runtime Smali under `apk/smali`**, requiring the precise coordinator call-site set to be only the existing watcher. It checks the sequential Settings read/dispatch/wait order, supervisor start/join, exclusive lock-before-watcher start, private socket bind order and the callback's passive behavior. Five negative controls ensure a second Java caller, **second watcher-supervisor startup**, missing join, missing exclusive lock and newly introduced direct callback power action each FAIL the check.

The test makes **no source/runtime changes**, does not spawn Android processes and cannot prove bytecode execution or physical side effects. It is intentionally an **architecture drift alarm**, not a concurrency model checker or a physical safety proof.

## What remains before release

1. Characterize **sequential stale** Settings mode observations and display event ordering through read-only fixtures, without hardware writes.
2. Evaluate existing passive trace retention, event order/monotonic timestamps, anonymization and CPU cost (no new always-on logging without measurements).
3. Check whether #94 handover parser behavior requires device acceptance (it does) and perform authorized exact-APK firmware-specific physical tests with recovery criteria, particularly TOP sleep/wake.
4. Only on fully green integrated CI/CodeQL plus physical acceptance and independent release approval, consider main merge and signing/publication.

**HOST:** static audit/negative controls run in CI once validated. **ANDROID EMULATOR:** no producer scheduling proof. **AYN THOR:** NOT RUN. No daemon, screen, wake, brightness, updater, versioning, signing or publication change here.
