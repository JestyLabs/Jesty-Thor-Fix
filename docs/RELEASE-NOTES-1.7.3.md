# Jesty Thor Fix v1.7.3

## Changes

- **Automatic service transfer after updating:** a normal kernel reboot is no longer required when the installed older service can be positively identified and replaced under safe conditions.
- The handover checks the exact v1.7.2 predecessor identity, live BOTH mode, both active display pipelines, matching display/CPU/Guard settings, and a healthy service. It waits for the old service to exit and verifies the new service identity before allowing controls again.
- If the device is in TOP mode, switch to BOTH; the app retries automatically when the display state is confirmed. **Do not switch modes rapidly or force-stop the app during transfer.**
- Existing preferences remain intact. Global early CPU hooks are handled only after the new service acquires its exclusive lock.

## Install

Install normally over the existing version, without uninstalling or clearing data. Open Jesty Thor Fix and allow the background service to transfer. Wait until the dashboard confirms **ACTIVE** or **STOCK**. Where safe transfer is not possible, the app will explain the fallback; a normal reboot remains available.

**From published v1.7.1:** automatic service transfer is unavailable. Install the update, open it to prepare activation, restart the Thor normally, then reopen the app and verify **ACTIVE** or **STOCK**, preserving app data throughout.

If v1.7.2 has not been activated and its in-app updater is unavailable, install the new APK using Android's normal external installer. Keep application data.

## Limits and existing issues

An unknown or unhealthy predecessor, unavailable privileged bridge, uncertain display state, failed stop, or storage failure **does not authorize an automatic kill**. The app retains the conservative restart/recovery path in those cases. A submitted root command is not treated as proof of completion.

Persistent TOP sleep/wake black screens remain an open investigation ([#84](https://github.com/JestyLabs/Jesty-Thor-Fix/issues/84)). The brief BOTTOM brightness flash remains tracked separately ([#65](https://github.com/JestyLabs/Jesty-Thor-Fix/issues/65)). This update does not claim to fix either issue.
