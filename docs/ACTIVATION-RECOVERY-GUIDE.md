# Activation and external recovery

**Pre-release: [v1.7.6 / code86](https://github.com/JestyLabs/Jesty-Thor-Fix/releases/tag/v1.7.6).** Updating from published 1.7.5 can complete without a reboot, but on hardware a transfer in TOP was held and needed one normal restart; follow the app's *Restart the Thor once* notice. A held transfer now logs the failed check. Do not repeat the update or clear data.

**Current public release: [v1.7.5 / code85](https://github.com/JestyLabs/Jesty-Thor-Fix/releases/tag/v1.7.5).** Install over the existing version without uninstalling or clearing app data, then open the app and wait for ACTIVE or STOCK. Verified safe transfer from exact published 1.7.1, 1.7.2, 1.7.3 and 1.7.4 services can proceed without a device reboot.

The process-bound adapter verifies the predecessor instance, waits for its confirmed exit and separately verifies successor READY. It does not weaken authentication, kill an unverified PID or promise transfer under every mode/device state. See [the implementation contract](quality/S2-PROCESS-BOUND-RETIREMENT.md) and [release validation](VALIDATION-1.7.5.md). Android/JNI coverage passed; exact released-APK Thor acceptance remains pending.

If the app requests a normal restart because safe transfer or activation cannot be verified, follow that guidance. Do not clear data, force process replacement, change activation markers or loop through restarts after an ACTIVATION BLOCKED identity error. Save available diagnostics and seek support. The older code81/code82 recovery packages below cannot normally be installed over code85; they are not a public rollback route.

## Historical v1.7.3 upgrade limitation

v1.7.3/code83 did not admit the published v1.7.1 daemon to automatic handover; that particular upgrade used the guarded normal-restart route. v1.7.4 adds the separately tested process-bound adapter. Earlier release wording and tests do not establish physical acceptance of the new adapter.

## Historical v1.7.2 / code80 reboot activation

The following sequence describes **v1.7.2 specifically**, not a universal requirement for v1.7.3. After installing v1.7.2, open Jesty Thor Fix once. Wait for confirmation that activation is prepared, restart the Thor normally, then reopen the app and verify that the installed service is ready. Installing that package alone does not activate its daemon. Returning from sleep or restarting only the Android interface does not count as a kernel reboot.

If the app was never opened before that restart, open it to prepare activation and perform the additional normal restart it requests. Manual opening can validate a later kernel boot even when the boot broadcast was missed. Force-stop alone does not establish a reboot.

RESTART REQUIRED means that preparation succeeded and a different kernel boot is still needed. ACTIVATION BLOCKED means that storage, package evidence or the expected service identity could not be verified. After a verified reboot, a service identity mismatch is not an instruction to keep restarting. Save diagnostics and seek support. Do not clear app data, uninstall, edit activation markers or bypass Android downgrade checks.

## Historical controlled recovery route for code80

The physically exercised private route was **v1.7.2/code80 → recovery/code81 → return/code82**. The exact private recovery/return build labels historically used 1.7.3-recovery/code81 and 1.7.3/code82; **neither is the currently published v1.7.3/code83 APK**. These private packages are not normal updater-catalog releases. Obtain owner-approved files and verification instructions through project support before considering this older route; this page does not offer public recovery downloads or advise installing the lower code81/code82 packages over a code83 installation.

Use the exact approved package through Android's normal external installer, preserving app data, then restart normally. The application ID and signing lineage must match and version codes must increase. After code81 or code82, code80 is not a normal update; likewise a code83 installation cannot normally be downgraded to code81/code82. If the installer rejects a package, stop and preserve the error. Recovery does not depend on this app's updater being operational.

The exact chain was exercised on an awake, healthy Android 13 Thor, including normal reboots, service readiness, selected preference retention and final display checks. Previously authorized ADB provided an external installation route in that session. This does not establish ADB availability during a blackout or guarantee recovery when both UI and transport are unavailable. Recovery addresses activation; it is not a compositor safe mode or a demonstrated blackout fix.

Persistent black screens after TOP sleep/wake remain an [open investigation](https://github.com/JestyLabs/Jesty-Thor-Fix/issues/84). If an anomaly occurs, stop the test rather than repeatedly changing modes or using buttons with the lid closed. Preserve diagnostics when possible and use project support for a recovery procedure appropriate to the actual state.
