import com.thor.displaypowertest.DaemonState;
import com.thor.displaypowertest.BootGateModel;
import com.thor.displaypowertest.BootSafety;
import com.thor.displaypowertest.DaemonWatchThread;
import com.thor.displaypowertest.DisplayEventManager;
import com.thor.displaypowertest.DisplayHardware;
import com.thor.displaypowertest.LidGuard;
import com.thor.displaypowertest.Telemetry;
import com.thor.displaypowertest.WakeRepairScheduler;

import android.util.Log;
import android.os.SystemClock;

import java.io.OutputStream;
import java.io.FileWriter;
import java.io.File;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public final class D {
    private static final String TRANSITION_WAKE_LOCK = "jesty_dashboard_cpu_transition";
    private static boolean composerRestartScheduled;
    private static boolean bootCoordinatorActive;
    private static boolean bootCpuFixDesired;
    private static boolean bootLidGuardDesired;
    private static long bootStartedAt;
    public static void main(String[] args) throws Exception {
        boolean enabled = args.length == 0 || !"0".equals(args[0]);
        bootCoordinatorActive = (args.length > 1 && "hold".equals(args[1]))
                || !"1".equals(property("sys.boot_completed"));
        bootCpuFixDesired = args.length > 2 && "1".equals(args[2]);
        bootLidGuardDesired = args.length > 3 && "1".equals(args[3]);
        bootStartedAt = args.length > 4 ? Long.parseLong(args[4])
                : SystemClock.elapsedRealtime();
        boolean afterComposerRestart = args.length > 5 && "post".equals(args[5]);
        BootSafety.begin(bootCoordinatorActive);
        DaemonState.setEnabled(enabled);
        if (!bootCoordinatorActive) LidGuard.setEnabled(bootLidGuardDesired);
        new DaemonWatchThread().start();
        DisplayEventManager.register();
        ServerSocket server = new ServerSocket(3804, 4, InetAddress.getByName("127.0.0.1"));
        if (bootCoordinatorActive) {
            new Thread(() -> reconcileBoot(afterComposerRestart), "thor-boot-coordinator").start();
        }
        Log.d("ThorDisplayDaemon", "READY 1.4.2 enabled=" + enabled
                + " bootHold=" + bootCoordinatorActive);
        while (true) {
            Socket socket = server.accept();
            try {
                int command = socket.getInputStream().read();
                String response = handle((char) command);
                OutputStream output = socket.getOutputStream();
                output.write((response + "\n").getBytes(StandardCharsets.UTF_8));
                output.flush();
            } catch (Throwable error) {
                Log.e("ThorDisplayDaemon", "command failed", error);
            } finally {
                socket.close();
            }
        }
    }

    private static String handle(char command) {
        if (BootSafety.isHeld() && command != 'Q' && command != 'V') {
            return "ok=0;error=BOOT_HOLD;boot_phase="
                    + BootSafety.phase().replace(' ', '_');
        }
        switch (command) {
            case '0':
                WakeRepairScheduler.cancel();
                return DisplayHardware.apply(false, "LEGACY") ? "ok=1" : "ok=0;error=OFF_FAILED";
            case '1':
                WakeRepairScheduler.cancel();
                return DisplayHardware.apply(true, "LEGACY") ? "ok=1" : "ok=0;error=ON_FAILED";
            case 'E':
                DaemonState.setEnabled(true);
                WakeRepairScheduler.cancel();
                boolean top = "1".equals(DaemonState.getMode());
                boolean enabledOk = DisplayHardware.apply(!top, "ENABLE_RECONCILE");
                return enabledOk ? "ok=1;fix=1" : "ok=0;error=ENABLE_FAILED";
            case 'N':
                DaemonState.setEnabled(false);
                WakeRepairScheduler.cancel();
                final boolean nativeOk = DisplayHardware.apply(true, "NATIVE_IMMEDIATE");
                new Thread(new Runnable() {
                    @Override public void run() {
                        try { Thread.sleep(350L); } catch (InterruptedException ignored) {}
                        if (!DaemonState.isEnabled()) DisplayHardware.apply(true, "NATIVE_FINAL");
                    }
                }, "native-final").start();
                return nativeOk ? "ok=1;fix=0" : "ok=0;error=NATIVE_FAILED";
            case 'Q': return DaemonState.snapshot();
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
                BootSafety.timeout();
                DaemonState.setLastAction("BOOT_CPU_FIX_NOT_APPLIED");
                traceBoot("BOOT_CPU_FIX_NOT_APPLIED");
                return;
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
        String bottom = Telemetry.bottomCrtcActive();
        if (!(bottomShouldBeOn ? "1" : "0").equals(bottom)) {
            if (!DisplayHardware.apply(bottomShouldBeOn, "BOOT_RECONCILE")) {
                BootSafety.timeout();
                traceBoot("BOOT_DISPLAY_FAILED");
                return;
            }
            traceBoot(bottomShouldBeOn ? "BOTTOM_ON" : "BOTTOM_OFF");
        }
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
        try {
            Process process = new ProcessBuilder("getprop", name).start();
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()));
            String value = reader.readLine();
            reader.close();
            process.waitFor();
            return value == null ? "" : value.trim();
        } catch (Throwable ignored) { return ""; }
    }

    private static String setSystemLoadCheckDisabled(boolean enabled) {
        String value = enabled ? "1" : "0";
        try {
            Process process = new ProcessBuilder("setprop",
                    "vendor.display.disable_system_load_check", value).start();
            int exit = process.waitFor();
            if (exit != 0) return "ok=0;error=SETPROP_EXIT_" + exit;
            return "ok=1;system_load_fix=" + value;
        } catch (Throwable error) {
            return "ok=0;error=SETPROP_FAILED";
        }
    }

    private static synchronized String restartComposerWithSystemLoadCheckDisabled(boolean enabled) {
        final String desired = enabled ? "1" : "0";
        if (desired.equals(Telemetry.systemLoadFixState())) {
            return "ok=1;system_load_fix=" + desired + ";composer_restart=not_needed";
        }
        if (composerRestartScheduled) {
            return "ok=0;error=COMPOSER_RESTART_BUSY";
        }
        String propertyResult = setSystemLoadCheckDisabled(enabled);
        if (!propertyResult.startsWith("ok=1")) return propertyResult;
        if (!acquireTransitionWakeLock()) {
            setSystemLoadCheckDisabled(!enabled);
            return "ok=0;error=WAKE_LOCK_FAILED";
        }
        composerRestartScheduled = true;
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    Thread.sleep(300L);
                    scheduleTransitionCleanupAndDaemonRestart();
                    int exit = new ProcessBuilder("setprop", "ctl.restart",
                            "vendor.qti.hardware.display.composer").start().waitFor();
                    if (exit != 0) throw new IllegalStateException("composer restart exit=" + exit);
                    Thread.sleep(3000L);
                } catch (Throwable error) {
                    Log.e("ThorDisplayDaemon", "composer restart failed", error);
                    releaseTransitionWakeLock();
                } finally {
                    synchronized (D.class) { composerRestartScheduled = false; }
                }
            }
        }, "composer-restart-once").start();
        return "ok=1;system_load_fix=" + desired + ";composer_restart=scheduled_once";
    }

    private static boolean acquireTransitionWakeLock() {
        try {
            // Kernel timeout is a safety net; the surviving helper releases it
            // explicitly as soon as Android's display stack has recovered.
            Process process = new ProcessBuilder("sh", "-c", "echo '"
                    + TRANSITION_WAKE_LOCK + " 90000000000' > /sys/power/wake_lock").start();
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

    private static void scheduleTransitionCleanupAndDaemonRestart() throws Exception {
        String enabled = DaemonState.isEnabled() ? "1" : "0";
        int daemonPid = android.os.Process.myPid();
        // The vendor composer restarts SurfaceFlinger and zygote. Keep an
        // eight-second minimum, then relaunch as soon as the Android services
        // and package manager recover. Keep the original 18-second wake-lock
        // coverage independently; the post-restart BootGate still requires
        // stable CRTCs + 5 s before any display action.
        String command = "(sleep 18; echo '" + TRANSITION_WAKE_LOCK
                + "' > /sys/power/wake_unlock 2>/dev/null) & "
                + "sleep 8; for I in $(seq 1 10); do "
                + "[ \"$(getprop init.svc.vendor.qti.hardware.display.composer)\" = running ] "
                + "&& [ \"$(getprop init.svc.surfaceflinger)\" = running ] "
                + "&& [ \"$(getprop init.svc.zygote)\" = running ] "
                + "&& [ \"$(getprop sys.boot_completed)\" = 1 ] "
                + "&& [ -n \"$(pm path com.thor.displaypowertest 2>/dev/null)\" ] "
                + "&& break; sleep 1; done; "
                + "A=''; for I in $(seq 1 30); do "
                + "A=$(pm path com.thor.displaypowertest 2>/dev/null); "
                + "[ -n \"$A\" ] && break; sleep 1; done; "
                + "A=${A#*:}; [ -n \"$A\" ] || exit 1; "
                + "kill " + daemonPid + "; sleep 1; "
                + "CLASSPATH=$A app_process / D " + enabled
                + (bootCoordinatorActive ? " hold " + (bootCpuFixDesired ? "1" : "0")
                    + " " + (LidGuard.isEnabled() ? "1" : "0") + " " + bootStartedAt
                    + " post" : " run 0 " + (LidGuard.isEnabled() ? "1" : "0"))
                + " >>/data/local/tmp/td032.log 2>&1 &";
        new ProcessBuilder("sh", "-c", command).start();
    }

}
