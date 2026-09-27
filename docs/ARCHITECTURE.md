# Architecture

Jesty Thor Fix is split between a normal Android dashboard and a privileged root-side daemon.

## Android side

- `MainActivity` renders the dashboard, controls both persistent fix settings,
  and polls telemetry once per second only while visible.
- `AutoService` starts or reconnects to the daemon, migrates the old protocol
  once, and reconciles saved settings after a normal boot.
- `BootReceiver` starts that service after a normal boot.
- `SocketClient` sends one-byte commands to the loopback daemon and requires an explicit `ok=1` acknowledgement.

The dashboard does not need to remain open for the fix to stay active.

## Root daemon

`PServer` asks the Thor firmware's `PServerBinder` service to launch `app_process / D` with the installed APK on its classpath. The daemon then:

- watches the Thor dual-screen mode;
- registers for display events;
- schedules one generation-aware wake repair;
- applies lower-display power through hidden `SurfaceControl` APIs;
- updates the Thor-specific `display.power.state` property;
- exposes status and one-shot verification over `127.0.0.1:3804`.
- applies the optional vendor system-load-check property for the AYN Dashboard
  CPU Fix and performs the required one-shot display-compositor restart, which
  restarts Android's framework processes and closes open apps;
- holds a named, timed kernel wake-lock across that transition so the Thor
  cannot suspend behind the black displays, then releases it after recovery;
- schedules a clean daemon relaunch after that display reset so the watcher
  receives fresh Android service binders and true-off remains independent.

The socket binds to loopback, not to an external network interface.

## Why Java and smali are both present

The dashboard, telemetry, state model, root launcher, and socket protocol are maintained in Java under `src/`. The previously validated watcher, hidden display callback integration, and wake scheduler remain in smali under `apk/smali/` so their device-specific hidden-API behavior stays byte-stable.

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

Before applying OFF it rechecks the current generation, fix state, and display mode. Native mode cancels pending repair and performs a final ON after the transition so a stale OFF cannot win.

## Telemetry

`Q` returns true-off state, physical lower-CRTC state, Dashboard CPU Fix state,
daemon uptime, cluster current/max frequencies, aggregate CPU utilization, wake
ID, timing markers, repair result, and last action.

Clock values come from `policy0`, `policy3`, and `policy7`. Maximum frequency uses `scaling_max_freq` with `cpuinfo_max_freq` as fallback. No governor or frequency file is written.

`V` reads `/sys/kernel/debug/dri/0/state` once and reports the tested top/bottom CRTCs. Debugfs is not continuously polled.

`R` and `L` enable or disable the Dashboard CPU Fix. They update
`vendor.display.disable_system_load_check`, restart the display compositor once,
and arrange the daemon relaunch described above. The composer dependency chain
also restarts SurfaceFlinger and Android framework processes; this is why open
apps close even though the kernel boot ID does not change. A timed kernel
wake-lock covers the transition and a surviving helper releases it after
recovery. Opening the UI does not issue either command.
