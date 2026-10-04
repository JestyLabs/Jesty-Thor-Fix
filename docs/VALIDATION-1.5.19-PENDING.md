# v1.5.18 + v1.5.19 combined physical candidate

**Status (2026-10-04): failed supervised in-place migration; stopped.** The 1.5.18 safety/updater patch and the
1.5.19 structural refactor were integrated as separate commits. Both host
test scripts pass on the combined tree. The v1.5.19 APK builds, aligns and
was signed with the established certificate. The 1.5.19 installation was
attempted once in BOTH and rolled back to 1.5.17 without clearing data or
rebooting. No 1.5.19 cold boot or feature validation was done. v1.5.16 remains
Latest.

## Supervised attempt and safe rollback — 2026-10-04

- Before installation: Android 13, v1.5.17/versionCode 66, battery 88%, AYN
  mode BOTH (`0`), both CRTCs 181/243 active, CPU property `1`, one root daemon
  PID 8608, composer PID 7438 and boot ID
  `599c48a0-4d31-4d90-91b0-287eb5c8577d`.
- The exact signed 1.5.19 APK below passed SHA-256, v1/v2/v3 signature and
  alignment checks. `adb install -r` succeeded. VersionCode 68 was visible;
  boot ID, compositor PID, CPU property and both CRTCs were unchanged.
- Opening the app started `AutoService` at 18:38:41 local. It identified and
  stopped the old authenticated daemon at 18:39:11. The root bridge reported
  new-daemon submissions at 18:39:11 and 18:39:41, but no `app_process / D`
  successor or `READY` appeared. At 18:40:11 the service logged `trusted
  daemon did not pass health check`. The 1.5.19 migration therefore failed;
  further mode, Wake Guard and boot tests were stopped.
- The owner confirmed normal images on both screens. The root daemon log was
  mode 0600 and could not be read through unprivileged ADB. The full logcat and
  read-only before/after collectors were saved under `C:\Temp` outside Git.
- The signed 1.5.17 APK with the same certificate was installed with
  `adb install -r -d`, without data clearing. After opening the app, it started
  one root daemon PID 26583 and logged `READY 1.5.17` and `trusted daemon
  healthy` at 18:44:06. The boot ID and compositor PID were still unchanged,
  CPU property was `1`, both CRTCs were active, and the owner confirmed BOTH
  visually normal. There was no cold boot.

The root cause remains unproved. One concrete difference is that the bridge
launch command grew from roughly 239 to 359 characters when the root-log guard
was added. The vendor bridge acknowledges submission but does not expose the
shell's exit status. A command-size limit or truncation is a hypothesis;
v1.5.20 shortens and tests the command before another supervised attempt.

The remaining numbered test sequence below is **deferred**. It is not evidence
that 1.5.19 passed any of those checks.

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

## Review boundary

The GitHub `work/thor-v1.5.19-combined-candidate` branch was published as one
snapshot commit because this Windows host's Git HTTPS helper crashes during
push. The local integration is split into `6d737b9` (1.5.18 safety/updater),
`755d2bc` (1.5.19 refactor) and `354dbf7` (signed candidate/validation), with
earlier investigation commits. The remote tree was compared with the exact
local tree, but GitHub's single snapshot is not a useful bisect boundary.
Keep the local split history and use those commits when attributing a defect.
Do not interpret the single remote commit as a single small code change.

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

## Checks to combine with that one session

- Before installation, run the two host suites, recheck the APK hash and
  certificate above, and save the installed version, preferences, daemon PID,
  socket, composer PID, boot ID, CPU property, mode and CRTCs. This is a
  baseline, not an additional boot.
- After installation in BOTH, confirm the daemon handover once, unchanged boot
  ID and composer PID, no second daemon, and the same effective CPU Fix and
  Wake Guard preferences. If a handover fails, preserve the trace and stop;
  do not retry by toggling CPU Fix.
- Check updater presentation without starting an install: the stable-release
  check should offer nothing newer than this 1.5.19 candidate. A manual
  no-network check may be observed while awake if it does not interrupt the
  device session. Download cancellation and Android install confirmation
  require a genuinely newer, signed release; record them as unavailable now.
- For the supervised TOP boot, use the same collector for both visual phases.
  Report the first visual end, Jesty receiver/service/daemon start, gate and
  grace, compositor restart, second visual end, and BOOT READY separately.
  The primary user-facing latency is the **second visual end**, not READY.
  Pair `cat /proc/uptime` with `date +%s.%N` and prefer event payload uptime
  over a shifted logcat monotonic column in the first phase.
- The helper-abort recovery path is exercised by host models only. Do not
  force a compositor/helper failure on the Thor to manufacture coverage.

Stop and preserve evidence on HELPER_ABORT, late GATE_RESET, a new
SurfaceFlinger abort, green flash, wrong panel, another kernel boot, repeated
composer restart or incomplete recovery. The previously documented early EGL
signature remains a known exception for diagnostic continuation only; it is
still an abort and does not satisfy a zero-abort stable criterion.

The pre-composer property idea is a **separate** experiment. Its read-only
feasibility preflight can share the awake session. Do not combine an unproven
boot hook with the first 1.5.19 cold boot: otherwise a failed boot cannot be
attributed to the app integration or the hook.
