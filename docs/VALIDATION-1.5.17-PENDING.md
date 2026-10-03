# v1.5.17 validation diary

**Status:** pre-release; physical validation pending. v1.5.16 remains Latest.

## Completed on the host

- Reviewed the supplied patch against the v1.5.16 main tree. Corrected the
  artifact name, one-pass CRTC parser, and diagnostic probe cadence.
- `scripts/test-boot-lid.ps1` and `scripts/test-dashboard.ps1` passed. The
  signed Android build passed APK alignment and v1/v2/v3 signature checks.
- The published v1.5.17 asset was downloaded and matched the local 9,151,581
  byte APK and SHA-256 recorded in [release integrity](RELEASE-INTEGRITY.md).
- The exact v1.5.17 APK has **not** been installed or boot-tested on the Thor.

## Preparation before touching the Thor

1. Review the final v1.5.17 diff against
   [the v1.5.16 review](CODE-REVIEW-1.5.16.md), including the open daemon,
   watcher, callback, socket, display-ID, migration, and telemetry findings.
2. Repeat both host test scripts, build and sign, and verify package version,
   certificate, alignment and signatures. Install only the exact published
   v1.5.17 asset, SHA-256
   `6E9BE2F4076FAB75753CD742D64D5027CFBF8C07275EF61119E67CFE93EDC550`.
   A rebuild with a different hash is diagnostic, not a replacement asset.
3. Confirm the local signed v1.5.16 recovery APK and its published SHA-256
   `4C697E4AD43D689BAF14ECD8184F827AC1E3E86BFD4CA198B18334104B6050F5`.
   Preserve app data. An older APK may be refused as a downgrade.

## Supervised Thor session

Use `adb devices -l` to identify the Thor serial. The collector performs only
device reads and writes evidence to `C:\Temp\jesty-thor-1517-evidence`, outside
Git. Run it immediately after each boot, before the trace can rotate:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/collect-thor-boot.ps1 -Serial <THOR_SERIAL> -Label baseline-1516 -InspectInit
```

1. With the owner present, record the installed version, physical BOTH state,
   CRTCs, `boot_id`, daemon/compositor PIDs, CPU property, Wake Guard and saved
   preferences. Stop if the baseline is unexpected. Do **one supervised cold
   boot on v1.5.16 in BOTH** and collect it with the same script. Record the
   charger, brightness, workload, CPU Fix preference and visual finish time.
2. Install v1.5.17 in place in BOTH without clearing data or rebooting. Check
   package version, saved preferences, one daemon, unchanged kernel/compositor
   PIDs and correct CRTCs. Stop before boot testing on a mismatch.
3. Do at most **three supervised v1.5.17 cold boots in BOTH**, reviewing each
   before the next, then **one in TOP** after confirming the physical switch
   and expected CRTC state. Keep the same CPU Fix setting and comparable
   conditions. Do not add boots to improve a timing average.
4. For each boot, save trace and `.1`, events, filtered main/system and crash
   logcat, tombstone list,
   `ro.boottime.*`, broadcast history, `query-receivers`, boot ID, PIDs, CPU
   property, mode and CRTCs. The collector's `-InspectInit` switch also reads
   composer init rules, possible `system_load` writers and Magisk/KernelSU
   presence. Store raw evidence outside Git and sanitize before sharing.

The **primary timing metric** is the end of the *second visual Android boot*:
correlate the second `boot_progress_enable_screen`, `sf_stop_bootanim` or
`ANDROID_BOOTANIM_EXIT` with the owner's observation. Report `BOOT_READY`
separately. Compare baseline and v1.5.17 per boot; do not claim a controlled
speedup if the finishing signal is missing or conditions differ.

Check approximately 500 ms gate sample spacing, a 10-second first grace,
`APPLY_CPU_FIX` to `CPU_PROP_WRITTEN` (hypothesis: under 0.1 s), and the restart
request relative to v1.5.16 (hypothesis: about 1.9 s earlier). These are
predictions, **not pass thresholds**. Confirm whether `sys.boot_completed`
stays `1` during the second visual phase while `service.bootanim.exit` returns
to `0`; record `pidof system_server`, package/settings `service check` output,
HELPER service marks and `trusted daemon healthy` after the second
`BOOT_COMPLETED`. These determine whether a real post-restart gate is viable.

Stop at any current-boot `HELPER_ABORT`, `GATE_RESET` after grace begins,
SurfaceFlinger abort, green flash, artifact, unexpected panel state, extra
kernel boot or incomplete recovery. Preserve evidence first. The v1.4.0 diary
recorded one early SurfaceFlinger abort before the app acted and another
during a candidate compositor restart; neither should be silently conflated
with a new v1.5.17 event.

If recovery is needed, try only a downgrade path that preserves app data and
is safe to attempt under supervision. v1.5.16 cannot replace a running
v1.5.17 daemon through its allowlist, so a **supervised cold boot is required
after a successful downgrade**. If Android refuses the downgrade, stop and
prepare a signed forward correction; do not clear data by default.

## Additional read-only questions

- Use `cmd package query-receivers --brief -a
  android.intent.action.BOOT_COMPLETED` and broadcast history to identify AYN
  handlers before considering a shorter initial grace.
- Compare the composer's init class/trigger and `ro.boottime` with
  `post-fs-data`; check vendor scripts for property overwrites and read-only
  Magisk/KernelSU presence. Do not install a boot script in this phase.
- Capture `uname -r`. Only claim that DRM debugfs reads block modesets if the
  matching Thor kernel source or measured trace supports it.
- After READY, take `-ResourceSnapshot` captures with screen on at idle, in a
  naturally awake screen-off state, and in a game. It records per-thread
  `top -H`, processes and memory. Do not hold the device awake solely for this
  comparison. A True Bottom Screen Off ON/OFF frame-time A/B using
  SurfaceFlinger `--timestats` is separate and requires explicit approval;
  restore the starting state afterward.

## Results

| Check | Result |
| --- | --- |
| v1.5.16 comparable BOTH baseline | Pending |
| v1.5.17 in-place upgrade and daemon migration | Pending |
| Three v1.5.17 BOTH cold boots | Pending |
| One v1.5.17 TOP cold boot | Pending |
| Visible finish and GATE/HELPER/ANDROID correlation | Pending |
| Current-boot aborts, physical CRTCs, CPU property | Pending |
| Runtime resource measurements and D read-only feasibility | Pending |

Promotion requires the in-place upgrade, at least three BOTH boots, one TOP
boot, correct final hardware state and zero aborts. A missing signal is a
documented limit, not evidence that a future gate is safe.
