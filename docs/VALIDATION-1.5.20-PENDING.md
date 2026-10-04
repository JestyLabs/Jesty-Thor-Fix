# v1.5.20 daemon launch repair candidate

**Status (2026-10-05): signed in-place handover, BOTH/TOP and TOP wake passed;
cold boot and remaining feature checks pending.** The Thor finished in visually
confirmed BOTH with v1.5.20 installed. Do not treat this as a boot validation.

## Reason for this candidate

The supervised 1.5.19 installation stopped the authenticated 1.5.17 daemon,
then submitted two new-daemon launch commands through the vendor bridge.
Neither produced a successor. No compositor or kernel restart occurred, the
two CRTCs stayed active, and a data-preserving reinstall of 1.5.17 restored a
healthy daemon. Details and evidence pointers are in
[the 1.5.19 diary](VALIDATION-1.5.19-PENDING.md).

The 1.5.19 bridge command was around 359 characters, compared with around 239
in 1.5.17. The bridge may truncate or reject long commands, but that has not
been proved. v1.5.20 keeps the log-file link/owner guard, compresses the four
boot timing fields into one validated `JT` environment value, and rejects any
generated launch command longer than 255 characters before calling the
bridge. A host test checks the worst case at 255 and round-trips the trace
fields. No display, boot gate, CPU property or Wake Guard behavior changed.

## Host evidence and gates

- `scripts/test-boot-lid.ps1`: passed, including `DaemonLaunchScriptTest`.
- `scripts/test-dashboard.ps1`: passed.
- `build.ps1 -Sign`: compiled and zipaligned versionCode 69 / 1.5.20.
  `apksigner` verified v1/v2/v3 with the established certificate SHA-256
  `727D4850779BED1E51018108E13BC399D4DA38CFC68F4F7504120AD5E2DAD6FC`.
  Exact signed APK SHA-256:
  `AC4EC453863935F8ECA674CE5F6F2B0418C1AEFEC081987B247AE7E6A10C79C6`.
  The APK remains outside Git and has not been installed.
- The launch command was reviewed for fixed arguments, bounded length, safe
  log fallback and a single `pm path` result on this Thor. The root daemon
  started successfully, which exercises the shell on-device. Prepublication
  scan passed for the staged source and diary updates.

## Supervised in-place result (2026-10-05)

- Preflight: signed v1.5.17/versionCode 66, CPU property `1`, battery 91%,
  BOTH with CRTC 181=`1` and 243=`1`, daemon PID 26583, compositor PID 7435,
  boot ID `599c48a0-4d31-4d90-91b0-287eb5c8577d`.
- The exact signed APK above installed with `adb install -r`; app data was
  preserved. Opening the app triggered one migration. AutoService stopped the
  identified v1.5.17 daemon after its 30 s same-boot socket wait and submitted
  one launch. New root daemon PID 24058 logged `READY 1.5.20` at 00:17:58 and
  `BOOT READY mode=0 bottom=1` at 00:18:09. There was no second daemon.
- The new trace records `DAEMON_MAIN`, `LISTEN_OK`, three stable samples at
  500 ms, a 10 s grace, `RECONCILE_DISPLAY`, and `BOOT_READY` with both CRTCs
  active. The compact `JT` metadata decoded to receiver_ms=-1,
  service_ms=70994920, socket=NOT_CHECKED and launch_wait_ms=30149.
- The owner saw normal image on both screens with no flash. A physical AYN
  change to TOP produced CRTC 181=`1`, 243=`0` and a physically dark lower
  panel. One short TOP sleep/wake preserved that state; logcat recorded
  `WAKE_REPAIR OFF`. Returning to BOTH produced both CRTCs active and normal
  image on both screens. CPU property stayed `1`; daemon, compositor and boot
  IDs stayed unchanged. No new fatal/abort line appeared in the installation
  window. Wake Guard remained OFF and was not tested in this round.
- Sanitized local evidence is outside Git at
  `C:\Temp\jesty-thor-1517-evidence\20261005-001703-v1520-preinstall`.
  This was an in-place check: no kernel reboot or compositor restart occurred.

The failed v1.5.19 launch and successful shorter v1.5.20 launch support the
command-length hypothesis, but do not prove the bridge's exact limit.

## Remaining supervised device checks

1. Confirm the saved controls, CPU Fix presentation and updater behavior
   without changing the CPU Fix toggle or forcing a compositor restart.
2. With the owner observing, perform at most one supervised TOP cold boot with
   both visual phases and the boot trace. Preserve the signed v1.5.17 APK as a
   data-preserving rollback option. Stop on an unexpected panel, abort or loop.
3. Wake Guard and updater installation still need their own physical checks
   before a stable release; neither was exercised by this handover.

Successful handover alone does not validate the 1.5.18/1.5.19 helper recovery,
updater, closed-lid path or second visual boot latency.
