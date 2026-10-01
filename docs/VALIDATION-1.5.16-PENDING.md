# v1.5.16 validation diary

## Completed on the host

- Source review and one safety correction to the supplied patch: helper
  replacement aborts if the live old PID's root UID or daemon command line
  cannot be verified, or if the process does not exit. An absent or zombie
  old PID is allowed; an unreadable `/proc` state aborts.
- Added a real `BootReceiver` timestamp distinct from `AutoService` start.
- `scripts/test-boot-lid.ps1` and `scripts/test-dashboard.ps1` passed after
  the final code changes; the signed build, APK alignment and v1/v2/v3
  signature checks passed.
- A Thor was connected after preparation; the exact rebuilt and signed APK
  was installed and observed as recorded below.

## Observed on the Thor (one in-place install, one cold boot)

- Before install: v1.5.15/versionCode 64, AYN BOTH (mode 0), CRTC 181=1 and
  243=1, CPU property `vendor.display.disable_system_load_check=1`, one root
  daemon, and kernel boot ID `85266414…`. The owner confirmed normal image on
  both screens. No data was cleared.
- The exact v1.5.16/versionCode 65 signed APK was installed in place once.
  Opening the app in BOTH triggered the narrow previous-version migration:
  one old daemon was stopped after the guarded wait, one new root daemon
  started, and no compositor or kernel restart occurred. The new daemon
  reached `BOOT_READY` in BOTH with both CRTCs active and CPU property `1`.
  The owner reported no visual anomaly.
- Fractional `sleep 0.1` and `sleep 0.25` both completed on the Thor shell.
  Direct app-context `/proc/.../boot_id` access was not testable through
  `run-as` because the production package is not debuggable, but the actual
  launcher classified `STALE_PREVIOUS_BOOT` using its own boot ID read.
- One supervised true power-off and physical power-on changed the kernel boot
  ID to `b0953070…`. `BootReceiver` recorded 35.122 s; service start was
  35.129 s. The old socket was classified `STALE_PREVIOUS_BOOT`, with a
  100 ms launch wait; `DAEMON_MAIN` was 35.364 s. The first gate's 10-second
  grace ran normally. The CPU property was written and verified as `1`, and
  one compositor restart was requested at 48.721 s. The helper observed new
  compositor, SurfaceFlinger and zygote PIDs; it verified the old daemon,
  waited for exit and launched one successor. The second gate's five-second
  grace also completed. `BOOT_READY` was at **65.479 s** and wake unlock at
  65.586 s, with CRTC 181=1, CRTC 243=1, mode 0 and CPU property `1`.
- The owner saw one initial kernel boot and a second visual Android UI boot,
  with no green flash or artifact reported. The same kernel boot ID remained
  throughout, with one post-compositor daemon and no observed restart loop.
  The owner confirmed both screens normal after the second visual phase and
  `CPU FIX ACTIVE` in the Jesty app.
- The filtered trace and logcat were saved locally outside Git. The earlier
  v1.5.15 boot reached READY at about 95.417 s; this run was about 29.9 s
  faster, consistent with removal of the old 30-second socket wait. These
  are separate boots and builds, not a controlled benchmark or repeatability
  proof.

## Physical plan used (one cold boot)

1. With the owner present, collect read-only baseline: installed version,
   AYN mode, both CRTC states, kernel boot ID, daemon/compositor PIDs, private
   socket and lock, CPU Fix property, boot trace and relevant logcat/tombstone
   status. Confirm BOTH shows image on both displays and preserve app data.
   If feasible, check fractional shell sleeps and whether the app context can
   read `/proc/sys/kernel/random/boot_id`. Do not alter the CPU property just
   for this test.
2. Install **only the exact signed v1.5.16 APK** over v1.5.15, supervised in
   BOTH, without reboot or data clearing. Confirm package version, certificate,
   one daemon, authenticated socket, both CRTCs and saved settings. Stop on
   display change, unexpected compositor restart, duplicate daemon or error.
3. If step 2 is healthy, perform **one** supervised cold boot. Observe the
   panels throughout. CPU Fix ON may cause one Android UI/compositor restart;
   it must not cause a second kernel boot or a restart loop. Stop at the first
   green flash, artifact, unexpected panel state, new abort or incomplete
   recovery. Confirm BOTH, CPU Fix effective state and READY at the end.
4. Save sanitized local trace and `adb logcat -b all -v monotonic -d` outside
   Git. Compare receiver, service, socket classification, daemon start,
   composer restart and READY timestamps. Check PID changes and kernel boot ID
   to distinguish UI restart from a kernel reboot. The ~65-second estimate is
   tested here, not presumed.
5. A second cold boot is reserved **only** for a specific correction that
   changes the APK and requires repeating the proof. Obtain fresh supervision
   for that boot. Do not run a broad boot matrix to chase timing variation.

No dock or BOTTOM ONLY physical test is in this phase. Do not clear data,
factory reset, install an unverified artifact, or assume a downgrade will be
accepted as recovery. Keep v1.5.15 stable until the observed results support
a separate promotion decision. Record any failure here before retrying.

## Results to fill after Thor observation

| Check | Result |
|---|---|
| Baseline and recovery route | v1.5.15 baseline and data preserved; no recovery action required |
| In-place install, one daemon and both CRTCs | Passed in BOTH |
| Single supervised cold boot | Passed; READY 65.479 s |
| Flash/artifacts/restart loop | None reported/observed in this run |
| Boot timing and trace correlation | Stale socket recognized; 100 ms launch wait; one composer restart |
| Fractional shell sleeps and boot ID access | Fractional sleeps passed; app launch classified prior boot; direct app-context read unavailable |
