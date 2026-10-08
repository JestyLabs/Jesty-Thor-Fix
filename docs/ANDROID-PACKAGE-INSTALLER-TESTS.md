# Android PackageInstaller callback integration — test-only

This research workstream is layered on [PR #51](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/51).
It is a disposable emulator test agent, separate from the Jesty Thor Fix application.

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

## Current emulator denial-callback investigation (2026-10-08)

The post-history-rewrite CI build and CodeQL checks passed, as did five
independent pre-commit session abandonment cycles and a real
`PENDING_USER_ACTION` callback. The same run did **not** observe
`USER_ABORTED` after a simulated BACK keypress on the install confirmation UI:
the recorded callback remained `PENDING_USER_ACTION` after the bounded wait.

This is a failure of the current emulator acceptance check, not evidence that
the production updater completed an unwanted install. The run stopped before
the final package timestamp assertion; do not mark refusal verified.

**Root cause identified in diagnostic run 37847715921:** the focused window
remained `ImmersiveModeConfirmation` before and after BACK, while Android's
`PackageInstallerActivity` was the resumed activity behind that tutorial.
Therefore the keypress did not demonstrate actual refusal of the install UI.

The emulator-only workflow now sets Android's documented secure immersive
confirmation flag to `confirmed` before running either lifecycle smoke or
installer checks. The installer harness also verifies the installer owns
the focused window before injecting BACK, preserving a hard failure if any
tutorial or unrelated UI still intercepts the input.

These are **emulator-test environment changes only**. No app/update/daemon
behavior or callback expectation has changed. The real terminal
`USER_ABORTED` callback and unchanged target APK timestamp remain mandatory;
the revised job must pass before the denial case is accepted.

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
3. [PR #49](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/49)
   has merged independently after host races and Android 13 native-session
   cancellation tests. This harness checks the platform confirmation/refusal
   callback, not the production updater's in-process receiver.
4. Emulator acceptance is not evidence of a successful signed upgrade on
   the physical Thor. No release is created by this test-only PR.

## Integration hardening (2026-10-08)
- Resolved review findings in the isolated test APK: the manifest no longer
  enables debugging, and error logging no longer includes the untrusted
  test-mode Intent value.
- The emulator harness now emits bounded `ThorInstallerCI` result markers
  into the Android log; CI no longer requires `run-as` or access to the app's
  private preferences. Stored test statuses remain internal to the harness.
- Each of the five abandon attempts clears the emulator test markers to prevent
  stale successful events from satisfying a new iteration.
- The CI workflow preserves the existing mainline signing protection and
  incorporates the installer test as part of the Android lifecycle job.
- These checks are confined to a disposable AVD, do not alter production
  installer behavior, and do not install or upgrade software on a physical Thor.
