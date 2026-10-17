# Activation and external recovery

This guide describes the activation and external recovery behavior of Jesty Thor Fix v1.7.2. The public stable APK is linked from the README.

After installing an approved package, open Jesty Thor Fix once. Wait for confirmation that activation is prepared, restart the Thor normally, then reopen the app and verify that the installed service is ready. Installing the package alone does not activate its daemon. Returning from sleep or restarting only the Android interface does not count as a kernel reboot.

If the app was never opened before that restart, open it to prepare activation and perform the additional normal restart it requests. Manual opening can validate a later kernel boot even when the boot broadcast was missed. Force-stop alone does not establish a reboot.

RESTART REQUIRED means that preparation succeeded and a different kernel boot is still needed. ACTIVATION BLOCKED means that storage, package evidence or the expected service identity could not be verified. After a verified reboot, a service identity mismatch is not an instruction to keep restarting. Save diagnostics and seek support. Do not clear app data, uninstall, edit activation markers or bypass Android downgrade checks.

## Controlled recovery route

The controlled route is 1.7.2/code80 → 1.7.3-recovery/code81 → 1.7.3/code82. These recovery/return packages remain outside the normal updater catalog. Obtain them and their verification instructions through project support before attempting recovery; this document does not advertise a public recovery download.

Use the exact approved package through Android's normal external installer, preserving app data, then restart normally. The application ID and signing lineage must match and version codes must increase. After code81 or code82, code80 is not a normal update. If the installer rejects a package, stop and preserve the error. Recovery does not depend on this app's updater being operational.

The exact chain was exercised on an awake, healthy Android 13 Thor, including normal reboots, service readiness, selected preference retention and final display checks. Previously authorized ADB provided an external installation route in that session. This does not establish ADB availability during a blackout or guarantee recovery when both UI and transport are unavailable. Recovery addresses activation; it is not a compositor safe mode or a demonstrated blackout fix.

Persistent black screens after TOP sleep/wake remain an [open investigation](https://github.com/JestyLabs/Jesty-Thor-Fix/issues/84). If an anomaly occurs, stop the test rather than repeatedly changing modes or using buttons with the lid closed. Preserve diagnostics when possible and use project support for a recovery procedure appropriate to the actual state.
