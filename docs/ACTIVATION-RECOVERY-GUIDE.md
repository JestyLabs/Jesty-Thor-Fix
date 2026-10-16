# Proposed activation and recovery guide

This guide accompanies an unpublished activation proposal. It does not announce an available stable or recovery package.

After installing an approved version, open Jesty Thor Fix once. When the app confirms that activation is prepared, restart the Thor normally and reopen the app. Check that the installed service is verified and ready. Installation by itself is not activation.

If the app had never been opened before that restart, open it now to prepare activation, then perform the one additional normal restart it requests. The app can verify this transition on manual opening even if the boot notification was missed. Returning from sleep, closing the app, force-stop and restarting only the Android interface do not count as a kernel reboot.

If the app reports ACTIVATION BLOCKED because it cannot save/read evidence or verify the boot/package, do not keep restarting. When storage is available, reopen once. If it still reports a storage or identity problem, stop retrying and use the owner's approved external recovery procedure. Do not uninstall, clear app data, edit the activation marker or force a downgrade.

External recovery must work while this app and its updater are unavailable: obtain the exact approved package and integrity/signing information through the owner's controlled distribution channel, verify them independently, and use Android's normal installer. The replacement must keep the application ID and required signing lineage, preserve app data and have a higher Android version code. If Android rejects it, stop and retain the error rather than bypassing checks.

A release needs a concrete recovery artifact, independent provenance, a complete return-to-stable route and acceptance of that external installation path. These are pending. This document provides the required procedure and constraints; it does not claim that a rescue package exists or that recovery was tested on hardware.
