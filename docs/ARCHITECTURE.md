# Architecture

Jesty Thor Fix is split between a normal Android dashboard and a privileged root-side daemon.

## Android side

- `MainActivity` renders the dashboard, controls the persistent fix and guard settings,
  and polls telemetry once per second only while visible.
- `AutoService` starts or reconnects to the daemon, migrates the old protocol
  once, and reconciles saved settings after a normal boot. From v1.5.16 it
  classifies an unreachable socket pathname before waiting (see *Daemon launch
  and stale sockets*).
- `BootReceiver` starts that service after a normal boot.
- `SocketClient` sends one-byte commands over a filesystem Unix socket in the
  app's private `files/` directory. It checks the kernel-reported server UID
  before trusting the reply; `ok=1` indicates command handling, not identity.

The dashboard does not need to remain open for the fix to stay active.

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

## Staged boot and lid guard

The boot receiver launches the daemon in `BOOT HOLD` with its saved choices.
The mode watcher, display callback, socket display commands, and lid sleep
action respect this gate. `BootGateModel` requires Android boot completion,
running compositor, a known AYN display mode, three matching CRTC samples, and
then a 10-second stable grace period. The CPU property is reconciled first; if
that restarts the compositor, the relaunched daemon repeats readiness checks
with a five-second grace period. Only then does it reconcile the lower display
idempotently. At 60 seconds without readiness, it remains held and reports
`BOOT SAFETY TIMEOUT` without forcing a display or sleep action.

The optional `LidGuard` reads Linux `EV_SW/SW_LID` events from `hall_switch`
directly. Unknown Hall, external display, unknown dock/interactive state, or
an open lid inhibit sleep. It waits 1.5 seconds after closure or 500 ms after
a closed-lid wake and rechecks before `KEYCODE_SLEEP`. After three sleep
attempts in ten seconds it pauses until the lid opens. It does not require
Device Admin, Accessibility, SensorManager, or another foreground service.
The current review covers BOTH and TOP with at most two observed cold boots.
BOTTOM ONLY and physical dock behavior are deferred to future improvements by
user decision; neither is a gate for the current BOTH/TOP review.

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

`R` and `L` enable or disable the Dashboard CPU Fix. They update
`vendor.display.disable_system_load_check`, restart the display compositor once,
and arrange the daemon relaunch described above. The Thor firmware explicitly
declares `onrestart restart surfaceflinger` in
`/vendor/etc/init/vendor.qti.hardware.display.composer-service.rc`, while
SurfaceFlinger's `/system/etc/init/surfaceflinger.rc` declares
`onrestart restart --only-if-running zygote`. Thus one requested composer
restart cascades into Android UI/framework restart and USB re-enumeration;
open apps close, but the kernel boot ID does not change. A timed kernel
wake-lock covers the transition. From v1.5.12 the relaunched daemon releases
it after the post-restart readiness and display reconciliation phase; the
kernel timeout is a bounded fallback if that daemon never starts. The
post-restart phase gets its own 60-second deadline, and saved Lid Guard intent
is carried across the relaunch even while BOOT HOLD has kept its watcher off.
Opening the UI does not issue either command.

On the tested `kalama` SoC 603, subtype 0, the vendor display-boot script only
sets `vendor.display.disable_system_load_check=1` for subtype 1. The property
therefore remains absent after a cold boot on this Thor. v1.5.15 reads it
with a default marker, preserving a distinct `UNSET` observation internally.
Public telemetry still reports `?` until the fix is applied; it never
relabels the absent value as `0` or claims success from saved intent. With
saved CPU Fix ON, a successful `UNSET` observation permits one guarded write
to `1` and compositor restart. Read failure or invalid values fail safe.
The relaunched daemon verifies `1` before reporting the fix as active.
One supervised cold boot and an in-place transition exercised this path.
The compositor restart also restarts Android UI, so a second visual boot
phase can occur without another kernel boot. The roughly 95-second readiness
time observed in that cold boot remains a performance follow-up. v1.5.16
removes the stale-socket wait identified as its largest candidate and adds the
trace marks above; the effect is unconfirmed until a supervised cold boot.
