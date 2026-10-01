# Architecture

Jesty Thor Fix is split between a normal Android dashboard and a privileged root-side daemon.

## Android side

- `MainActivity` renders the dashboard, controls the persistent fix and guard settings,
  and polls telemetry once per second only while visible.
- `AutoService` starts or reconnects to the daemon, migrates the old protocol
  once, and reconciles saved settings after a normal boot.
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
only after an authenticated health check. There is no TCP fallback. The app's
private socket, SELinux access, and migration still require on-device validation
for 1.5.0; an unsigned build is not evidence that they work on the Thor.

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
The complete physical matrix remains a release gate for 1.5.0.

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
wake-lock covers the transition and a surviving helper releases it after
recovery. Opening the UI does not issue either command.
