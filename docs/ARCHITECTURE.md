# Architecture

Jesty Thor Fix is split between a normal Android dashboard and a privileged root-side daemon.

## Android side

- `MainActivity` owns the dashboard lifecycle and wires its parts (from
  v1.5.19): `DashboardLayout` builds the views once, `DashboardSettingsController`
  owns the three persistent switches and their daemon commands,
  `TelemetryPoller` polls telemetry once per second only while visible,
  `DashboardRenderer` applies `DashboardStateModel`/`CpuWarningModel` results
  to the views, `BackgroundMediaController` owns the background art and video,
  and `UpdateReadiness` keeps the evidence for the updater's install decision.
  The dashboard host test rejects daemon requests or view construction in
  `MainActivity`.
- `AutoService` starts or reconnects to the daemon, migrates the old protocol
  once, and reconciles saved settings after a normal boot. From v1.5.16 it
  classifies an unreachable socket pathname before waiting (see *Daemon launch
  and stale sockets*).
- `BootReceiver` starts that service after a normal boot.
- `AppUpdater` checks GitHub releases only while the dashboard is open (at most
  hourly), shows the top-bar UPDATE button, and downloads, verifies and hands
  a newer APK to `PackageInstaller`; `UpdateInstallReceiver` (not exported)
  receives the installer status. `UpdateVersion` holds the pure version,
  asset, digest, signer and install-readiness rules. The installer is never
  committed while `display_actions_held=1` (a boot hold seen earlier is kept if
  the daemon stops answering), while a switch command is in flight, or without
  a dashboard sample from the current telemetry generation that is under 3.5 s
  old. From the Download tap until commit, the switches are reserved. Android's
  confirmation is opened only from a resumed activity, otherwise on the next
  resume; stale sessions are abandoned. After replacement, the old
  daemon keeps running until `AutoService` replaces it through the existing
  `PreviousSecureDaemonIdentity` gate (BOTH only) or the next boot.
- `SocketClient` sends one-byte commands over a filesystem Unix socket in the
  app's private `files/` directory. It checks the kernel-reported server UID
  before trusting the reply; `ok=1` indicates command handling, not identity.

The dashboard does not need to remain open for the fix to stay active.

### Local diagnostic event history (experimental)

`EventHistoryModel` records only changes to known fields of the already authenticated
`Q` telemetry response: daemon connectivity, a boot safety hold, watcher readiness,
mode and *both* CRTC observations (two consistent samples required), wake counter
increments, repair result and CPU Fix phase. Arbitrary daemon strings, identifiers,
CPU frequencies and free-form error messages are never persisted. A display
observation is **not** physical verification of panel pixels. Collection is
limited to periods while the dashboard is visible; the daemon/watcher do not
perform new work and no IPC command or daemon protocol is changed.

`EventHistoryJournal` keeps at most 64 events in app-private SharedPreferences,
writing only when the state changes, not on each 1 Hz poll. The EVENTS top-bar
button opens a local, read-only chronological report via `EventHistoryDialog`,
with explicit COPY and CLEAR controls. Copying puts only the sanitized history
on the user's clipboard; there is no network submission. Loss of contact resets
observational baselines to avoid claiming a state transition over an unavailable
period. This support history does **not** affect boot safety, updater eligibility,
IPC trust or daemon ownership. See `docs/EVENT-HISTORY-DESIGN.md`.


## Root daemon

`PServer` asks the Thor firmware's `PServerBinder` service to launch `app_process / D` with the installed APK on its classpath. The daemon then:

- watches the Thor dual-screen mode;
- registers for display events;
- submits mode changes, boot reconciliation, user toggles, wake repair, and
  Lid Guard sleep to `DisplayActionCoordinator`;
- uses `DisplayHardware` as the only writer of the lower-display power state
  and the Thor-specific `display.power.state` property;
- exposes status and one-shot verification over the private Unix socket,
  checking the client app's kernel-reported UID before reading commands;
- applies the optional vendor system-load-check property for the AYN Dashboard
  CPU Fix and performs the required one-shot display-compositor restart, which
  restarts Android's framework processes and closes open apps;
- holds a named, timed kernel wake-lock across that transition so the Thor
  cannot suspend behind the black displays, then releases it after recovery;
- schedules a clean daemon relaunch after that display reset so the watcher
  receives fresh Android service binders and true-off remains independent.

The old loopback TCP listener is stopped during in-place migration only after
the legacy daemon's process identity is checked. The migration marker is saved
only after an authenticated health check. There is no TCP fallback. The
v1.5.10 review candidate replaced the installed v1.5.9 daemon in BOTH without
a reboot and verified one root daemon and the private listener afterward. A
different app UID was denied access; a silent app-UID client was closed by the
read timeout. The remaining physical transitions are still pending; host
tests alone cannot establish those results.
The follow-up inode check confirmed the socket's owner, type, 0600 mode and
app-data SELinux label. An isolated test found that this Thor can replace an
occupied filesystem socket pathname on a second bind. From v1.5.11 the daemon
therefore holds a cross-process private file lock before socket cleanup and
bind; physical lock-contention validation remains pending.

## Daemon objects (from v1.5.19)

`D` is only the `app_process` entry point; its class name and default package
are part of the launch and identity checks. `DaemonRuntime` parses `DaemonArgs`
and builds the daemon's objects with explicit constructor dependencies:

- `BootSession`: boot-hold state, phase deadline and the saved CPU and Wake
  Guard intents passed on the command line;
- `BootTrace`: the sanitized trace writer (one lock per daemon);
- `SystemProbe`: bounded `getprop`, `pidof` and `service check` reads;
- `BootCoordinator`: the staged gate and boot reconciliation, plus in-place
  recovery after a handover in which the compositor never restarted;
- `CpuFixController`: the CPU property, the one-shot compositor restart, its
  `TransitionWakeLock`, the relaunch helper and its observer. Its monitor
  replaces the former `D.class` lock;
- `DaemonCommandHandler`: the closed one-byte command set;
- `DaemonIpcServer`: the private socket listener (two workers, four queued
  connections, 1.5 s read timeout, app UID check before reading);
- `AndroidRecoveryTrace`: post-restart observability only.

`BootCoordinator` and `CpuFixController` reference each other through
`CpuFixController.HandoffRecovery`, connected once by `DaemonRuntime`.
`BootSafety`, `DaemonState`, `DisplayActionCoordinator`, `LidGuard`,
`WatcherSupervisor`, `DisplayHardware` and `Telemetry` stay static: the smali
watcher, display callback and wake repair call them directly, and replacing
those calls belongs with any later smali/hidden-API work. The split moved code
without changing commands, replies, trace lines, waits or the helper script.

## Daemon launch and stale sockets

The filesystem socket inode survives power-off, so after a cold boot it exists
before any daemon is listening. Up to v1.5.15 `AutoService` treated the mere
pathname as a possibly live daemon and polled it for 30 seconds (150 x 200 ms)
before launching; that wait preceded the daemon's first boot-trace line.
From v1.5.16 the daemon writes the kernel `boot_id` into its instance lock file
once it holds the lock. `AutoService` sends one authenticated `I` request and
classifies the result with `DaemonLaunchModel`:

| Observation | Action |
|---|---|
| Healthy (`READY`, same version and fix intent, watcher running) | No launch |
| `STARTING`: same version and fix intent, phase in the boot coordinator | No launch; the daemon owns the transition |
| Reachable but neither of the above | Previous 30-second wait, then the guarded replacement path |
| Unreachable, no pathname | Launch now |
| Unreachable, stamped `boot_id` differs from the current one | Launch now (inode from an earlier kernel boot) |
| Unreachable, stamp absent/unreadable or equal to the current boot | Five-second bounded grace, then launch |

`BOOT SAFETY TIMEOUT` is not a starting phase. The instance lock still
arbitrates any launch race; a losing daemon exits before binding. The
migration marker is saved after `HEALTHY` or `STARTING`, both of which are
authenticated, same-version responses. Daemons older than v1.5.16 do not stamp
the lock, so the first boot after upgrading takes the five-second branch.

The post-compositor helper keeps its eight-second floor and service checks. It
now signals the old daemon only after checking its root UID and command line,
waits for that process to exit (up to about five seconds) instead of a fixed
one-second sleep, and does not launch a successor while the old daemon still
holds the lock. Only a daemon that scheduled a compositor restart hands the
timed wake-lock to its successor; every other boot coordinator ending releases
it.

### Failed handovers and stalled phases (from v1.5.18)

Up to v1.5.17 a boot daemon that scheduled the compositor restart stayed in
`APPLYING CPU FIX`, with display actions held, until the helper replaced it.
If the helper aborted, nothing ended that state before the next reboot, and
`AutoService` accepted it as `STARTING`. Two independent paths now close it:

1. **Helper observer.** The daemon keeps the helper's `Process` and waits for
   it on a separate thread. On the normal path the helper stops the daemon
   before exiting, so an exit is only observed when the handover did not
   happen. Each abort reason has its own exit status (10-14, see
   `HandoffRecoveryModel`). The decision depends on whether the framework may
   have restarted underneath the daemon:
   - `COMPOSER_NOT_RESTARTED` (10) with a `RUNNING` watcher proves that the
     old compositor, `system_server` and therefore the daemon's watcher and
     display callback are still current. A boot daemon that is still held
     then runs the post-restart gate itself (five-second grace, fresh
     60-second deadline, no second compositor restart) and reconciles exactly
     as a successor would; a timeout stays held as `BOOT SAFETY TIMEOUT`. A
     runtime toggle only releases the transition wake lock. The CPU phase
     reports `ERROR` and, while the old compositor PID still runs, the
     previous property value is restored, as after a failed `ctl.restart`.
   - Any other exit (11-14, or a shell status) happens after the compositor
     restart, which also restarts SurfaceFlinger, zygote and `system_server`.
     The surviving daemon's watcher still uses the cached activity-manager
     binder and its display callback belongs to the old `system_server`, so
     its mode can no longer be trusted. It sets the held phase
     `HANDOFF FAILED`, releases the wake lock and stops every display and
     Wake Guard action. `AutoService` replaces it immediately the next time it
     runs: the second `BOOT_COMPLETED` after the restart, or the next
     dashboard open, which requests `AutoService` at most once a minute while
     the phase is visible. The same applies to exit 10 when the watcher is no
     longer `RUNNING`.
   - If the helper itself cannot be started, `ctl.restart` is never sent and
     a held boot daemon recovers in place with the same rule.

   Only one helper may run at a time; the dashboard shows CPU `PENDING` until
   it exits. `Q` reports
   `handoff=NONE|SCHEDULED|RECOVERING|RECOVERED|RECOVERY_FAILED|ABORTED|FAILED`
   and `boot_phase_ms`.
2. **Stalled phase.** `I` adds `phase_ms`, the time since the current boot
   phase was entered. `DaemonLaunchModel.replaceablePid` accepts a
   same-version, same-intent daemon in `HANDOFF_FAILED`, or in a `STARTING`
   phase that has not changed for 90 seconds. `AutoService` then stops only
   that PID after the usual root-UID and command-line checks and launches a
   successor in hold, which runs the full boot gate. Every gate phase has its
   own 60-second timeout, so this path is for states without one. A daemon
   older than v1.5.18 sends no `phase_ms` and is never classified as stalled.
   `BOOT SAFETY TIMEOUT` is never replaced by this path.

Neither path changes the gate, its waits or its sample rules. The helper-abort
and stalled-phase decisions have host tests; a helper abort must not be
provoked on a physical Thor. Limits that remain:

- If the compositor never restarted but the property already matches, a
  successor's gate sees the CPU setting as already applied. The helper
  observer covers that case in the same daemon; the stalled-phase replacement
  does not.
- If the CPU property cannot be applied persistently, each successor can
  schedule one more compositor restart, so every dashboard open after a
  failure can restart the interface once. The per-daemon single restart still
  applies; the trace shows each `HANDOFF_FAILED`.
- Exit 10 is decided from the PID observed by the helper. A compositor restart
  that `init` performs only after the helper gave up is not detected until the
  watcher fails.

### Boot trace marks

`/data/local/tmp/jesty-thor-boot-trace.log` uses the boot clock
(`elapsedRealtime`, and `/proc/uptime` in the helper at 10 ms resolution). Each
line carries `pid`, `boot_id` and `source=daemon|helper`.
Besides the existing phase lines, v1.5.16 records `DAEMON_MAIN` (launch type,
the actual `BootReceiver` time, `AutoService.onStartCommand` time, socket
classification, launch wait and process start),
`LISTEN_OK`, `SERVICES_REGISTERED`, edges of each gate input
(`GATE_BOOT_COMPLETED`, `GATE_COMPOSER_RUNNING`, `GATE_MODE_KNOWN`,
`GATE_CRTC_VALID`), `GATE_STABLE_SAMPLE` 1-3, `GATE_RESET`,
`GATE_GRACE_BEGIN`/`END`, the CPU property write/verification, compositor
restart request/acknowledgement, the helper's new compositor, SurfaceFlinger
and zygote PIDs, floor end, service and package checks, old daemon exit and
successor launch, and `WAKE_UNLOCK_SENT` (also written when no lock was
held). These marks only observe; no safety wait was shortened.

From v1.5.17 the helper also records `HELPER_SYSTEM_SERVER_NEW_PID`,
`HELPER_PACKAGE_SERVICE_FOUND` and `HELPER_SETTINGS_SERVICE_FOUND` (service
checks start only after a new `system_server` PID, so the old one cannot
satisfy them), and `HELPER_KILL_RACE_GONE` when the old daemon exited just
before the signal. The successor records `ANDROID_SYSTEM_SERVER`,
`ANDROID_PACKAGE_SERVICE`, `ANDROID_SETTINGS_SERVICE`, `ANDROID_BOOTANIM_EXIT`
edges once per second until the boot animation exits again (at most 60
seconds), and `FIRST_IDENTITY_QUERY`, normally AutoService on Android's second
`BOOT_COMPLETED`. `sys.boot_completed` is not reset by a compositor restart,
so `GATE_BOOT_COMPLETED` in the post-restart phase does not show Android's
second boot; these marks do not gate any action. The trace rotates to
`jesty-thor-boot-trace.log.1` above 256 KiB.

From v1.5.18 the daemon adds `HELPER_EXIT_OBSERVED` (exit, reason, action),
`HANDOFF_RECOVERY_BEGIN` and `HANDOFF_RECOVERED`/`HANDOFF_RECOVERY_FAILED`.
`/data/local/tmp` belongs to the shell user, while these files are written by
root. `RootLogFiles` therefore opens the trace with `O_NOFOLLOW`, appends only
to a regular, single-link, root-owned file, sets mode 0644 on that descriptor
and rotates only such a file. The helper and the daemon launch use an mksh
builtin check (`safe_log`) before each redirection and discard output for a
link or foreign file. A shell check cannot fully close a race with a
concurrent swap; it narrows it and turns a planted link into a skipped line.
The location and world-readable mode are kept so the read-only ADB collector
(`adb shell cat`) keeps working; the files contain no secrets. Moving them to
the app's private directory would require an in-app export path first.

Bounded command waits use `ProcessWait` (5 ms polling). The inherited timed
`Process.waitFor` implementation can poll at 100 ms, and the v1.5.16 trace
showed similar gaps around short commands. The exact Thor timing benefit
remains to be measured on the v1.5.17 build.

## Staged boot and lid guard

The boot receiver launches the daemon in `BOOT HOLD` with its saved choices.
The mode watcher, display callback, socket display commands, and lid sleep
action respect this gate. `BootGateModel` requires Android boot completion,
running compositor, a known AYN display mode, three matching CRTC samples, and
then a 10-second stable grace period. Samples run at a fixed 500 ms cadence;
the sample that can complete the grace is taken when the grace ends, and
READY still requires that fresh sample to match. The CPU property is
reconciled first; if
that restarts the compositor, the relaunched daemon repeats readiness checks
with a five-second grace period. Only then does it reconcile the lower display
idempotently. At 60 seconds without readiness, it remains held and reports
`BOOT SAFETY TIMEOUT` without forcing a display or sleep action.

The optional `LidGuard` reads Linux `EV_SW/SW_LID` events from `hall_switch`
directly. From v1.5.18 `HallNodeModel` keeps that named device first; only if
no input device has that name does it accept the single device whose
`capabilities/sw` reports `SW_LID`. Two named devices, or several `SW_LID`
devices without the name, leave Wake Guard unavailable. The fallback is not
reached on the tested Thor and has no physical validation. Unknown Hall, external display, unknown dock/interactive state, or
an open lid inhibit sleep. It waits 1.5 seconds after closure or 500 ms after
a closed-lid wake and rechecks before `KEYCODE_SLEEP`. After three sleep
attempts in ten seconds it pauses until the lid opens. It does not require
Device Admin, Accessibility, SensorManager, or another foreground service.
The current review covers BOTH and TOP with at most two observed cold boots.
BOTTOM ONLY and physical dock behavior are deferred to future improvements by
user decision; neither is a gate for the current BOTH/TOP review.

## Thor hardware profile

`ThorHardwareProfile` holds every measured Thor identifier: the lower panel's
physical display ID `0x40446d4a32a16584`, its logical display `4`, top/bottom
CRTCs `181`/`243`, the DRM debugfs state path and the `hall_switch` device
name. `DisplayEventCallback.smali` repeats the logical display ID as a literal;
`scripts/test-boot-lid.ps1` fails if that literal or any Java copy of these
identifiers drifts from the profile. A new hardware revision or firmware must
be checked against all of them before any value changes.

## Target SDK 25

`targetSdkVersion` stays at 25 deliberately; it is not merely inherited. Known
dependencies on that level:

- `BootReceiver` starts `AutoService` with `startService` from the
  background. Apps targeting 26 or higher are subject to background-service
  limits, so the boot path would need a foreground service and notification.
- The app side reaches `ServiceManager.getService("PServerBinder")` through
  reflection. Android's hidden-API restrictions depend on the target SDK, so a
  higher target could block that call. The root daemon runs under
  `app_process`, not as the installed app, but it is launched only through
  that call.
- The in-app updater relies on the pre-26 install-permission model;
  `canRequestPackageInstalls()` applies only from target 26.
- Targets 31 and higher add explicit `android:exported` and `PendingIntent`
  mutability requirements; the manifest and updater already declare them.

None of this has been tested above 25 on a Thor. A raise must be isolated
from stable runtime changes, verified for boot launch, the privileged bridge,
updates and Android's compatibility warnings, then subjected to a separate
release decision. See [Target SDK 25 migration audit](TARGET-SDK-25-MIGRATION.md)
for dependency-by-dependency blockers and supervised test gates, and
[Failure validation matrix](FAILURE-VALIDATION-MATRIX.md) for updater,
daemon, handoff and absent Binder scenarios.

## Why Java and smali are both present

The dashboard, telemetry, state models, coordinator, root launcher, and socket
protocol are maintained in Java under `src/`. The smali watcher now only reads
the AYN mode at its existing 20 ms cadence and hands samples to the Java
coordinator. The hidden display callback and wake scheduler remain in smali.
The coordinator throttles its DRM reads; it does not read DRM on every mode sample.

Small Java stubs under `stubs/` allow the Java portion to compile against those smali-owned classes. The build process:

1. builds the resource/smali APK with apktool;
2. compiles the Java sources and stubs against Android platform 34;
3. merges the resulting Java archive into the APK's DEX with D8;
4. aligns the unsigned APK;
5. optionally signs it with a user-supplied keystore.

## Wake repair timeline

The tested framework wakes Display 4 before the app receives the display callback. Immediate OFF attempts therefore fight the Android screen-on pipeline.

The stable scheduler uses the later of:

- wake detection + 700 ms;
- Display 4 ON confirmation + 400 ms.

Before applying OFF it rechecks the current generation, fix state, mode, and
CRTCs. Mode, fix, sleep, and boot transitions invalidate pending work. An
unknown mode or ambiguous/sleeping CRTCs never trigger a speculative ON.

## Telemetry

`Q` returns true-off state, both physical CRTC states, Dashboard CPU Fix state,
daemon uptime, cluster current/max frequencies, cumulative LITTLE/BIG
`time_in_state` counters, aggregate CPU utilization, battery/external power
source, wake ID, timing markers, repair result, and last action. Version 1.4.0
adds `boot_phase`, `display_actions_held`, `lid`, `lid_guard`,
`lid_guard_state`, `blocked_wakes`, `last_lid_action`, and `external_display`.
The 1.5.0 candidate adds display intent/effective/action status/generation,
watcher health, and CPU Fix intent/phase so pending or failed work is not
reported as confirmed.
`G` and `H` persist the user-facing guard selection through the Android app;
the daemon's Hall availability check may reject `G`.

Clock values come from `policy0`, `policy3`, and `policy7`. Maximum frequency
uses `scaling_max_freq` with `cpuinfo_max_freq` as fallback. The visible app
compares cumulative `time_in_state` deltas over a 12-second window and only
reports pinning when LITTLE and BIG each spend at least 85% of a settled,
low-load window in the top 5% of the cluster's frequency range. This includes
the reproduced BIG lock at 2.707 GHz even though its policy advertises a
2.803 GHz ceiling. Exact-maximum counters remain in `Q`; additive
`little_high_ticks` and `big_high_ticks` fields drive the dashboard. No
governor or frequency file is written.

Battery draw is reported only when USB is offline and the battery status is
`Discharging`. It is calculated from the absolute battery current multiplied
by battery voltage. The UI presents the median of its five latest valid
samples. The older USB/system-proxy fields remain in `Q` for diagnostics and
protocol compatibility but are no longer displayed.

`V` performs an explicit one-shot CRTC verification. While the daemon watches
mode changes, the coordinator samples CRTCs at most every 250 ms in steady
state, and immediately for mode changes or due wake repairs. The app's visible
dashboard also samples physical state in its once-per-second `Q` poll.

Those polling frequencies describe implementation cost, **not a demonstrated
gameplay defect**. No micro-stutter, thermal regression, or frame-time problem
has been attributed to the watcher. The project is measuring daemon and
SettingsProvider cost before changing a physically stable display path; see
the current roadmap.

## CPU Fix application and startup recovery

`R` and `L` enable or disable the Dashboard CPU Fix. The control changes
`vendor.display.disable_system_load_check`; it does **not** write CPU
frequencies, governors, voltages or thermal limits.

The important vendor constraint is that the Thor's Qualcomm display stack reads
this property in `ResourceImpl::Init()` and caches the result in the running
composer. Physical and static-analysis work established that:

- a late property write alone does not change the effective state;
- TOP/BOTH recreation in the same composer process does not reload it;
- a replacement composer with the new property value does apply it;
- no supported same-process `ResourceImpl` recreation path was found on the
  tested firmware.

Therefore changing CPU Fix while Android is already running requires **one**
controlled compositor replacement. The firmware's composer `onrestart`
restarts SurfaceFlinger, and SurfaceFlinger in turn restarts the running zygote,
so Android UI/framework and open apps are affected even though the kernel does
not reboot. The app warns before a user-triggered toggle and persists
`RESTART_REQUESTED` before issuing the restart, so an ambiguous handoff never
causes a blind second attempt.

### Why v1.6.0 still has one startup compositor replacement

A true zero-restart cold boot would require the property to be set before the
**first** composer consumes it.

The inspected stock Thor firmware provides no safe app-controlled writable
execution path that early:

- Qualcomm's own `init.qti.display_boot.sh` demonstrates that the property can
  be set pre-composer for a different hardware subtype;
- the tested Thor is subtype 0, so that stock branch is not taken;
- the full inspected init tree exposed no acceptable writable pre-composer
  script/property hook;
- stock `pservice` executes `/data/boot_start.sh`, but measured pservice
  startup was about 133 ms **after** the first composer process started.

That means the remaining compositor replacement is not an avoidable UI choice
in the proven stock-firmware/app-only design. Removing it entirely would
require AYN/Qualcomm firmware/init support, modification/overlay of immutable
vendor content, or genuinely new evidence of a trusted privileged mechanism
that executes before the first composer. Jesty Thor Fix deliberately does not
spoof hardware subtype or patch `/vendor` to remove a short startup recovery.

### v1.6.0 early-startup path

With saved CPU Fix ON, the app maintains a tightly owned
`/data/boot_start.sh` hook for the stock pservice path. The hook is
enable-only and guarded by app-private Direct-Boot identity/opt-in state.

On a real kernel boot:

1. stock pservice launches the managed hook during normal startup;
2. the hook validates app identity/opt-in and a boot-scoped `/dev` no-repeat
   record;
3. it writes and verifies the CPU property;
4. it records `RESTART_REQUESTED` **before** requesting one composer restart;
5. it never directly restarts SurfaceFlinger/zygote, reboots Android, or changes
   panel routing;
6. the later normal daemon imports that same-boot attempt, proves that the
   composer PID changed, and only then records `APPLIED`;
7. the owned global hook is reconciled/removed after the early attempt has been
   consumed.

Unknown pre-existing `/data/boot_start.sh` content is never overwritten.
Disabling CPU Fix removes only a hook whose ownership/content are verified.
Uninstall removes the app-private opt-in, so any managed residue becomes inert.

### Physical v1.6.0 provenance

The prototype path that became v1.6.0 was physically validated on the Thor:

- early attempt imported as `RESTART_REQUESTED`;
- baseline composer PID `1221`;
- successor composer PID `2314`;
- attempt reached `APPLIED`;
- final mode was BOTH with both physical CRTCs active;
- the one-shot gate was consumed and the managed hook cleaned up;
- the later normal daemon did **not** request a second CPU-fix restart.

The visible result is a slightly longer black phase during natural startup
instead of the old late second boot-like interruption after Android had already
appeared. That is the intended v1.6.0 behavior.

See [pre-composer proof](PRECOMPOSER-CPU-FIX-PROOF.md),
[restart-options history](CPU-FIX-RESTART-OPTIONS.md), and
[research provenance](../PROVENANCE.md) for the evidence trail.
