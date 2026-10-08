# Target SDK 25: dependency audit and isolated migration plan

> Research only. No manifest, release, signing certificate, device setting, or runtime behavior changes are authorized by this document.

## Verified repository facts (2026-10-08)

- `apk/AndroidManifest.xml` declares `android:targetSdkVersion="25"` for package `com.thor.displaypowertest`; the reference device documented in `docs/COMPATIBILITY.md` runs Android 13.
- `apk/smali/com/thor/displaypowertest/BootReceiver.smali` receives `BOOT_COMPLETED` and starts `AutoService` using `Context.startService(...)` (lines 49-76).
- `AutoService.onStartCommand` starts a bounded worker, probes the current private daemon socket, and invokes `PServer.startDaemon` only when the daemon is absent or a verified predecessor is replaceable. It is not a foreground service and calls `stopSelf` at the end.
- `PServer.send` reflectively uses `android.os.ServiceManager.getService("PServerBinder")`, then `IBinder.transact(0, ...)`. This call runs in the app process and must not be assumed to survive a higher target SDK.
- `AppUpdater.canInstallPackages` explicitly bypasses the `canRequestPackageInstalls()` check for targets below 26. Its installer uses a user-confirmed `PackageInstaller` session. `REQUEST_INSTALL_PACKAGES` is already declared.
- The manifest already specifies exported states for components and the updater specifies mutable `PendingIntent` flags on Android 12+; those are not evidence that every higher-target migration requirement is satisfied.
- `EarlyCpuOptIn` depends on device-protected app files and a fixed package-specific path; validate access, ownership, UID and update persistence rather than assuming they are unchanged.

## Dependency and risk matrix

| Dependency | Why a higher target matters | Required proof | Priority |
| --- | --- | --- | --- |
| Boot receiver -> `AutoService` | Android 8+ background-service start restrictions apply to apps targeting 26+; the current `startService` can be rejected after boot | Actual cold boot starts one healthy daemon without user opening the app; capture log and timing | **Blocker at 26** |
| Foreground service replacement | A correct replacement needs notification/channel, timely `startForeground` and an appropriate lifecycle; later SDK levels add permission/type/start restrictions | No ANR, crash, stuck notification, surprise restart, or regression on boot/wake | **Blocker** |
| Hidden `ServiceManager` -> `PServerBinder` | Non-SDK interface restrictions vary by API, target and vendor implementation; source inspection alone cannot determine whether reflection will succeed | On-device binder lookup plus daemon launch and authenticated socket handshake at each candidate SDK | **Blocker; evidence required** |
| App update permission | Target 26+ changes the per-app unknown-source authorization flow; current bypass must be replaced, not simply removed | Install-permission request, denial, retry, confirmed install and session status callback | **Blocker** |
| Package replacement and existing root daemon | The package can be replaced while a privileged process from the old code is still alive | Version/UID checks, allowlisted daemon handoff, socket ownership and recovery | **Blocker** |
| Android 12+ and 14+ behaviors | Foreground-service launches/types, background activity launches, notification permission, `PendingIntent` and broadcasts become more restrictive depending on target | Per-API tests; do not infer safety at API 31/34 from the current API 25 run | Later stages |
| Device-protected boot opt-in | Must survive update but not uninstall; failure must leave the boot hook inert/fail-safe | Opt-in token, UID and filesystem checks through update/reboot/uninstall | High |

## Proposed experimental sequence

1. **Baseline at target 25:** capture actual Android/firmware build, `BOOT_COMPLETED` timestamp, `AutoService` lifecycle, Binder lookup result, daemon protocol/UID, top/bottom CRTC, updater permission state and signed installer result. Store raw captures privately; publish only sanitized observations.
2. **Isolated target-26 branch:** change only the necessary boot-service/notification path, updater permission handling, manifest target and any required declarations. Do not change display actions, polling, CPU property, early boot hook, or release code.
3. **Host verification:** build, model tests, manifest/component inspection, static checks, and explicit failure paths for denied install permission and foreground-service failures. A host-green build does not prove Android behavior.
4. **Supervised Android 13 hardware gate:** boot with app UI closed, open from dashboard, daemon absent/present/unhealthy, direct Binder availability, screen/wake, service teardown, user-denied and user-approved updater flows, install over the existing signed app, reboot, and rollback plan.
5. **Only after target 26 passes:** investigate 28/29 and then 31/33/34+ separately, including hidden-API changes and modern foreground-service rules. Avoid a direct 25-to-current-target jump.

## Go/no-go and rollback

- **Stop immediately** on missing `PServerBinder`, `IllegalStateException`/`ForegroundServiceStartNotAllowedException`, foreground-service timeout, inconsistent app/daemon identity, unexplained panel state, unexpected compositor restart, unsuccessful package replacement, or installer confirmation failing to return.
- Do not work around binder restrictions by disabling global hidden-API enforcement, rewriting vendor services, or modifying firmware in this experiment.
- Use a dedicated, versioned, release-signed **test** APK so the Android updater can replace the existing package. Document the fact that an increased `versionCode` cannot simply be downgraded normally; preserve a data-safe rollback/recovery plan **before** installing.
- Keep `main`, stable releases and the existing target-25 APK unchanged until hardware evidence and explicit maintainer approval. Host tests alone are insufficient.

## Evidence status

**Confirmed from source:** boot `startService`, private bridge reflection, target-sensitive updater permission branch and already-present exported/mutable fields.

**Not yet demonstrated:** target-26+ boot survival, foreground-service design, Binder access under a raised target, post-install handoff and end-to-end updater behavior. Do not mark these as passing based on this audit.

See [Architecture](ARCHITECTURE.md), [Compatibility](COMPATIBILITY.md) and [Failure validation matrix](FAILURE-VALIDATION-MATRIX.md).
