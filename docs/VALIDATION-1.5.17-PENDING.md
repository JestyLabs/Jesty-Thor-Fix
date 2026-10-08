# v1.5.17 validation diary

**Status:** pre-release; physical validation resumed for one supervised,
read-only graphics investigation boot on 2026-10-04. v1.5.16 remains Latest
stable.

Later safety, timing and resource decisions are tracked in the
[post-v1.5.17 roadmap](ROADMAP-AFTER-1.5.17.md).
The saved crash evidence and AOSP code-path analysis are in the
[early SurfaceFlinger investigation](SURFACEFLINGER-EARLY-BOOT-INVESTIGATION.md).

## Completed on the host

- Reviewed the supplied patch against the v1.5.16 main tree. Corrected the
  artifact name, one-pass CRTC parser, and diagnostic probe cadence.
- `scripts/test-boot-lid.ps1` and `scripts/test-dashboard.ps1` passed. The
  signed Android build passed APK alignment and v1/v2/v3 signature checks.
- The published v1.5.17 asset was downloaded and matched the local 9,151,581
  byte APK and SHA-256 recorded in [release integrity](RELEASE-INTEGRITY.md).
- The exact v1.5.17 APK was subsequently installed in place and boot-tested
  once in BOTH and once in TOP; see the dated results below. This host section
  records what was completed before that supervised session.

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

Before powering off for each planned cold boot, start the read-only early
capture on the PC with the current boot ID. It waits for a *different* kernel
boot ID, then saves events and startup snapshots every 15 seconds for 105
seconds. It does not request a restart or modify the Thor:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/watch-thor-boot.ps1 -Serial <THOR_SERIAL> -PreviousBootId <CURRENT_BOOT_ID> -Label baseline-1516
```

Then collect the full post-boot snapshot above. Keep each early folder and
post-boot folder paired by boot ID. If the watcher times out, record the gap;
do not repeat a boot solely to fill it.
Both scripts now save `/proc/uptime` and `date +%s.%N` in one shell call as
`clock-pair.txt`, for later wall-time/tombstone correlation. Preserve the raw
output if the device's `date` does not support nanoseconds. The collector also
saves broadcast history; use it to investigate the first visual finish to
`BOOT_COMPLETED` receiver interval.

1. With the owner present, record the installed version, physical BOTH state,
   CRTCs, `boot_id`, daemon/compositor PIDs, CPU property, Wake Guard and saved
   preferences. Stop if the baseline is unexpected. Use an already completed
   v1.5.16 cold boot as the baseline only if its boot ID, trace, first and second
   visual-finish events and observer notes are all still available. Otherwise
   do **one supervised cold boot on v1.5.16 in BOTH** and collect it with the
   same script. Record charger, brightness, workload, CPU Fix preference and
   visual finish time. A wake from suspend is not a cold-boot baseline.
2. Install v1.5.17 in place in BOTH without clearing data or rebooting. Check
   package version, saved preferences, one daemon, unchanged kernel/compositor
   PIDs and correct CRTCs. Stop before boot testing on a mismatch.
3. Start with **one supervised v1.5.17 cold boot in BOTH**. If it passes, switch
   physically to TOP while awake, check true-off and wake repair without a
   reboot, then do **one supervised cold boot in TOP**. These two boots answer
   the immediate safety and timing questions before any further reboot. The
   other **two BOTH boots** are needed only if v1.5.17 is to meet its written
   stable-promotion criterion of three BOTH plus one TOP; do not silently
   treat the two-boot diagnostic result as that criterion. Review each boot
   before continuing. Keep the same CPU Fix setting and comparable conditions.
   Do not add boots to improve a timing average.
4. For each boot, save trace and `.1`, events, filtered main/system and crash
   logcat, tombstone list,
   `ro.boottime.*`, broadcast history, `query-receivers`, boot ID, PIDs, CPU
   property, mode and CRTCs. The collector's `-InspectInit` switch also reads
   composer init rules, possible `system_load` writers and Magisk/KernelSU
   presence. Store raw evidence outside Git and sanitize before sharing.

Collect each boot promptly after the second visible Android phase: an old boot's
events buffer may already have rolled over. Take one host logcat/events capture
as soon as ADB reconnects if the first phase is at risk of being lost. Take
dashboard BOTH and TOP screenshots during the already planned awake mode checks,
and note the physical state separately: an Android screenshot alone cannot prove
that the lower panel is electrically off. No reboot is needed for screenshots,
the in-place upgrade check, sleep/wake, the AYN mode switch or resource snapshots.
Capture resource snapshots after READY in the same session, without changing the
CPU Fix setting or keeping the device awake artificially.

On the Thor observed on 2026-10-04, ADB shell has no `su`. The collector reads
the DRM state and vendor init files directly; `/data/tombstones` and `/data/adb`
are not readable to shell. Record those as unavailable rather than interpreting
an empty result as evidence of no tombstone or no root manager. The boot ID
`00000000-0000-4000-8000-000000000013` was already about 55 hours old when
the Thor was connected, so that wake is not the v1.5.16 baseline. Its trace
remains a secondary timing reference (`BOOT_READY` 65.479 s), without retained
visible-finish events.

The **primary timing metric** is the end of the *second visual Android boot*:
correlate the second `boot_progress_enable_screen`, `sf_stop_bootanim` or
`ANDROID_BOOTANIM_EXIT` with the owner's observation. Report `BOOT_READY`
separately. Compare baseline and v1.5.17 per boot; do not claim a controlled
speedup if the finishing signal is missing or conditions differ.
For first-phase boot events, prefer the embedded uptimeMillis to the
`logcat -v monotonic` column when they disagree. Cross-check with
`ro.boottime.*` and Jesty `elapsed_ms`; keep converted crash timestamps marked
as approximate until a paired clock sample can validate the conversion.

Check approximately 500 ms gate sample spacing, a 10-second first grace,
`APPLY_CPU_FIX` to `CPU_PROP_WRITTEN` (hypothesis: under 0.1 s), and the restart
request relative to v1.5.16 (hypothesis: about 1.9 s earlier). These are
predictions, **not pass thresholds**. Confirm whether `sys.boot_completed`
stays `1` during the second visual phase while `service.bootanim.exit` returns
to `0`; record `pidof system_server`, package/settings `service check` output,
HELPER service marks and `trusted daemon healthy` after the second
`BOOT_COMPLETED`. These determine whether a real post-restart gate is viable.

Stop at any current-boot `HELPER_ABORT`, `GATE_RESET` after grace begins,
SurfaceFlinger abort outside the exact exception below, green flash, artifact,
unexpected panel state, extra kernel boot or incomplete recovery. Preserve
evidence first. The v1.4.0 diary
recorded one early SurfaceFlinger abort before the app acted and another
during a candidate compositor restart; neither should be silently conflated
with a new v1.5.17 event.

The owner allowed continuation only for the observed early signature:
`/system/bin/surfaceflinger`, `no suitable EGLConfig found, giving up`, a
`SkiaGLRenderEngine::chooseEglConfig` frame with BuildId
`a4e0851419d45662b0fd5cd067b585bf`, before the first `DAEMON_MAIN` for
that kernel boot ID. Any changed signature or later abort is a stop. This
exception does not make a boot abort-free or satisfy the stable criterion.

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

### 2026-10-04 supervised v1.5.16 baseline — stopped before upgrade

The owner observed one cold boot in BOTH with both displays normal and no green
flash or unexpected visual cycle. The kernel boot ID changed once from
`00000000-0000-4000-8000-000000000013` to
`00000000-0000-4000-8000-000000000002`. The existing v1.5.16 APK
(`versionCode 65`) and CPU Fix preference were unchanged. The property finished
at `1`, both physical CRTCs 181/243 were active, and the successor daemon
reached `BOOT_READY` at 59.446 s. The first `sf_stop_bootanim` was 17.899 s
by its embedded uptimeMillis (21.444 s in the displaced logcat column);
the second was 55.249 s, so the visible end is about 55.25 s on this boot.
The intentional composer restart was requested once at 42.940 s. The early
event capture and full post-boot snapshot are stored outside Git under
`C:\Temp\jesty-thor-1517-evidence\20261004-034530-baseline-1516-early` and
`20261004-034645-baseline-1516-postboot`.

The current boot's crash buffer records a SurfaceFlinger `SIGABRT` at column
time 7.965 s (~4.419 s after correcting the first-phase clock displacement),
`no suitable EGLConfig found, giving up`. This predates the Jesty receiver
(29.340 s) and the intentional composer restart (42.940 s); the same early
signature was recorded in the 1.4.0 validation diary. No second SurfaceFlinger
abort appears during the intentional restart. The crash buffer also includes
Google Play services permission exceptions and two Eden emulator application
startup exceptions after the UI restart; these are not attributed to Jesty.
The Thor recovered visually and electronically, but the written stop condition
includes any SurfaceFlinger abort. The session paused before installing
v1.5.17. A decision to exempt this known pre-receiver firmware abort had to be
explicit and still stops on any later or different abort.

The owner authorized continuing with only that narrow exception after receiving
the separate local incident bundle at
`C:\Temp\jesty-thor-1516-sf-abort-20261004.zip` (SHA-256
`9FAF7893969BE0D717A40E281C757DF58F46CDDBD4E7267A49BB4BA1A470D54A`).
The baseline still contains an abort and is not a zero-abort pass.
The raw bundle remains sealed. Its clock-corrected report and refreshed local
file hashes are in
`C:\Temp\jesty-thor-1516-sf-abort-20261004-clock-corrected.zip` (SHA-256
`2C9F75FB877C303B5F081E42D9B5B1DD5FBC8CAABF0B0936810586224D8614C5`).

The first-phase `boot_progress_start`, `boot_progress_pms_start` and
`sf_stop_bootanim` column/embedded pairs differ by +3.546, +3.546 and
+3.545 s respectively; the second-phase `boot_progress_start` pair agrees.
The conversion puts the abort before first `boot_progress_system_run` at
5.594 s. `ro.boottime.surfaceflinger` is 4.095 s. The zygote boot-start
events are 3.965 and 4.790 s, so the new zygote appears ~0.37 s after the
inferred abort; causation is unproven. The first visual finish at 17.899 s
preceded Jesty's receiver by 11.441 s. Receiver to daemon main was 0.298 s
with `socket=STALE_PREVIOUS_BOOT` and `launch_wait_ms=29`. Daemon main to
`APPLY_CPU_FIX` was 12.390 s; composer restart to second visual finish was
12.309 s; second finish to `BOOT_READY` was 4.197 s. Roughly 26 s of the
37.350-second visual-finish interval involve the current CPU Fix gate and
restart. This is a reason to investigate the pre-compositor property option
read-only, not evidence that shortening the grace is safe. The initial
`cpu_fix=?` shows that the property did not persist across this cold boot.

### 2026-10-04 v1.5.17 in-place upgrade — no kernel boot

The exact signed v1.5.17 APK with the hash above was installed with `adb
install -r` over v1.5.16; Android reported `versionCode 66` and
`versionName 1.5.17`. Data was not cleared. Kernel boot ID stayed
`00000000-0000-4000-8000-000000000002`; system_server, SurfaceFlinger and
composer PIDs stayed unchanged. The old root daemon PID 9320 remained until
the newly opened app completed its guarded migration, then one new root daemon
PID 19556 started. Its trace shows stable CRTC samples at 500 ms cadence,
10.0 s grace, `BOOT_READY`, mode BOTH, CRTCs 181/243 active, property `1`,
and no CPU Fix compositor restart during the upgrade. Physical visual check
of the upgraded app is recorded separately from these ADB facts.

### 2026-10-04 v1.5.17 BOTH cold boot #1

The kernel boot ID changed once to
`00000000-0000-4000-8000-000000000017`. The post-boot collector saved
`C:\Temp\jesty-thor-1517-evidence\20261004-035732-both-1517-1-postboot`;
the early events capture is paired by that boot ID. The daemon reached
`BOOT_READY` at 60.924 s with CPU property `1`, AYN mode BOTH and CRTCs
181/243 active. The second `sf_stop_bootanim` was 58.552 s. One intentional
composer restart was requested at 46.242 s; the kernel boot ID did not change
again. No current-boot `HELPER_ABORT`, late `GATE_RESET` or later
SurfaceFlinger abort was found.

The gate's first stable samples were at 34.751, 35.250 and 35.747 s; the
initial grace ran from 35.748 to 45.763 s (10.015 s). `APPLY_CPU_FIX` was
45.776 s and `CPU_PROP_WRITTEN` 45.917 s, a 141 ms interval: the previous
under-100 ms estimate was not met on this boot and was never a pass threshold.
The new package/settings service signals appeared at 50.940/50.960 s, before
the unchanged helper floor at 54.400 s. The successor saw
`ANDROID_BOOTANIM_EXIT=1` at 54.958 s, then completed its five-second grace.

Compared with the v1.5.16 baseline, the receiver ran 5.132 s later in
absolute boot time (34.472 vs 29.340 s). The restart request was 3.302 s
later in absolute time (46.242 vs 42.940 s), but **1.830 s earlier relative
to the receiver** (11.770 vs 13.600 s). The visible finish was 3.303 s later
in absolute time (58.552 vs 55.249 s), but 1.829 s earlier relative to the
receiver (24.080 vs 25.909 s). One boot per version cannot establish a
repeatable absolute speedup. The earlier relative timing supports the intended
command/gate improvement; the variable Android time before the receiver
dominates this pair of absolute results.

The current boot's crash buffer has the same early pre-receiver SurfaceFlinger
`no suitable EGLConfig found` abort, this time at 4.424 s. This is within the
owner's narrow exception; it is still an abort and must not be counted as
zero. The owner reported normal image on both screens, without green flash,
artifact, wrong panel or unexpected visual cycle.

After this boot, the owner used the physical AYN control to switch to TOP,
then performed one short power-button sleep/wake. The lower panel stayed
physically off and the upper panel recovered normally, without green flash.
ADB confirmed mode `1`, CRTC 181 active and CRTC 243 inactive, with the same
kernel boot ID and CPU property `1`.

The post-restart observer marked `ANDROID_BOOTANIM_EXIT=1` at 54.958 s,
**before** `sf_stop_bootanim` at 58.552 s, and ended with
`ANDROID_RECOVERY_TRACE_END reason=TIMEOUT` at 115.160 s because it never
saw a `0` after attaching. This observer does not gate actions; `BOOT_READY`
had already succeeded. It is concrete evidence that `service.bootanim.exit=1`
alone must not be treated as proof that the second visual boot finished.
Do not make that signal a v1.5.18 gate without a stronger condition.

### 2026-10-04 v1.5.17 TOP cold boot #1

The owner observed the top display normal and the lower panel physically off,
without green flash, artifact or unexpected visual cycle. The kernel boot ID
changed once to `00000000-0000-4000-8000-000000000016`. The early and
post-boot evidence is outside Git under
`C:\Temp\jesty-thor-1517-evidence\20261004-040152-top-1517-1-early` and
`20261004-040309-top-1517-1-postboot`. The second `sf_stop_bootanim` was
58.723 s. The successor daemon reached `BOOT_READY` at 61.467 s, mode TOP,
CRTC 181 active, CRTC 243 inactive, CPU property `1`. One intentional
composer restart was requested at 46.150 s; no second kernel boot occurred.
No later SurfaceFlinger abort, `HELPER_ABORT` or late `GATE_RESET` was found.

The initial gate grace ran from 35.655 to 45.680 s (10.025 s), with samples
about 500 ms apart. `APPLY_CPU_FIX` to `CPU_PROP_WRITTEN` took 134 ms.
Package/settings services were found at 51.190/51.210 s, before the 54.680 s
helper floor. The post-restart grace ran from 56.193 to 61.152 s; the lower
CRTC was confirmed off at 61.460 s. `ANDROID_BOOTANIM_EXIT=1` was seen at
55.197 s, again before the 58.723 s visible-finish event.

The crash buffer contains the same early pre-receiver SurfaceFlinger
`no suitable EGLConfig found` abort at 4.480 s. It remains an abort under the
owner's narrow exception, not a zero-abort result. The pre-boot awake TOP
switch and short sleep/wake also passed physically and electronically.

### Session close and exact resumption point

The owner requested a stop after the TOP boot. The Thor was returned to BOTH
with the physical AYN control and the owner confirmed normal image on both
screens. The final read-only snapshot is outside Git at
`C:\Temp\jesty-thor-1517-evidence\20261004-040550-session-final-both`:
v1.5.17/versionCode 66 installed, kernel boot ID
`00000000-0000-4000-8000-000000000016`, Android `Awake`, AYN mode `0`,
CPU property `1`, composer running, CRTCs 181 and 243 active, one root daemon
PID 8888. Its command line carries display/CPU preferences ON and lid guard
OFF. No data was cleared, no APK was downgraded, and no v1.5.18/1.5.19 patch
was installed. The early capture processes completed. Raw evidence stays
outside Git; only this summary belongs in the repository. Nine session
capture folders (238 files) were also copied into
`C:\Temp\jesty-thor-1517-session-20261004.zip`, SHA-256
`09AF828DC0C0395988CE38716E7297F94D2E0E70C447BEAB03252B06F15EE9E8`.
The separate early-abort incident bundle remains available as linked above.

Three supervised cold boots were used in this session: one v1.5.16 BOTH
baseline, one v1.5.17 BOTH and one v1.5.17 TOP. This establishes the requested
diagnostic comparison and physical TOP coverage, not the written stable
promotion threshold of three v1.5.17 BOTH boots plus one TOP and zero aborts.
The same early pre-receiver SurfaceFlinger abort appeared in all three boots;
there was no later SurfaceFlinger abort, green flash or wrong final panel.
v1.5.17 remains a pre-release and v1.5.16 remains the stable release.

### 2026-10-04 v1.5.17 BOTH graphics investigation boot #2

The owner later authorized one more reboot while observing the Thor. Kernel
boot ID changed once from `00000000-0000-4000-8000-000000000016` to
`00000000-0000-4000-8000-000000000008`. The first and second visual
phases finished at 17.743 and 59.106 s (`sf_stop_bootanim` event payloads).
The owner confirmed both phases normal in BOTH, with no flash or panel
anomaly. No APK or app data was changed.

The daemon started at 34.881 s, wrote CPU property `1` at 46.082 s, requested
one compositor restart at 46.407 s, and its successor reached `BOOT_READY`
at 61.499 s with both CRTCs active. The final snapshot had one root daemon,
v1.5.17/versionCode 66 and CPU property `1`. The device subsequently entered
normal sleep, so a later CRTC snapshot is not an awake-panel test.

The unfiltered early main/system/crash capture recorded the same
SurfaceFlinger abort at 4.325 s, before any Jesty process. It exposed a new
precursor: first EGL loaded vendor ANGLE libraries and failed with
`initializeAnglePlatform failed to get valid ANGLE library filename suffix!`;
the restarted zygote later loaded Adreno. The vendor init rules set
`ro.hardware.egl=adreno` only after
`vendor.display.gpu_rendering=true`. The detailed causal analysis and exact
native binary cross-reference are in
[the early SurfaceFlinger investigation](SURFACEFLINGER-EARLY-BOOT-INVESTIGATION.md).
The separate [second visual boot timing analysis](BOOT-RESTART-LATENCY-INVESTIGATION.md)
uses the saved pre-restart log to distinguish Android's broadcast delivery
delay from Jesty's safety gate; no additional reboot was needed.
This known abort still prevents a zero-abort stable-promotion result.

Raw boot capture and post-boot snapshot are outside Git at
`C:\Temp\jesty-thor-1517-evidence\20261004-043420-sf-repro-one-reboot-early`
and `20261004-043813-sf-repro-postboot`, with SHA-256 manifests. The early
collector initially exited while trying to hash its own `SHA256SUMS.txt`;
the individual capture files were intact and their manifest was regenerated
after fixing the script. The post-boot collector's two grep-filtered logcat
files failed because its PowerShell strings lost shell quoting; the full
early events and graphics buffers captured the relevant entries. That
collector quoting is now fixed and was checked with a read-only ADB command.
No further boot was used to repair collection.

Before any later Thor action, recheck installed version, boot ID, mode, CRTCs,
property, daemon count and physical image. First analyze the saved host and
device traces without a reboot. In particular, `service.bootanim.exit=1`
preceded the visible finish and the observer timed out in BOTH; do not promote
it to a post-restart gate. The proposed v1.5.18 recovery/updater and v1.5.19
refactor remain separate, uninstalled candidates. The one additional BOTH
boot would only be relevant if a decision is made to pursue v1.5.17 stable
promotion and the zero-abort blocker is resolved; do not run it merely for a
timing average.

| Check | Result |
| --- | --- |
| v1.5.16 comparable BOTH baseline | Captured; visible finish 55.249 s, READY 59.446 s; known early SurfaceFlinger abort; paused |
| v1.5.17 in-place upgrade and daemon migration | Passed ADB and owner visual checks |
| Three v1.5.17 BOTH cold boots | Two captured; one additional only if stable-promotion criterion is pursued, but the repeated early abort blocks zero-abort promotion |
| One v1.5.17 TOP cold boot | Captured; owner observed TOP normal, lower panel off |
| Visible finish and GATE/HELPER/ANDROID correlation | BOTH and TOP captured; bootanim property turns 1 before visible finish |
| Current-boot aborts, physical CRTCs, CPU property | Known early pre-receiver SF abort in each boot; no later SF abort; awake final CRTCs/property correct |
| Runtime resource measurements and D read-only feasibility | Read-only snapshots and init findings captured; detailed analysis pending |

Promotion requires the in-place upgrade, at least three BOTH boots, one TOP
boot, correct final hardware state and zero aborts. A missing signal is a
documented limit, not evidence that a future gate is safe.
