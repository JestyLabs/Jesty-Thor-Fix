# v1.5.20 daemon launch repair validation

**Status (2026-10-05): accepted for stable release with explicit limits.** The
signed APK passed an in-place handover, BOTH/TOP, TOP wake, one supervised TOP
cold boot and available Wake Guard checks. The Thor finished awake in BOTH,
CPU Fix active and Wake Guard OFF. The owner accepted targeted tests instead
of a fixed boot quota and deferred investigation of the known early firmware
SurfaceFlinger abort.

## Reason for the launch repair

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
  The APK remains outside Git; the exact signed file was installed in place.
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

## Supervised TOP cold boot (2026-10-05)

- Before shutdown: v1.5.20, CPU property `1`, mode TOP, CRTC 181=`1`,
  243=`0`, owner-confirmed image on the superior panel and the lower panel
  dark. The exact signed v1.5.17 APK with published SHA-256 was ready for a
  data-preserving rollback. One `reboot -p` power-off and one physical power-on
  produced boot ID `d33386ae-e35e-4b64-8bf8-63ebfdc1e699`.
- The first daemon started at elapsed 33.656 s with
  `socket=STALE_PREVIOUS_BOOT` and `launch_wait_ms=74`; the old 30 s socket
  wait did not recur. CPU property was initially unknown, written to `1` at
  44.897 s, and verified at 44.909 s. One compositor restart was requested.
  The helper found the new compositor, SurfaceFlinger, zygote and
  system_server, then launched one successor daemon. There was no helper abort,
  daemon duplication or late gate reset.
- The successor observed the second boot animation exit at 54.279 s and
  reached `BOOT_READY` at 60.539 s. In TOP, the lower CRTC remained active
  during the held boot/restart interval; reconciliation confirmed CRTC 181=`1`
  and 243=`0` at 60.532 s. The owner saw the expected two visual phases with
  no green flash, artefact, wrong panel or extra cycle, and confirmed normal
  TOP image at the end. The CPU property was `1` after boot.
- The crash buffer contained the same early SurfaceFlinger signature as the
  earlier 1.5.17 TOP boot: `no suitable EGLConfig found, giving up`, frame
  `chooseEglConfig`, BuildId `a4e0851419d45662b0fd5cd067b585bf`, before
  the app's first daemon. The two Google Play Services exceptions around 29 s
  and 66 s also match that earlier boot. No later SurfaceFlinger abort appeared.
  The known early abort remains a release-quality limitation, even though the
  app action completed and the image was normal.
- Raw read-only evidence is outside Git in
  `C:\Temp\jesty-thor-1517-evidence\20261005-v1520-top-early` and
  `C:\Temp\jesty-thor-1517-evidence\20261005-003323-v1520-top-postboot`.
  The owner confirmed `TOP ONLY · TRUE OFF`, `CPU FIX ACTIVE · CLOCKS NORMAL`
  and the expected toggles in the app. After the boot test, the physical AYN
  control returned the Thor to BOTH with both CRTCs active and normal image.

## Supervised Wake Guard check (2026-10-05)

- The owner enabled the guard temporarily in BOTH. The named `hall_switch`
  reported `SW_LID=0` with the lid open. One normal close/open entered sleep
  and woke normally in BOTH, as observed by the owner.
- For one controlled false wake, the owner held the lid closed. Preflight was
  `SW_LID=1`, `mWakefulness=Asleep`. One ADB `KEYCODE_WAKEUP` produced
  `Awake → Dozing → Asleep` in the following samples while `SW_LID` stayed
  `1`; there was no repeated wake command or loop. The owner opened the lid,
  confirmed BOTH normal and switched the guard OFF again.
- Final ADB state: `SW_LID=0`, `mWakefulness=Awake`, mode BOTH, CRTC 181=`1`,
  243=`1`, CPU property `1`, one daemon PID 8991. Samples are saved outside
  Git as `closed-lid-wake-samples.txt` in the TOP postboot evidence folder.

## Deferred physical checks and known limits

1. By owner decision, the updater's end-to-end install test moves to a later
   phase with BOTTOM ONLY and dock use. Its host checks passed, but cancel,
   installer confirmation, offline/failure and install-readiness paths have
   not been exercised with a genuinely newer release on this Thor.
2. The anti-loop Wake Guard limit was not retested on v1.5.20; it passed on
   v1.5.17. The external-display/dock case remains deferred by product choice.
3. The known early firmware SurfaceFlinger abort still needs a separate
   release decision. Additional boots may improve confidence, but the owner
   observed no green flash or wrong panel in this v1.5.20 run.

These results exercise the normal helper handoff, closed-lid path and second
visual boot on hardware. The adversarial helper recovery and updater install
paths remain host-tested only.
