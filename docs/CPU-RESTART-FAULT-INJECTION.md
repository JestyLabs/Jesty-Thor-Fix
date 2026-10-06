# CPU restart fault-injection test

Status: **test branch only**. Do not merge this harness into a release branch.

Branch:

```text
test/thor-cpu-restart-fault-injection
```

## Goal

Physically reproduce the provenance failure window that motivated
`CpuBootAttemptModel` / `CpuBootAttemptStore`:

```text
setprop succeeds
  -> PROPERTY_VERIFIED is durable
  -> daemon dies before RESTART_REQUESTED / ctl.restart
  -> old composer is still alive with its old cached vendor flag
  -> successor daemon must recover from the marker
  -> exactly one compositor restart
  -> new composer PID
  -> APPLIED
```

A plain `getprop == desired` must never be accepted as proof that the old compositor consumed
the value.

## One-shot trigger

The test candidate checks this shell-owned file only at the exact fault point:

```text
/data/local/tmp/jesty-thor-fault-after-property-verified
```

Required content:

```text
AFTER_PROPERTY_VERIFIED_ONCE
```

The daemon accepts only a regular, single-link file owned by shell/root, opens it with
`O_NOFOLLOW`, validates the exact token, then removes it **before** killing anything. Therefore
the trigger is one-shot even when the successor daemon starts immediately.

Arm it from ADB:

```powershell
adb shell "printf '%s\n' AFTER_PROPERTY_VERIFIED_ONCE > /data/local/tmp/jesty-thor-fault-after-property-verified"
adb shell "cat /data/local/tmp/jesty-thor-fault-after-property-verified"
```

Then reboot:

```powershell
adb reboot
```

## Injection behavior

After `setprop` readback and a durable `PROPERTY_VERIFIED` write, but before
`RESTART_REQUESTED`, the test harness:

1. consumes the one-shot trigger;
2. records `CPU_FAULT_INJECTION_ARMED`;
3. starts a tiny root helper;
4. records `CPU_FAULT_INJECTION_TRIGGERED`;
5. helper kills only the current root daemon;
6. helper waits for that daemon PID to disappear;
7. helper launches the same APK as a normal `run` daemon;
8. helper does **not** restart or otherwise touch the compositor.

The normal successor then enters `resumePersistedAttempt()`. It should see
`PROPERTY_VERIFIED` with the same baseline composer PID, persist `RESTART_REQUESTED`, and issue
the one allowed compositor restart.

If the helper cannot start or kill/relaunch safely, the current daemon continues the normal path
and records `CPU_FAULT_INJECTION_ABORT`; the test trigger has already been consumed.

## Pass criteria

For one kernel `boot_id`, the trace should prove:

```text
CPU_ATTEMPT_PREPARED
CPU_PROP_WRITTEN
CPU_ATTEMPT_PROPERTY_VERIFIED
CPU_PROP_VERIFIED
CPU_FAULT_INJECTION_ARMED
CPU_FAULT_INJECTION_TRIGGERED

# daemon PID changes here, composer PID must still be baseline

CPU_ATTEMPT_RESTART_REQUESTED      # exactly once
CTL_RESTART_SENT                   # exactly once
HELPER_COMPOSER_NEW_PID
CPU_ATTEMPT_APPLIED
BOOT_READY
```

Required invariants:

- no `RESTART_REQUESTED` before the injected daemon death;
- exactly one `CPU_ATTEMPT_RESTART_REQUESTED` for that `boot_id`;
- exactly one `CTL_RESTART_SENT` for that `boot_id`;
- composer PID remains the baseline across the daemon-only crash/relaunch;
- `APPLIED` is written only after composer PID changes;
- no `CPU_ATTEMPT_FAILED`;
- no `CPU_RESTART_ATTEMPT_FAILED`;
- trigger file is absent after the test.

## Collect evidence

After Android reaches the launcher:

```powershell
adb shell getprop vendor.display.disable_system_load_check
adb shell pidof vendor.qti.hardware.display.composer-service
adb shell cat /proc/sys/kernel/random/boot_id
adb shell "ls -l /data/local/tmp/jesty-thor-fault-after-property-verified 2>/dev/null || echo trigger-consumed"
adb shell "tail -n 300 /data/local/tmp/jesty-thor-boot-trace.log"
```

Do not arm the trigger for ordinary boot-timing measurements. The deliberate daemon crash adds an
extra handoff and is only for provenance validation.

## After this passes

Remove this fault-injection candidate. Test the normal current `main` candidate, which already
contains the independent early CPU gate:

```text
sys.boot_completed
+ composer running / valid PID
+ watcher RUNNING
+ valid boot_id / CPU property
  -> CPU restart provenance reconcile
  -> restart immediately if required
  -> unchanged display mode/CRTC stability gate
  -> unchanged display grace
  -> display reconcile / Wake Guard
```

That clean boot is the measurement used to validate the expected ~10 second restart shift.
