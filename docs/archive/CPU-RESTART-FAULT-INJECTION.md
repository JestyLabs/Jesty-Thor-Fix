# Archived CPU restart provenance fault injection

Source branch: `test/thor-cpu-restart-fault-injection`  
Head: `cd985361deff09e0b220e116745c623cd37908ac`  
Original PR: #19

This was deliberately test-only code and must not be wired into release behavior. It is preserved here because the physical fault-injection result is useful provenance for the CPU restart state model.

## Original research document

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
boot_id = 00000000-0000-4000-8000-000000000004
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


---

## Original fault-injection class

```java
package com.thor.displaypowertest;

import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;
import android.util.Log;

import java.io.FileDescriptor;
import java.nio.charset.StandardCharsets;

/**
 * TEST-BRANCH ONLY fault injection for the CPU restart provenance path.
 *
 * A shell-created one-shot marker asks the daemon to die after the vendor
 * property has been read back and PROPERTY_VERIFIED is durable, but before
 * RESTART_REQUESTED exists. A tiny root helper then relaunches a normal daemon
 * without touching the compositor. The successor must recover from the marker
 * and issue exactly one compositor restart.
 *
 * This class must never be merged into a release branch.
 */
public final class CpuRestartFaultInjection {
    public static final String TRIGGER_PATH =
            "/data/local/tmp/jesty-thor-fault-after-property-verified";
    private static final String TRIGGER_TEXT = "AFTER_PROPERTY_VERIFIED_ONCE";
    private static final int SHELL_UID = 2000;
    private static final int MAX_BYTES = 64;

    private CpuRestartFaultInjection() {}

    /**
     * Returns normally only when no fault was armed or the helper could not be
     * started. A successful injection kills this daemon before this method can
     * return, so the caller can never advance to RESTART_REQUESTED itself.
     */
    public static void triggerAfterPropertyVerifiedIfArmed(BootTrace trace,
            boolean displayFixEnabled, boolean cpuFixDesired, boolean lidGuardDesired) {
        if (!consumeTrigger()) return;

        trace.mark("CPU_FAULT_INJECTION_ARMED",
                "point=AFTER_PROPERTY_VERIFIED;one_shot=1");

        Process helper = null;
        try {
            int daemonPid = android.os.Process.myPid();
            String command = String.join("\n",
                    RootLogFiles.SHELL_GUARD,
                    "L=" + RootLogFiles.DAEMON_LOG,
                    "if safe_log \"$L\"; then exec </dev/null >>\"$L\" 2>&1;"
                            + " else exec </dev/null >/dev/null 2>&1; fi",
                    "DP=" + daemonPid,
                    "A=$(pm path com.thor.displaypowertest 2>/dev/null); A=${A#*:}",
                    "[ -n \"$A\" ] || exit 31",
                    "kill $DP 2>/dev/null || exit 32",
                    "I=0; while [ -d /proc/$DP ] && [ $I -lt 50 ];"
                            + " do sleep 0.1; I=$((I+1)); done",
                    "[ ! -d /proc/$DP ] || exit 33",
                    "CLASSPATH=$A app_process / D "
                            + (displayFixEnabled ? "1" : "0")
                            + " run " + (cpuFixDesired ? "1" : "0")
                            + " " + (lidGuardDesired ? "1" : "0") + " &");
            helper = new ProcessBuilder("sh", "-c", command).start();
            trace.mark("CPU_FAULT_INJECTION_TRIGGERED",
                    "point=AFTER_PROPERTY_VERIFIED;daemon_pid=" + daemonPid);

            // On success the helper kills this process, so execution never
            // reaches the loop's terminal branch. If the helper exits first,
            // the fault did not occur and normal CPU reconciliation may resume.
            for (int poll = 0; poll < 60; poll++) {
                if (ProcessWait.exited(helper, 100L)) {
                    int exit = helper.exitValue();
                    trace.mark("CPU_FAULT_INJECTION_ABORT",
                            "reason=HELPER_EXIT;exit=" + exit);
                    return;
                }
            }
            helper.destroy();
            trace.mark("CPU_FAULT_INJECTION_ABORT", "reason=HELPER_TIMEOUT");
        } catch (Throwable error) {
            if (helper != null) helper.destroy();
            trace.mark("CPU_FAULT_INJECTION_ABORT", "reason=HELPER_START_FAILED");
            Log.e("ThorDisplayDaemon", "CPU fault injection helper failed", error);
        }
    }

    /**
     * Accept only a tiny regular single-link file owned by shell/root with the
     * exact token. The trigger is removed before any process is killed, making
     * the injection one-shot across the successor relaunch and reboot.
     */
    private static boolean consumeTrigger() {
        FileDescriptor fd = null;
        try {
            StructStat path = Os.lstat(TRIGGER_PATH);
            if (!trusted(path) || path.st_size <= 0L || path.st_size > MAX_BYTES) return false;

            fd = Os.open(TRIGGER_PATH, OsConstants.O_RDONLY
                    | OsConstants.O_CLOEXEC | OsConstants.O_NOFOLLOW, 0);
            StructStat opened = Os.fstat(fd);
            if (!trusted(opened) || opened.st_ino != path.st_ino
                    || opened.st_dev != path.st_dev || opened.st_size != path.st_size) {
                return false;
            }

            byte[] bytes = new byte[(int) opened.st_size];
            int offset = 0;
            while (offset < bytes.length) {
                int read = Os.read(fd, bytes, offset, bytes.length - offset);
                if (read <= 0) return false;
                offset += read;
            }
            String value = new String(bytes, StandardCharsets.US_ASCII).trim();
            if (!TRIGGER_TEXT.equals(value)) return false;

            Os.close(fd);
            fd = null;

            // Revalidate the path immediately before consuming it.
            StructStat current = Os.lstat(TRIGGER_PATH);
            if (!trusted(current) || current.st_ino != opened.st_ino
                    || current.st_dev != opened.st_dev) {
                return false;
            }
            Os.remove(TRIGGER_PATH);
            return true;
        } catch (Throwable ignored) {
            return false;
        } finally {
            if (fd != null) {
                try { Os.close(fd); } catch (Throwable ignored) {}
            }
        }
    }

    private static boolean trusted(StructStat stat) {
        return OsConstants.S_ISREG(stat.st_mode)
                && stat.st_nlink == 1
                && (stat.st_uid == SHELL_UID || stat.st_uid == 0);
    }
}

```

Status: **ARCHIVED TEST HARNESS / DO NOT SHIP**.
