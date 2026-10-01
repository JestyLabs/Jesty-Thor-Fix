import com.thor.displaypowertest.DaemonState;
import com.thor.displaypowertest.DaemonIdentity;
import com.thor.displaypowertest.BootGateModel;
import com.thor.displaypowertest.BootSafety;
import com.thor.displaypowertest.DaemonWatchThread;
import com.thor.displaypowertest.DisplayEventManager;
import com.thor.displaypowertest.DisplayHardware;
import com.thor.displaypowertest.DisplayActionCoordinator;
import com.thor.displaypowertest.LidGuard;
import com.thor.displaypowertest.Telemetry;
import com.thor.displaypowertest.WakeRepairScheduler;
import com.thor.displaypowertest.WatcherSupervisor;

import android.util.Log;
import android.os.SystemClock;
import android.net.LocalServerSocket;
import android.net.LocalSocket;

import java.io.OutputStream;
import java.io.FileWriter;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import com.thor.displaypowertest.SecureChannel;

public final class D {
    private static final String TRANSITION_WAKE_LOCK = "jesty_dashboard_cpu_transition";
    private static volatile boolean composerRestartScheduled;
    private static volatile boolean composerRestartFailed;
    private static volatile boolean cpuFixDesired;
    private static boolean bootCoordinatorActive;
    private static boolean bootCpuFixDesired;
    private static boolean bootLidGuardDesired;
    private static long bootStartedAt;
    public static void main(String[] args) throws Exception {
        boolean enabled = args.length == 0 || !"0".equals(args[0]);
        bootCoordinatorActive = (args.length > 1 && "hold".equals(args[1]))
                || !"1".equals(property("sys.boot_completed"));
        bootCpuFixDesired = args.length > 2 && "1".equals(args[2]);
        cpuFixDesired = bootCpuFixDesired;
        bootLidGuardDesired = args.length > 3 && "1".equals(args[3]);
        bootStartedAt = args.length > 4 ? Long.parseLong(args[4])
                : SystemClock.elapsedRealtime();
        boolean afterComposerRestart = args.length > 5 && "post".equals(args[5]);
        BootSafety.begin(bootCoordinatorActive);
        DaemonState.setEnabled(enabled);
        if (!bootCoordinatorActive) LidGuard.setEnabled(bootLidGuardDesired);
        LocalServerSocket server = SecureChannel.listen();
        WatcherSupervisor.start();
        DisplayEventManager.register();
        ThreadPoolExecutor connections = new ThreadPoolExecutor(2, 2, 0L,
                TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(4));
        if (bootCoordinatorActive) {
            new Thread(() -> reconcileBoot(afterComposerRestart), "thor-boot-coordinator").start();
        }
        Log.d("ThorDisplayDaemon", "READY " + DaemonIdentity.VERSION + " enabled=" + enabled
                + " bootHold=" + bootCoordinatorActive);
        while (true) {
            LocalSocket socket = server.accept();
            try {
                connections.execute(() -> serve(socket));
            } catch (java.util.concurrent.RejectedExecutionException busy) {
                socket.close();
            }
        }
    }

    private static void serve(LocalSocket socket) {
        try (LocalSocket client = socket) {
            if (!SecureChannel.isTrustedApp(client)) {
                Log.w("ThorDisplayDaemon", "rejected untrusted local client");
                return;
            }
            client.setSoTimeout(1500);
            int command = client.getInputStream().read();
            String response = command < 0 ? "ok=0;error=EMPTY_COMMAND"
                    : handle((char) command);
            OutputStream output = client.getOutputStream();
            output.write((response + "\n").getBytes(StandardCharsets.UTF_8));
            output.flush();
        } catch (Throwable error) {
            Log.e("ThorDisplayDaemon", "command failed", error);
        }
    }

    private static String handle(char command) {
        if (BootSafety.isHeld() && command != 'I' && command != 'Q' && command != 'V') {
            return "ok=0;error=BOOT_HOLD;boot_phase="
                    + BootSafety.phase().replace(' ', '_');
        }
        switch (command) {
            case 'I': return "ok=1;protocol=" + SecureChannel.PROTOCOL
                    + ";version=" + DaemonIdentity.VERSION + ";pid=" + android.os.Process.myPid()
                    + ";boot_phase=" + BootSafety.phase().replace(' ', '_')
                    + ";fix=" + (DaemonState.isEnabled() ? "1" : "0")
                    + ";watcher=" + WatcherSupervisor.health();
            case 'E':
                return DisplayActionCoordinator.requestFix(true);
            case 'N':
                return DisplayActionCoordinator.requestFix(false);
            case 'Q': return DaemonState.snapshot()
                    + ";cpu_fix_desired=" + (cpuFixDesired ? "1" : "0")
                    + ";cpu_fix_phase=" + cpuFixPhase();
            case 'V': return Telemetry.verifyDrm();
            case 'R': return restartComposerWithSystemLoadCheckDisabled(true);
            case 'L': return restartComposerWithSystemLoadCheckDisabled(false);
            case 'G': return LidGuard.setEnabled(true) ? "ok=1;lid_guard=1"
                    : "ok=0;error=HALL_UNAVAILABLE";
            case 'H': LidGuard.setEnabled(false); return "ok=1;lid_guard=0";
            default: return "ok=0;error=UNKNOWN_COMMAND";
        }
    }

    private static void reconcileBoot(boolean afterComposerRestart) {
        try {
            reconcileBootPhase(afterComposerRestart);
        } finally {
            // A surviving post-restart daemon owns the release. The kernel's
            // timed lock is the fallback if that daemon never starts.
            if (afterComposerRestart) releaseTransitionWakeLock();
        }
    }

    private static void reconcileBootPhase(boolean afterComposerRestart) {
        BootGateModel gate = new BootGateModel(bootStartedAt, afterComposerRestart);
        BootSafety.phase(afterComposerRestart ? "WAITING AFTER COMPOSER" : "WAITING FOR ANDROID");
        traceBoot(afterComposerRestart ? "WAIT_AFTER_COMPOSER" : "WAIT_FOR_ANDROID");
        while (true) {
            long now = SystemClock.elapsedRealtime();
            BootGateModel.Result result = gate.observe(now,
                    "1".equals(property("sys.boot_completed")),
                    "running".equals(property("init.svc.vendor.qti.hardware.display.composer")),
                    DaemonState.getMode(), Telemetry.topCrtcActive(), Telemetry.bottomCrtcActive());
            if (result == BootGateModel.Result.TIMEOUT) {
                BootSafety.timeout();
                DaemonState.setLastAction("BOOT_SAFETY_TIMEOUT");
                Log.e("ThorDisplayDaemon", "BOOT SAFETY TIMEOUT; no display action");
                traceBoot("BOOT_SAFETY_TIMEOUT");
                return;
            }
            if (result == BootGateModel.Result.READY) break;
            try { Thread.sleep(500L); } catch (InterruptedException ignored) { return; }
        }
        BootGateModel.CpuAction cpuAction = BootGateModel.cpuAction(bootCpuFixDesired,
                Telemetry.systemLoadFixState(), afterComposerRestart);
        if (cpuAction == BootGateModel.CpuAction.FAIL_SAFE) {
            // An unknown CPU property must never trigger a compositor restart.
            // It does not make a separately verified display state unsafe.
            DaemonState.setLastAction("BOOT_CPU_FIX_NOT_APPLIED");
            traceBoot("BOOT_CPU_FIX_NOT_APPLIED");
            Log.w("ThorDisplayDaemon", "CPU fix not confirmed; continuing safe display reconcile");
        }
        if (cpuAction == BootGateModel.CpuAction.RESTART_ONCE) {
            BootSafety.phase("APPLYING CPU FIX");
            traceBoot("APPLY_CPU_FIX");
            String result = restartComposerWithSystemLoadCheckDisabled(bootCpuFixDesired);
            if (result.contains("composer_restart=scheduled_once")) return;
            BootSafety.timeout();
            DaemonState.setLastAction("BOOT_CPU_FIX_FAILED");
            Log.e("ThorDisplayDaemon", "boot CPU reconcile failed: " + result);
            traceBoot("BOOT_CPU_FIX_FAILED");
            return;
        }
        BootSafety.phase("RECONCILING DISPLAY");
        traceBoot("RECONCILE_DISPLAY");
        String mode = DaemonState.getMode();
        if (!BootSafety.knownMode(mode)) {
            BootSafety.timeout();
            return;
        }
        boolean bottomShouldBeOn = !DaemonState.isEnabled() || !"1".equals(mode);
        if (!DisplayActionCoordinator.reconcileBoot(mode, bottomShouldBeOn)) {
            BootSafety.timeout();
            traceBoot("BOOT_DISPLAY_FAILED");
            return;
        }
        traceBoot(bottomShouldBeOn ? "BOTTOM_ON_CONFIRMED" : "BOTTOM_OFF_CONFIRMED");
        BootSafety.ready();
        bootCoordinatorActive = false;
        if (bootLidGuardDesired && !LidGuard.setEnabled(true)) {
            Log.w("ThorDisplayDaemon", "Hall switch unavailable; wake guard disabled");
        }
        DaemonState.setLastAction("BOOT_READY");
        Log.d("ThorDisplayDaemon", "BOOT READY mode=" + mode
                + " bottom=" + Telemetry.bottomCrtcActive());
        traceBoot("BOOT_READY");
    }

    /** Device-local trace contains only phase and hardware flags, no personal data. */
    private static void traceBoot(String action) {
        String observedMode = DaemonState.getMode();
        String line = "elapsed_ms=" + SystemClock.elapsedRealtime()
                + ";action=" + action
                + ";mode=" + (BootSafety.knownMode(observedMode) ? observedMode : "?")
                + ";top_crtc=" + Telemetry.topCrtcActive()
                + ";bottom_crtc=" + Telemetry.bottomCrtcActive()
                + ";cpu_fix=" + Telemetry.systemLoadFixState() + "\n";
        File trace = new File("/data/local/tmp/jesty-thor-boot-trace.log");
        try (FileWriter writer = new FileWriter(trace, true)) {
            writer.write(line);
            trace.setReadable(true, false);
        } catch (Throwable error) {
            Log.w("ThorDisplayDaemon", "could not write sanitized boot trace", error);
        }
    }

    private static String property(String name) {
        Process process = null;
        try {
            process = new ProcessBuilder("getprop", name).start();
            if (!process.waitFor(2L, TimeUnit.SECONDS) || process.exitValue() != 0)
                return "?";
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()));
            String value = reader.readLine();
            reader.close();
            return value == null || value.trim().isEmpty() ? "?" : value.trim();
        } catch (Throwable ignored) { return "?"; }
        finally { if (process != null) process.destroy(); }
    }

    private static String composerPid() {
        Process process = null;
        try {
            process = new ProcessBuilder("pidof",
                    "vendor.qti.hardware.display.composer-service").start();
            if (!process.waitFor(2L, TimeUnit.SECONDS) || process.exitValue() != 0)
                return "?";
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String value = reader.readLine();
                return value != null && value.trim().matches("[0-9]+")
                        ? value.trim() : "?";
            }
        } catch (Throwable ignored) { return "?"; }
        finally { if (process != null) process.destroy(); }
    }

    private static String setSystemLoadCheckDisabled(boolean enabled) {
        String value = enabled ? "1" : "0";
        Process process = null;
        try {
            process = new ProcessBuilder("setprop",
                    "vendor.display.disable_system_load_check", value).start();
            if (!process.waitFor(2L, TimeUnit.SECONDS)) return "ok=0;error=SETPROP_TIMEOUT";
            int exit = process.exitValue();
            if (exit != 0) return "ok=0;error=SETPROP_EXIT_" + exit;
            return "ok=1;system_load_fix=" + value;
        } catch (Throwable error) {
            return "ok=0;error=SETPROP_FAILED";
        } finally { if (process != null) process.destroy(); }
    }

    private static synchronized String restartComposerWithSystemLoadCheckDisabled(boolean enabled) {
        final String desired = enabled ? "1" : "0";
        final String actual = Telemetry.systemLoadFixState();
        if (!"0".equals(actual) && !"1".equals(actual)) {
            return "ok=0;error=CPU_FIX_STATE_UNKNOWN";
        }
        if (!"running".equals(property("init.svc.vendor.qti.hardware.display.composer"))) {
            return "ok=0;error=COMPOSER_NOT_READY";
        }
        if (!"RUNNING".equals(WatcherSupervisor.health())) {
            return "ok=0;error=WATCHER_NOT_READY";
        }
        final String beforeComposerPid = composerPid();
        if ("?".equals(beforeComposerPid)) return "ok=0;error=COMPOSER_PID_UNKNOWN";
        if (desired.equals(actual) && !composerRestartFailed) {
            cpuFixDesired = enabled;
            return "ok=1;system_load_fix=" + desired + ";composer_restart=not_needed";
        }
        if (composerRestartScheduled) {
            return "ok=0;error=COMPOSER_RESTART_BUSY";
        }
        String propertyResult = setSystemLoadCheckDisabled(enabled);
        if (!propertyResult.startsWith("ok=1")) return propertyResult;
        if (!acquireTransitionWakeLock()) {
            setSystemLoadCheckDisabled("1".equals(actual));
            return "ok=0;error=WAKE_LOCK_FAILED";
        }
        cpuFixDesired = enabled;
        composerRestartFailed = false;
        composerRestartScheduled = true;
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    Thread.sleep(300L);
                    scheduleTransitionCleanupAndDaemonRestart(beforeComposerPid);
                    int exit = new ProcessBuilder("setprop", "ctl.restart",
                            "vendor.qti.hardware.display.composer").start().waitFor();
                    if (exit != 0) throw new IllegalStateException("composer restart exit=" + exit);
                    Thread.sleep(3000L);
                } catch (Throwable error) {
                    composerRestartFailed = true;
                    Log.e("ThorDisplayDaemon", "composer restart failed", error);
                    releaseTransitionWakeLock();
                } finally {
                    synchronized (D.class) { composerRestartScheduled = false; }
                }
            }
        }, "composer-restart-once").start();
        return "ok=1;system_load_fix=" + desired + ";composer_restart=scheduled_once";
    }

    private static String cpuFixPhase() {
        if (composerRestartFailed) return "ERROR";
        if (composerRestartScheduled || BootSafety.isHeld()) return "PENDING";
        String actual = Telemetry.systemLoadFixState();
        if (!"0".equals(actual) && !"1".equals(actual)) return "UNKNOWN";
        if (!"running".equals(property("init.svc.vendor.qti.hardware.display.composer")))
            return "PENDING";
        if (!"RUNNING".equals(WatcherSupervisor.health())) return "PENDING";
        return (cpuFixDesired ? "1" : "0").equals(actual) ? "CONFIRMED" : "MISMATCH";
    }

    private static boolean acquireTransitionWakeLock() {
        try {
            // The post-restart phase can need 60 seconds after helper recovery.
            // The post-restart daemon releases this early after reconciliation.
            Process process = new ProcessBuilder("sh", "-c", "echo '"
                    + TRANSITION_WAKE_LOCK + " 150000000000' > /sys/power/wake_lock").start();
            return process.waitFor() == 0;
        } catch (Throwable error) {
            Log.e("ThorDisplayDaemon", "transition wake lock failed", error);
            return false;
        }
    }

    private static void releaseTransitionWakeLock() {
        try {
            new ProcessBuilder("sh", "-c", "echo '" + TRANSITION_WAKE_LOCK
                    + "' > /sys/power/wake_unlock").start().waitFor();
        } catch (Throwable error) {
            Log.e("ThorDisplayDaemon", "transition wake unlock failed", error);
        }
    }

    private static void scheduleTransitionCleanupAndDaemonRestart(String beforeComposerPid)
            throws Exception {
        String enabled = DaemonState.isEnabled() ? "1" : "0";
        String desiredCpu = cpuFixDesired ? "1" : "0";
        // During BOOT HOLD the Hall watcher has not been enabled yet. Preserve
        // its saved preference rather than sampling the inactive watcher.
        String desiredLidGuard = (bootCoordinatorActive ? bootLidGuardDesired
                : LidGuard.isEnabled()) ? "1" : "0";
        // A compositor restart starts a second readiness phase. Do not reuse
        // the initial boot's 60-second deadline for its five-second grace.
        long phaseStartedAt = SystemClock.elapsedRealtime();
        int daemonPid = android.os.Process.myPid();
        // The vendor composer restarts SurfaceFlinger and zygote. Keep an
        // eight-second minimum, then relaunch as soon as the Android services
        // and package manager recover. The post-restart daemon releases the
        // timed wake lock only after reconciliation or safe timeout.
        String command = "sleep 8; for I in $(seq 1 10); do "
                + "[ \"$(getprop init.svc.vendor.qti.hardware.display.composer)\" = running ] "
                + "&& [ \"$(getprop init.svc.surfaceflinger)\" = running ] "
                + "&& [ \"$(getprop init.svc.zygote)\" = running ] "
                + "&& [ \"$(getprop sys.boot_completed)\" = 1 ] "
                + "&& [ -n \"$(pm path com.thor.displaypowertest 2>/dev/null)\" ] "
                + "&& break; sleep 1; done; "
                + "NEW=$(pidof vendor.qti.hardware.display.composer-service); "
                + "[ -n \"$NEW\" ] && [ \"$NEW\" != '" + beforeComposerPid
                + "' ] || exit 1; "
                + "A=''; for I in $(seq 1 30); do "
                + "A=$(pm path com.thor.displaypowertest 2>/dev/null); "
                + "[ -n \"$A\" ] && break; sleep 1; done; "
                + "A=${A#*:}; [ -n \"$A\" ] || exit 1; "
                + "kill " + daemonPid + "; sleep 1; "
                + "CLASSPATH=$A app_process / D " + enabled
                + " hold " + desiredCpu
                + " " + desiredLidGuard + " " + phaseStartedAt
                + " post"
                + " >>/data/local/tmp/td032.log 2>&1 &";
        new ProcessBuilder("sh", "-c", command).start();
    }

}
