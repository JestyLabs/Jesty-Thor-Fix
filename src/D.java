import com.thor.displaypowertest.DaemonState;
import com.thor.displaypowertest.DaemonWatchThread;
import com.thor.displaypowertest.DisplayEventManager;
import com.thor.displaypowertest.DisplayHardware;
import com.thor.displaypowertest.Telemetry;
import com.thor.displaypowertest.WakeRepairScheduler;

import android.util.Log;

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public final class D {
    private static final String TRANSITION_WAKE_LOCK = "jesty_dashboard_cpu_transition";
    private static boolean composerRestartScheduled;
    public static void main(String[] args) throws Exception {
        boolean enabled = args.length == 0 || !"0".equals(args[0]);
        DaemonState.setEnabled(enabled);
        new DaemonWatchThread().start();
        DisplayEventManager.register();
        ServerSocket server = new ServerSocket(3804, 4, InetAddress.getByName("127.0.0.1"));
        Log.d("ThorDisplayDaemon", "READY 1.3.0 enabled=" + enabled);
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
            default: return "ok=0;error=UNKNOWN_COMMAND";
        }
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
        String command = "sleep 18; echo '" + TRANSITION_WAKE_LOCK
                + "' > /sys/power/wake_unlock 2>/dev/null; "
                + "A=''; for I in $(seq 1 30); do "
                + "A=$(pm path com.thor.displaypowertest 2>/dev/null); "
                + "[ -n \"$A\" ] && break; sleep 1; done; "
                + "A=${A#*:}; [ -n \"$A\" ] || exit 1; "
                + "kill " + daemonPid + "; sleep 1; "
                + "CLASSPATH=$A app_process / D " + enabled
                + " >>/data/local/tmp/td032.log 2>&1 &";
        new ProcessBuilder("sh", "-c", command).start();
    }

}
