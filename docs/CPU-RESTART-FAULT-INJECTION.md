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


## Physical validation — 2026-10-06

Validated on a real Thor with signed test candidate built from the fault-injection branch.

Observed boot:

```text
boot_id = 176cd515-f02f-4fd9-946c-f7df10b3534c
initial daemon pid = 6789
baseline composer pid = 1294
successor daemon pid = 6991
new composer pid = 7141
post-restart daemon pid = 8305
```

Trace proved the intended crash window:

```text
34552  CPU_ATTEMPT_PREPARED
34583  CPU_PROP_WRITTEN
34591  CPU_ATTEMPT_PROPERTY_VERIFIED
34591  CPU_PROP_VERIFIED
34592  CPU_FAULT_INJECTION_ARMED
34593  CPU_FAULT_INJECTION_TRIGGERED

34895  CPU_ATTEMPT_RESTART_REQUESTED   # successor daemon pid 6991
35240  HELPER_START old_composer=1294
36190  HELPER_COMPOSER_NEW_PID composer_pid=7141

43802  CPU_ATTEMPT_APPLIED baseline_pid=1294 composer_pid=7141
49962  BOOT_READY
```

The one-shot trigger was consumed before recovery. For this boot there was exactly one
`CPU_ATTEMPT_RESTART_REQUESTED`, one helper/composer replacement path, one APPLIED transition and
one BOOT_READY. No `CPU_ATTEMPT_FAILED` or `CPU_RESTART_ATTEMPT_FAILED` was observed.

The successor path did not emit `CTL_RESTART_SENT` because that trace point is currently
conditional on an active boot session. The single helper start plus a single composer PID
replacement, together with the state-model no-retry invariant after `RESTART_REQUESTED`, is
sufficient to validate the intended provenance recovery. If exact restart-send counting is needed
for a future fault test, make those trace markers unconditional before repeating the test.

Result: **PASS**. The crash between durable property verification and restart no longer allows a
plain matching `getprop` value to be mistaken for an already-applied compositor state.
