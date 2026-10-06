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
