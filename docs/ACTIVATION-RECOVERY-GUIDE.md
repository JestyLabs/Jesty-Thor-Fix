# Activation and external recovery

**Current public release: [v1.7.3 / code83](https://github.com/JestyLabs/Jesty-Thor-Fix/releases/tag/v1.7.3).** Install normally over the existing version without uninstalling or clearing data, then open the app. A guarded same-kernel handover can activate the updated service automatically when the known predecessor (v1.7.1 or v1.7.2) and current BOTH/display/CPU/Guard state are verified safe. Wait for **ACTIVE** or **STOCK**. If the handover is unavailable, follow the app's conservative restart/recovery guidance; it will not blindly stop an unknown or unsafe process. See [v1.7.3 release notes](RELEASE-NOTES-1.7.3.md).

## Historical v1.7.2 / code80 reboot activation

The following sequence describes **v1.7.2 specifically**, not a universal requirement for v1.7.3. After installing v1.7.2, open Jesty Thor Fix once. Wait for confirmation that activation is prepared, restart the Thor normally, then reopen the app and verify that the installed service is ready. Installing that package alone does not activate its daemon. Returning from sleep or restarting only the Android interface does not count as a kernel reboot.

If the app was never opened before that restart, open it to prepare activation and perform the additional normal restart it requests. Manual opening can validate a later kernel boot even when the boot broadcast was missed. Force-stop alone does not establish a reboot.

RESTART REQUIRED means that preparation succeeded and a different kernel boot is still needed. ACTIVATION BLOCKED means that storage, package evidence or the expected service identity could not be verified. After a verified reboot, a service identity mismatch is not an instruction to keep restarting. Save diagnostics and seek support. Do not clear app data, uninstall, edit activation markers or bypass Android downgrade checks.

## Historical controlled recovery route for code80

The physically exercised private route was **v1.7.2/code80 → recovery/code81 → return/code82**. The exact private recovery/return build labels historically used 1.7.3-recovery/code81 and 1.7.3/code82; **neither is the currently published v1.7.3/code83 APK**. These private packages are not normal updater-catalog releases. Obtain owner-approved files and verification instructions through project support before considering this older route; this page does not offer public recovery downloads or advise installing the lower code81/code82 packages over a code83 installation.

Use the exact approved package through Android's normal external installer, preserving app data, then restart normally. The application ID and signing lineage must match and version codes must increase. After code81 or code82, code80 is not a normal update; likewise a code83 installation cannot normally be downgraded to code81/code82. If the installer rejects a package, stop and preserve the error. Recovery does not depend on this app's updater being operational.

The exact chain was exercised on an awake, healthy Android 13 Thor, including normal reboots, service readiness, selected preference retention and final display checks. Previously authorized ADB provided an external installation route in that session. This does not establish ADB availability during a blackout or guarantee recovery when both UI and transport are unavailable. Recovery addresses activation; it is not a compositor safe mode or a demonstrated blackout fix.

Persistent black screens after TOP sleep/wake remain an [open investigation](https://github.com/JestyLabs/Jesty-Thor-Fix/issues/84). If an anomaly occurs, stop the test rather than repeatedly changing modes or using buttons with the lid closed. Preserve diagnostics when possible and use project support for a recovery procedure appropriate to the actual state.
