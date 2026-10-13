# Updater confirmation deduplication — 2026-10-13

Baseline: PR #67 at `8e78f756f27530bd30db7455766a9f06eee385f8`.
The previous 14-case native updater matrix and exact-head CI passed. It explicitly
characterized repeated pending callbacks as repeat launches, without claiming
deduplication. This change closes that policy gap; no installer consent is bypassed.

## Contract and failure behavior

- Session ownership is still checked by `AppUpdater` before storing or launching
  a confirmation. Unknown and superseded native callbacks stay inert.
- Same-session deferred callbacks preserve the first paired platform Intent and
  produce one reminder, rather than replacing the Intent or repeating the toast.
- A launch is claimed before calling the resumed Activity, so a reentrant or later
  duplicate cannot open another confirmation for that session.
- Pause/resume does not reset the claim. Matching terminal cleanup or a deliberate
  replacement resets the bookkeeping; an older terminal cannot release a newer
  session's claim.
- If Activity launch throws, the existing failure path invalidates and abandons
  that native session. It is not retried by replaying the obsolete callback.
  A new update attempt with a new native session can ask for consent normally.
- After process death, the existing policy still abandons stale native sessions
  at startup and permits a fresh retry. Confirmation claims are process-local.

The pure `UpdateConfirmationStore` owns coalescing and launch claims; Android UI
and terminal recovery remain in `AppUpdater`. No gate, IPC, watcher, CPU/boot,
manifest, privileged service or production signing policy is changed.

## Validation

The updated real-class host lifecycle assertion failed on the baseline's two
foreground launches. The updated native replay assertion also failed on the
baseline: after the first system confirmation appeared, a replay queued another
pending Intent. Replay was sent through a test-only control receiver without
bringing the app Activity forward or dismissing the original native installer.

Host models cover coalescing, terminal/replacement reset, obsolete cleanup and
24 simultaneous claims admitting one launch. The real updater/receiver host
tests cover foreground and pause/resume duplicates, terminal cleanup, a new
session, and the existing launch-exception recovery path.

The Android suite adds real-platform coverage for an injected replay during launch
and an injected launch exception followed by a fresh-session retry. It retains
the original consent, verification, native failure, Activity/process recovery and
stale-callback cases. Native per-install consent, archive verification and
`PackageInstaller.Session` remain real; the suite compiles production updater
sources unchanged into a disposable test APK.

The updated real-class host lifecycle suite passed **198 assertions/33 scenarios**;
the ownership suite passed **137 assertions/18 scenarios** and receiver coverage
passed 15 assertions. Dashboard/store/gate regressions and the unsigned product
build passed, with 231 product DEX classes and no test Activity/control receiver.
The four focused native confirmation scenarios passed on API 33.

One early local complete-matrix attempt encountered a framework
`ConfigurationController` startup exception immediately after APK replacement,
before the test Activity ran. The unchanged fixture passed the same acceptance
case when rerun individually. The original failure is retained owner-local and
has no assigned root cause; no automatic retry, swallowed exception or relaxed
assertion was added. A successful later execution does not establish absence
of intermittent emulator/framework startup failures.

Complete native matrix and exact-head CI results are recorded separately from
that initial failed execution.

The final complete local execution passed **16/16 native cases** with the same
fixture APKs, including successful APK replacement/process restart, all four
confirmation-policy scenarios, Activity recreation and actual process death.
Exact-head remote CI remains an independent gate; local PASS does not imply its
completion or close the separately reviewed security finding.
Raw logs, process/session identifiers, APKs and fixture private keys stay outside
Git. No physical device acceptance follows from these tests.

## Boundaries

Transport is a controlled fixture and release metadata is cached; live TLS,
redirects and discovery remain untested. Source-install permission is pre-granted;
its Settings journey is untested. Duplicate callbacks and launch failure are
explicitly injected; this is not evidence that Android naturally emitted those
duplicates or exceptions. Successful `startActivity` return alone is not proof
of visible consent on every firmware; the suite separately observes system UI on
the tested API 33 emulator. The test Host does not validate production dashboard
readiness or physical daemon handover.

The previous native validation snapshot is [preserved separately](UPDATER-ANDROID-VALIDATION-2026-10-13.md).
Build/run instructions: [Android updater harness](../tests/android/updater-e2e/README.md).
