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
import com.thor.displaypowertest.PropertyState;
import com.thor.displaypowertest.ProcessWait;
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
    private static final String BOOT_TRACE = "/data/local/tmp/jesty-thor-boot-trace.log";
    private static final Object TRACE_LOCK = new Object();
    private static volatile boolean bootCoordinatorActive;
    private static volatile boolean bootCpuFixDesired;
    private static volatile boolean bootLidGuardDesired;
    private static volatile long bootStartedAt;
    private static volatile String bootId = "?";
    private static final java.util.concurrent.atomic.AtomicBoolean FIRST_IDENTITY_TRACE =
            new java.util.concurrent.atomic.AtomicBoolean();
    private static final long TRACE_ROTATE_BYTES = 256L * 1024L;
    public static void main(String[] args) throws Exception {
        boolean enabled = args.length == 0 || !"0".equals(args[0]);
        String currentBootId = SecureChannel.currentBootId();
        bootId = currentBootId.isEmpty() ? "?" : currentBootId;
        bootCoordinatorActive = (args.length > 1 && "hold".equals(args[1]))
                || !"1".equals(property("sys.boot_completed"));
        bootCpuFixDesired = args.length > 2 && "1".equals(args[2]);
        cpuFixDesired = bootCpuFixDesired;
        bootLidGuardDesired = args.length > 3 && "1".equals(args[3]);
        bootStartedAt = args.length > 4 ? Long.parseLong(args[4])
                : SystemClock.elapsedRealtime();
        boolean afterComposerRestart = args.length > 5 && "post".equals(args[5]);
        if (bootCoordinatorActive) {
            traceMark("DAEMON_MAIN", "launch=" + (afterComposerRestart ? "post"
                    : args.length > 1 && "hold".equals(args[1]) ? "hold" : "run")
                    + ";receiver_ms=" + envNumber("JESTY_RECEIVER_MS")
                    + ";service_ms=" + envNumber("JESTY_SERVICE_MS")
                    + ";socket=" + envToken("JESTY_SOCKET_STATE")
                    + ";launch_wait_ms=" + envNumber("JESTY_LAUNCH_WAIT_MS")
                    + ";process_start_ms=" + processStartMs());
        }
        BootSafety.begin(bootCoordinatorActive);
        DaemonState.setEnabled(enabled);
        if (!bootCoordinatorActive) LidGuard.setEnabled(bootLidGuardDesired);
        LocalServerSocket server = SecureChannel.listen();
        if (bootCoordinatorActive) traceMark("LISTEN_OK", null);
        WatcherSupervisor.start();
        DisplayEventManager.register();
        if (bootCoordinatorActive) traceMark("SERVICES_REGISTERED", null);
        ThreadPoolExecutor connections = new ThreadPoolExecutor(2, 2, 0L,
                TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(4));
        if (bootCoordinatorActive) {
            new Thread(() -> reconcileBoot(afterComposerRestart), "thor-boot-coordinator").start();
        }
        if (afterComposerRestart) {
            FIRST_IDENTITY_TRACE.set(true);
            new Thread(D::observeAndroidRecovery, "thor-android-recovery-trace").start();
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
            case 'I':
                // After a compositor restart the first identity query normally
                // comes from AutoService on Android's second BOOT_COMPLETED.
                if (FIRST_IDENTITY_TRACE.compareAndSet(true, false)) {
                    traceMark("FIRST_IDENTITY_QUERY", null);
                }
                return "ok=1;protocol=" + SecureChannel.PROTOCOL
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
        boolean handedOff = false;
        try {
            handedOff = reconcileBootPhase(afterComposerRestart);
        } finally {
            // Only a daemon that scheduled a compositor restart hands the timed
            // lock to its successor. Any other ending releases it here, so a
            // daemon that won the launch race cannot leave it to the timeout.
            synchronized (D.class) {
                if (!handedOff && !composerRestartScheduled) {
                    releaseTransitionWakeLock();
                    traceMark("WAKE_UNLOCK_SENT", "after_composer="
                            + (afterComposerRestart ? "1" : "0"));
                }
            }
        }
    }

    /** Returns true only when a compositor restart now owns the boot transition. */
    private static boolean reconcileBootPhase(boolean afterComposerRestart) {
        BootGateModel gate = new BootGateModel(bootStartedAt, afterComposerRestart);
        BootSafety.phase(afterComposerRestart ? "WAITING AFTER COMPOSER" : "WAITING FOR ANDROID");
        traceBoot(afterComposerRestart ? "WAIT_AFTER_COMPOSER" : "WAIT_FOR_ANDROID");
        Boolean lastBooted = null;
        Boolean lastComposer = null;
        Boolean lastModeKnown = null;
        Boolean lastCrtcValid = null;
        while (true) {
            long now = SystemClock.elapsedRealtime();
            boolean booted = "1".equals(property("sys.boot_completed"));
            boolean composer = "running".equals(
                    property("init.svc.vendor.qti.hardware.display.composer"));
            String mode = DaemonState.getMode();
            String[] crtc = Telemetry.crtcActivePair();
            String top = crtc[0];
            String bottom = crtc[1];
            lastBooted = gateEdge("GATE_BOOT_COMPLETED", lastBooted, booted);
            lastComposer = gateEdge("GATE_COMPOSER_RUNNING", lastComposer, composer);
            lastModeKnown = gateEdge("GATE_MODE_KNOWN", lastModeKnown,
                    BootSafety.knownMode(mode));
            lastCrtcValid = gateEdge("GATE_CRTC_VALID", lastCrtcValid,
                    binary(top) && binary(bottom));
            int previousSamples = gate.stableSamples();
            String previousCandidate = gate.candidate();
            BootGateModel.Result result = gate.observe(now, booted, composer, mode, top, bottom);
            if (result == BootGateModel.Result.TIMEOUT) {
                BootSafety.timeout();
                DaemonState.setLastAction("BOOT_SAFETY_TIMEOUT");
                Log.e("ThorDisplayDaemon", "BOOT SAFETY TIMEOUT; no display action");
                traceBoot("BOOT_SAFETY_TIMEOUT");
                return false;
            }
            int samples = gate.stableSamples();
            if (previousSamples > 0 && samples != previousSamples + 1) {
                traceMark("GATE_RESET", "previous_samples=" + previousSamples
                        + ";previous=" + safeCandidate(previousCandidate)
                        + ";candidate=" + safeCandidate(gate.candidate()));
            }
            // samples == 1 always means a new candidate, even right after another one.
            if (samples == 1 || (samples <= 3 && samples == previousSamples + 1)) {
                traceMark("GATE_STABLE_SAMPLE", "n=" + samples
                        + ";candidate=" + safeCandidate(gate.candidate()));
            }
            if (samples == 3 && previousSamples == 2) {
                traceMark("GATE_GRACE_BEGIN", "grace_ms=" + gate.graceMs());
            }
            if (result == BootGateModel.Result.READY) {
                traceMark("GATE_GRACE_END", "grace_ms=" + gate.graceMs()
                        + ";samples=" + samples);
                break;
            }
            long delay = gate.nextSampleDelayMs(now, SystemClock.elapsedRealtime());
            try { Thread.sleep(delay); } catch (InterruptedException ignored) { return false; }
        }
        BootGateModel.CpuAction cpuAction = BootGateModel.cpuAction(bootCpuFixDesired,
                Telemetry.systemLoadFixObservation(), afterComposerRestart);
        if (cpuAction == BootGateModel.CpuAction.FAIL_SAFE) {
            // A read failure or invalid CPU property must never trigger a restart.
            // It does not make a separately verified display state unsafe.
            DaemonState.setLastAction("BOOT_CPU_FIX_NOT_APPLIED");
            traceBoot("BOOT_CPU_FIX_NOT_APPLIED");
            Log.w("ThorDisplayDaemon", "CPU fix not confirmed; continuing safe display reconcile");
        }
        if (cpuAction == BootGateModel.CpuAction.RESTART_ONCE) {
            BootSafety.phase("APPLYING CPU FIX");
            traceBoot("APPLY_CPU_FIX");
            String result = restartComposerWithSystemLoadCheckDisabled(bootCpuFixDesired);
            if (result.contains("composer_restart=scheduled_once")) return true;
            BootSafety.timeout();
            DaemonState.setLastAction("BOOT_CPU_FIX_FAILED");
            Log.e("ThorDisplayDaemon", "boot CPU reconcile failed: " + result);
            traceBoot("BOOT_CPU_FIX_FAILED");
            return false;
        }
        BootSafety.phase("RECONCILING DISPLAY");
        traceBoot("RECONCILE_DISPLAY");
        String mode = DaemonState.getMode();
        if (!BootSafety.knownMode(mode)) {
            BootSafety.timeout();
            return false;
        }
        boolean bottomShouldBeOn = !DaemonState.isEnabled() || !"1".equals(mode);
        if (!DisplayActionCoordinator.reconcileBoot(mode, bottomShouldBeOn)) {
            BootSafety.timeout();
            traceBoot("BOOT_DISPLAY_FAILED");
            return false;
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
        return false;
    }

    private static Boolean gateEdge(String action, Boolean previous, boolean current) {
        if (previous == null || previous != current) {
            traceMark(action, "value=" + (current ? "1" : "0"));
        }
        return current;
    }

    private static boolean binary(String value) {
        return "0".equals(value) || "1".equals(value);
    }

    private static String safeCandidate(String candidate) {
        return candidate != null && candidate.matches("[0-2]:[01]:[01]") ? candidate : "-";
    }

    /** Device-local trace contains only phase and hardware flags, no personal data. */
    private static void traceBoot(String action) {
        String observedMode = DaemonState.getMode();
        String[] crtc = Telemetry.crtcActivePair();
        appendTrace("elapsed_ms=" + SystemClock.elapsedRealtime()
                + ";action=" + action
                + ";mode=" + (BootSafety.knownMode(observedMode) ? observedMode : "?")
                + ";top_crtc=" + crtc[0]
                + ";bottom_crtc=" + crtc[1]
                + ";cpu_fix=" + Telemetry.systemLoadFixState()
                + ";pid=" + android.os.Process.myPid()
                + ";boot_id=" + bootId
                + ";source=daemon");
    }

    /** Cheap timing mark: no debugfs read and no property fork. */
    private static void traceMark(String action, String detail) {
        appendTrace("elapsed_ms=" + SystemClock.elapsedRealtime()
                + ";action=" + action
                + (detail == null || detail.isEmpty() ? "" : ";" + detail)
                + ";pid=" + android.os.Process.myPid()
                + ";boot_id=" + bootId
                + ";source=daemon");
    }

    private static void appendTrace(String line) {
        synchronized (TRACE_LOCK) {
            File trace = new File(BOOT_TRACE);
            // Bound local storage: keep one previous generation only.
            if (trace.length() > TRACE_ROTATE_BYTES) {
                File previous = new File(BOOT_TRACE + ".1");
                if (!trace.renameTo(previous)) {
                    Log.w("ThorDisplayDaemon", "could not rotate boot trace");
                }
            }
            try (FileWriter writer = new FileWriter(trace, true)) {
                writer.write(line + "\n");
                trace.setReadable(true, false);
            } catch (Throwable error) {
                Log.w("ThorDisplayDaemon", "could not write sanitized boot trace", error);
            }
        }
    }

    private static String envNumber(String name) {
        String value = System.getenv(name);
        return value != null && value.matches("-?[0-9]{1,18}") ? value : "?";
    }

    private static String envToken(String name) {
        String value = System.getenv(name);
        return value != null && value.matches("[A-Z_]{1,32}") ? value : "?";
    }

    /** Process start on the boot clock, so it is comparable with elapsed_ms. */
    private static long processStartMs() {
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.FileReader("/proc/self/stat"))) {
            String stat = reader.readLine();
            String[] fields = stat.substring(stat.lastIndexOf(')') + 2).split(" ");
            long ticks = Long.parseLong(fields[19]);
            long hz = android.system.Os.sysconf(android.system.OsConstants._SC_CLK_TCK);
            return hz > 0 ? ticks * 1000L / hz : -1L;
        } catch (Throwable ignored) {
            return -1L;
        }
    }

    private static String property(String name) {
        Process process = null;
        try {
            process = new ProcessBuilder("getprop", name).start();
            if (!ProcessWait.exited(process, 2000L) || process.exitValue() != 0)
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
        return pidOf("vendor.qti.hardware.display.composer-service");
    }

    /** "1" when servicemanager lists the service, "0" when not, "?" on failure. */
    private static String serviceFound(String name) {
        Process process = null;
        try {
            process = new ProcessBuilder("service", "check", name).start();
            if (!ProcessWait.exited(process, 2000L) || process.exitValue() != 0)
                return "?";
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String value = reader.readLine();
                if (value == null) return "?";
                if (value.endsWith(": found")) return "1";
                return value.endsWith(": not found") ? "0" : "?";
            }
        } catch (Throwable ignored) { return "?"; }
        finally { if (process != null) process.destroy(); }
    }

    /**
     * Observability only, for the daemon launched after a compositor restart.
     * sys.boot_completed keeps its first-boot value across that restart, so it
     * cannot show when Android finishes its second visual boot. Record edges of
     * signals that do restart with the framework. Nothing here gates or changes
     * display, CPU or lid state.
     */
    private static void observeAndroidRecovery() {
        long started = SystemClock.elapsedRealtime();
        String bootanim = null;
        String systemServer = null;
        String packageService = null;
        String settingsService = null;
        boolean sawBootanimRunning = false;
        String reason = "TIMEOUT";
        while (SystemClock.elapsedRealtime() - started < 60000L) {
            String value = property("service.bootanim.exit");
            String nextBootanim = binary(value) ? value : "?";
            String nextSystemServer = pidOf("system_server");
            String nextPackage = serviceFound("package");
            String nextSettings = serviceFound("settings");
            if (!nextBootanim.equals(bootanim)) {
                traceMark("ANDROID_BOOTANIM_EXIT", "value=" + nextBootanim);
            }
            if (!nextSystemServer.equals(systemServer)) {
                traceMark("ANDROID_SYSTEM_SERVER", "pid=" + nextSystemServer);
            }
            if (!nextPackage.equals(packageService)) {
                traceMark("ANDROID_PACKAGE_SERVICE", "found=" + nextPackage);
            }
            if (!nextSettings.equals(settingsService)) {
                traceMark("ANDROID_SETTINGS_SERVICE", "found=" + nextSettings);
            }
            bootanim = nextBootanim;
            systemServer = nextSystemServer;
            packageService = nextPackage;
            settingsService = nextSettings;
            if ("0".equals(bootanim)) {
                sawBootanimRunning = true;
            } else if ("1".equals(bootanim) && sawBootanimRunning) {
                reason = "BOOTANIM_EXIT";
                break;
            }
            // Four subprocess probes per pass: keep this diagnostic watcher
            // light enough not to distort the boot timing it records.
            try { Thread.sleep(1000L); } catch (InterruptedException ignored) {
                reason = "INTERRUPTED";
                break;
            }
        }
        traceMark("ANDROID_RECOVERY_TRACE_END", "reason=" + reason);
    }

    private static String pidOf(String name) {
        Process process = null;
        try {
            process = new ProcessBuilder("pidof", name).start();
            if (!ProcessWait.exited(process, 2000L) || process.exitValue() != 0)
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
        return setSystemLoadCheckValue(enabled ? "1" : "0");
    }

    private static String setSystemLoadCheckValue(String value) {
        Process process = null;
        try {
            process = new ProcessBuilder("setprop",
                    "vendor.display.disable_system_load_check", value).start();
            if (!ProcessWait.exited(process, 2000L)) return "ok=0;error=SETPROP_TIMEOUT";
            int exit = process.exitValue();
            if (exit != 0) return "ok=0;error=SETPROP_EXIT_" + exit;
            return "ok=1;system_load_fix=" + value;
        } catch (Throwable error) {
            return "ok=0;error=SETPROP_FAILED";
        } finally { if (process != null) process.destroy(); }
    }

    private static void restoreSystemLoadCheckState(String previous) {
        // An empty value restores the unconfigured state after a failed
        // pre-restart write. Never invent a prior binary value for UNSET.
        String value = PropertyState.UNSET.equals(previous) ? "" : previous;
        String result = setSystemLoadCheckValue(value);
        if (!result.startsWith("ok=1")) {
            Log.e("ThorDisplayDaemon", "could not restore CPU property: " + result);
        }
    }

    private static synchronized String restartComposerWithSystemLoadCheckDisabled(boolean enabled) {
        final String desired = enabled ? "1" : "0";
        final String actual = Telemetry.systemLoadFixObservation();
        boolean unsetEnable = enabled && PropertyState.UNSET.equals(actual);
        if (!unsetEnable && !"0".equals(actual) && !"1".equals(actual)) {
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
        if (!acquireTransitionWakeLock()) {
            return "ok=0;error=WAKE_LOCK_FAILED";
        }
        final boolean bootTrace = bootCoordinatorActive;
        String propertyResult = setSystemLoadCheckDisabled(enabled);
        if (!propertyResult.startsWith("ok=1")) {
            releaseTransitionWakeLock();
            return propertyResult;
        }
        if (bootTrace) traceMark("CPU_PROP_WRITTEN", "value=" + desired);
        if (!desired.equals(Telemetry.systemLoadFixObservation())) {
            restoreSystemLoadCheckState(actual);
            releaseTransitionWakeLock();
            return "ok=0;error=SETPROP_UNCONFIRMED";
        }
        if (bootTrace) traceMark("CPU_PROP_VERIFIED", "value=" + desired);
        cpuFixDesired = enabled;
        composerRestartFailed = false;
        composerRestartScheduled = true;
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    Thread.sleep(300L);
                    scheduleTransitionCleanupAndDaemonRestart(beforeComposerPid);
                    if (bootTrace) traceMark("HELPER_SCHEDULED",
                            "old_composer=" + beforeComposerPid);
                    if (bootTrace) traceMark("CTL_RESTART_SENT", null);
                    int exit = new ProcessBuilder("setprop", "ctl.restart",
                            "vendor.qti.hardware.display.composer").start().waitFor();
                    if (bootTrace) traceMark("CTL_RESTART_ACK", "exit=" + exit);
                    if (exit != 0) throw new IllegalStateException("composer restart exit=" + exit);
                    Thread.sleep(3000L);
                } catch (Throwable error) {
                    composerRestartFailed = true;
                    if (beforeComposerPid.equals(composerPid())) {
                        restoreSystemLoadCheckState(actual);
                    }
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
        // Timing marks use /proc/uptime (boot clock, 10 ms resolution) in
        // centiseconds: mksh arithmetic is 32-bit, so milliseconds are only
        // formatted, never computed.
        String command = String.join("\n",
                "exec </dev/null >>/data/local/tmp/td032.log 2>&1",
                "TR=" + BOOT_TRACE,
                "BID=$(cat /proc/sys/kernel/random/boot_id 2>/dev/null)",
                "cs(){ read U X </proc/uptime; S=${U%.*}; F=${U#*.}; F=${F#0};"
                        + " echo $((S*100+${F:-0})); }",
                "T(){ echo \"elapsed_ms=$(cs)0;action=$1;${2:+$2;}pid=$$;"
                        + "boot_id=${BID:-?};source=helper\" >>$TR; }",
                "OLD='" + beforeComposerPid + "'",
                "DP=" + daemonPid,
                "SF0=$(pidof surfaceflinger); Z0=$(pidof zygote64); SS0=$(pidof system_server)",
                "T HELPER_START \"old_composer=$OLD;old_sf=${SF0:--};old_zygote=${Z0:--};"
                        + "old_system_server=${SS0:--};daemon_pid=$DP\"",
                "B=$(cs); N=0; NC=''; NS=''; NZ=''; NSS=''; PKS=''; STS=''",
                "while [ $(($(cs)-B)) -lt 800 ] && [ $N -lt 60 ]; do",
                "  P=$(pidof vendor.qti.hardware.display.composer-service)",
                "  if [ -z \"$NC\" ] && [ -n \"$P\" ] && [ \"$P\" != \"$OLD\" ]; then"
                        + " NC=$P; T HELPER_COMPOSER_NEW_PID \"composer_pid=$P\"; fi",
                "  P=$(pidof surfaceflinger)",
                "  if [ -z \"$NS\" ] && [ -n \"$P\" ] && [ \"$P\" != \"$SF0\" ]; then"
                        + " NS=$P; T HELPER_SF_NEW_PID \"sf_pid=$P\"; fi",
                "  P=$(pidof zygote64)",
                "  if [ -z \"$NZ\" ] && [ -n \"$P\" ] && [ \"$P\" != \"$Z0\" ]; then"
                        + " NZ=$P; T HELPER_ZYGOTE_NEW_PID \"zygote_pid=$P\"; fi",
                // Observability only: service readiness of the new system_server
                // shows how much of the eight-second floor is actually needed.
                "  P=$(pidof system_server)",
                "  if [ -z \"$NSS\" ] && [ -n \"$P\" ] && [ \"$P\" != \"$SS0\" ]; then"
                        + " NSS=$P; T HELPER_SYSTEM_SERVER_NEW_PID \"system_server_pid=$P\"; fi",
                "  if [ -n \"$NSS\" ] && [ -z \"$PKS\" ]"
                        + " && service check package 2>/dev/null | grep -q ': found$'; then"
                        + " PKS=1; T HELPER_PACKAGE_SERVICE_FOUND; fi",
                "  if [ -n \"$NSS\" ] && [ -z \"$STS\" ]"
                        + " && service check settings 2>/dev/null | grep -q ': found$'; then"
                        + " STS=1; T HELPER_SETTINGS_SERVICE_FOUND; fi",
                "  N=$((N+1)); sleep 0.25",
                "done",
                // The eight-second floor holds even if fractional sleep is unsupported.
                "R=$((800-($(cs)-B))); [ $R -gt 0 ] && sleep $(((R+99)/100))",
                "T HELPER_FLOOR_DONE \"polls=$N\"",
                "A=''",
                "for I in $(seq 1 10); do",
                "  [ \"$(getprop init.svc.vendor.qti.hardware.display.composer)\" = running ]"
                        + " && [ \"$(getprop init.svc.surfaceflinger)\" = running ]"
                        + " && [ \"$(getprop init.svc.zygote)\" = running ]"
                        + " && [ \"$(getprop sys.boot_completed)\" = 1 ]"
                        + " && A=$(pm path com.thor.displaypowertest 2>/dev/null)"
                        + " && [ -n \"$A\" ] && break",
                "  A=''; sleep 1",
                "done",
                "[ -n \"$A\" ] && PM=1 || PM=0",
                "T HELPER_SERVICES_CHECKED \"attempts=$I;pm=$PM\"",
                "NEW=$(pidof vendor.qti.hardware.display.composer-service)",
                "if [ -z \"$NEW\" ] || [ \"$NEW\" = \"$OLD\" ]; then"
                        + " T HELPER_ABORT reason=COMPOSER_NOT_RESTARTED; exit 1; fi",
                "if [ -z \"$A\" ]; then for I in $(seq 1 30); do"
                        + " A=$(pm path com.thor.displaypowertest 2>/dev/null);"
                        + " [ -n \"$A\" ] && break; sleep 1; done; fi",
                "A=${A#*:}",
                "if [ -z \"$A\" ]; then T HELPER_ABORT reason=PACKAGE_PATH; exit 1; fi",
                "T HELPER_PM_READY \"composer_pid=$NEW\"",
                "state(){ [ ! -d /proc/$DP ] && { echo GONE; return; };"
                        + " S=$(sed -n 's/^State:[[:space:]]*\\([A-Z]\\).*/\\1/p'"
                        + " /proc/$DP/status 2>/dev/null);"
                        + " if [ -z \"$S\" ]; then"
                        + " if [ -d /proc/$DP ]; then echo UNKNOWN; else echo GONE; fi;"
                        + " elif [ \"$S\" = Z ]; then echo GONE;"
                        + " else echo LIVE; fi; }",
                // Only signal the PID if it is still this root daemon, then wait
                // for its exit so the successor's instance lock cannot lose.
                // A failed identity read is re-classified: a daemon that has
                // just exited is gone, anything still present is a mismatch.
                "ident(){ [ \"$(stat -c %u /proc/$DP 2>/dev/null)\" = 0 ]"
                        + " && tr '\\000' ' ' </proc/$DP/cmdline 2>/dev/null"
                        + " | grep -Eq '^app_process / D [01] (hold|run) [01] [01]( |$)'"
                        + " && { echo OK; return; };"
                        + " [ \"$(state)\" = GONE ] && echo GONE || echo BAD; }",
                "PSTATE=$(state)",
                "if [ \"$PSTATE\" = UNKNOWN ]; then"
                        + " T HELPER_ABORT reason=DAEMON_IDENTITY_MISMATCH; exit 1; fi",
                "[ \"$PSTATE\" = LIVE ] && PSTATE=$(ident)",
                "if [ \"$PSTATE\" = BAD ]; then"
                        + " T HELPER_ABORT reason=DAEMON_IDENTITY_MISMATCH; exit 1; fi",
                "if [ \"$PSTATE\" = OK ]; then",
                // A daemon that exits between the checks and the signal is
                // not a failure; only a live process that rejects it is.
                "  if kill $DP 2>/dev/null; then T HELPER_KILL_SENT \"daemon_pid=$DP\";"
                        + " elif [ \"$(state)\" = GONE ]; then"
                        + " T HELPER_KILL_RACE_GONE \"daemon_pid=$DP\";"
                        + " else T HELPER_ABORT reason=KILL_FAILED; exit 1; fi",
                "  I=0; while [ \"$(state)\" != GONE ] && [ $I -lt 50 ];"
                        + " do sleep 0.1; I=$((I+1)); done",
                "  [ \"$(state)\" != GONE ] && sleep 1",
                "  if [ \"$(state)\" != GONE ]; then"
                        + " T HELPER_ABORT reason=OLD_DAEMON_ALIVE; exit 1; fi",
                "  T HELPER_OLD_EXITED \"polls=$I\"",
                "else",
                "  T HELPER_OLD_GONE \"daemon_pid=$DP\"",
                "fi",
                "T HELPER_EXEC_NEW \"composer_pid=$NEW\"",
                "CLASSPATH=$A app_process / D " + enabled
                        + " hold " + desiredCpu
                        + " " + desiredLidGuard + " " + phaseStartedAt
                        + " post &");
        ProcessBuilder helper = new ProcessBuilder("sh", "-c", command);
        // Launch timing belongs to this daemon only; never pass it to the successor.
        helper.environment().keySet().removeIf(name -> name.startsWith("JESTY_"));
        helper.start();
    }

}
