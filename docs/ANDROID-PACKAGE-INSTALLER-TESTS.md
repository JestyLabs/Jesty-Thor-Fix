# Android PackageInstaller callback integration — test-only

This research workstream is layered on [PR #51](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/51).
It is an independently authored, **disposable emulator test agent**, not a new
component of the Jesty Thor Fix application.

## Test boundary

The new Android-only harness calls the **real platform**
`PackageInstaller.createSession/openWrite/fsync/abandonSession/commit`, and
receives platform callbacks via an explicit, non-exported
`BroadcastReceiver` and mutable `PendingIntent`.

The CI emulator is API 33, has no `PServerBinder`, and receives APKs signed
with a **disposable test key** generated in the same workflow. The harness
has its own package and is never included in the production APK.

Automated paths:
1. Install the actual Thor Fix APK, previously signed with the disposable key
   by PR #51. Capture the package's `lastUpdateTime`.
2. Install the test-only harness with the same disposable key.
3. Prepare a real PackageInstaller session, stream APK bytes, `fsync`, call
   `abandonSession`, confirm the session is no longer owned, and verify the
   target package was not updated.
4. Prepare a new session, `commit` with an explicit mutable status
   `PendingIntent`, verify the platform delivers
   `STATUS_PENDING_USER_ACTION` and a system confirmation intent.
5. Open the actual Android confirmation UI, decline with BACK, confirm that
   the callback reports `STATUS_FAILURE_ABORTED` and package timestamp
   remains unchanged.

The script strictly refuses non-emulator ADB serials and checks the AVD
identity. It never performs privileged vendor commands, restarts
SurfaceFlinger, triggers CPU Fix or manipulates DRM/CRTC.

## Known initial CI failure and guard against flakiness

The first emulator integration run (37836879071) failed during the
pre-commit abandon test with `IllegalStateException`; that early version
recorded neither a stage nor an exception message. The precise cause is
**not proven**. One plausible cause was an immediate `getMySessions()`
query still listing the just-abandoned session. Later CI runs succeeded
with exactly that implementation, so an intermittent visibility delay remains
possible, not established.

To avoid assuming synchronous removal, the harness now observes the list for
up to **2 seconds**, fails explicitly if the abandoned session remains,
records the stage, and resets the result on every invocation. The emulator
job repeats **five independent abandon cycles** before testing real
`PENDING_USER_ACTION` and `STATUS_FAILURE_ABORTED` callbacks. The extra
wait is **only** in the disposable test harness; app production behavior
and the updater are unchanged. If the repeated test still fails, inspect
`ThorInstallerCI` and stop rather than relaxing the requirement.

## Evidence limits

- **Platform-level integration**, not a test of Jesty Thor Fix's
  `UpdateInstallReceiver` or the in-app `AppUpdater` UI.
- `adb install` is used only to prepare the sandbox. The actual session
  under test uses app-driven PackageInstaller APIs and receives real Android
  callbacks. This is stronger than PR #51's package replacement smoke.
- It does not prove PR #49's `UpdateCommitGate` is wired correctly to Android.
  That state model still needs host race tests and an app-wiring review.
- A declined confirmation says nothing about process death *after* commit,
  successful signed upgrades, old-daemon replacement or firmware behavior.
- Callback and UI timing are system dependent: if CI fails, investigate
  rather than relaxing checks or silently skipping scenarios.

## Follow-ons

1. Test real signed in-app updater installation on a dedicated physical
   AYN Thor only after approval, same release certificate, backup/rollback
   plan and both CRTCs observed. Do not force a vendor-service outage.
2. Extend instrumentation to exercise app-owned `UpdateInstallReceiver`
   callback paths, including missing confirmation and interrupted lifecycle.
3. Keep [PR #49](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/49)
   independent and draft until Cancel-vs-commit races and physical user
   confirmation have been validated.
4. No merge or stable release from these tests alone.
