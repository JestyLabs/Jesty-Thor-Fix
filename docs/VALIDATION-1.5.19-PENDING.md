# v1.5.18 + v1.5.19 combined physical candidate

**Status (2026-10-04): host-only.** The 1.5.18 safety/updater patch and the
1.5.19 structural refactor were integrated as separate commits. Both host
test scripts pass on the combined tree. The v1.5.19 APK builds, aligns and
was signed with the established certificate. No installation, runtime update
or cold boot was done.
The Thor still has v1.5.17 installed as a pre-release. v1.5.16 remains Latest.

Exact candidate kept outside Git:
`dist/Jesty-Thor-Fix-1.5.19.apk`, package `com.thor.displaypowertest`,
versionCode 68 / versionName 1.5.19, SHA-256
`87E96B123B049D32E0A2F4B7ED02A55EE7597BF2E187A05396D36346F2B6514F`.
`apksigner` verified v1/v2/v3 and one signer with certificate SHA-256
`727D4850779BED1E51018108E13BC399D4DA38CFC68F4F7504120AD5E2DAD6FC`.
Recheck the on-disk hash immediately before installation.

The one conflicting 1.5.18 test-script hunk was applied manually without
changing its assertions. The standalone in-app-updates patch was already
included in 1.5.18 and was not applied a second time.

## Before touching the Thor

1. Review the combined diff, especially daemon identity/migration, IPC,
   helper exit recovery, Wake Guard, app lifecycle and updater install gate.
   Preserve the separate 1.5.18 and 1.5.19 commits so a defect can be
   attributed without another boot.
2. Re-run `scripts/test-boot-lid.ps1` and `scripts/test-dashboard.ps1` if the
   source changes. Recheck the exact signed 1.5.19 APK hash, manifest,
   signature and alignment immediately before installation. Keep APK and keys
   out of Git. The staged-source prepublication scan passed before commit.
3. Confirm battery, awake BOTH physical image and both CRTCs, installed
   v1.5.17, boot ID, daemon/compositor PIDs, CPU property and preferences.
   Save the read-only collector output outside Git. Do not clear app data.

## Minimum physical sequence

1. Install v1.5.19 **once in BOTH**, without cold boot. The allowlist accepts
   the installed 1.5.17 daemon. Verify the new daemon identity, one root
   daemon/socket, saved toggles, CPU Fix effective state, and both CRTCs.
   Opening the dashboard must not restart the compositor. Observe updater
   layout and whether normal stable-release checking stays quiet when the
   installed version is newer than Latest.
2. While awake, use the physical AYN control for BOTH -> TOP -> BOTH, check
   lower CRTC true-off and wake repair, and leave the device in the confirmed
   intended mode. Test the available Hall/Wake Guard path only with the owner
   observing, then return the guard to its starting setting. Do not cycle
   CPU Fix OFF/ON merely to repeat historical 26-28-second UI restarts.
3. Use **one supervised cold boot in TOP first** if the owner is available.
   TOP exercises the higher-risk boot/display reconciliation path. Collect
   both visual phases, trace, first-phase broadcasts, crash buffer, mode,
   CRTCs, CPU property, single daemon and final UI. If this passes, switch
   back to BOTH while awake and validate physical image/CRTCs. A separate
   BOTH cold boot is deferred until it answers a release decision or a
   specific discrepancy; do not do one for a timing average.
4. A real updater installation needs a later release with a **higher**
   verifiable version and the same signing certificate. Host tests, archive
   verification and dashboard display can be checked now, but do not claim
   the end-to-end updater install passed without that release and Android's
   user confirmation.

Stop and preserve evidence on HELPER_ABORT, late GATE_RESET, a new
SurfaceFlinger abort, green flash, wrong panel, another kernel boot, repeated
composer restart or incomplete recovery. The previously documented early EGL
signature remains a known exception for diagnostic continuation only; it is
still an abort and does not satisfy a zero-abort stable criterion.

The pre-composer property idea is a **separate** experiment. Its read-only
feasibility preflight can share the awake session. Do not combine an unproven
boot hook with the first 1.5.19 cold boot: otherwise a failed boot cannot be
attributed to the app integration or the hook.
