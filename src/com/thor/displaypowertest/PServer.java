package com.thor.displaypowertest;

import android.os.IBinder;
import android.os.Parcel;
import android.util.Log;

import java.lang.reflect.Method;
import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.net.InetSocketAddress;
import java.net.Socket;

public final class PServer {
    private static final String TAG = "ThorDisplayAuto";

    private PServer() {}

    public static boolean startDaemon() { return startDaemon(true); }

    public static boolean startDaemon(boolean enabled) {
        return startDaemon(enabled, false, false, false);
    }

    public static boolean startDaemon(boolean enabled, boolean bootHold,
            boolean dashboardFix, boolean lidGuard) {
        return startDaemon(enabled, bootHold, dashboardFix, lidGuard,
                -1L, -1L, "?", -1L);
    }

    /** Timing fields use one compact environment value; argv identity stays fixed. */
    public static boolean startDaemon(boolean enabled, boolean bootHold,
            boolean dashboardFix, boolean lidGuard, long receiverAtMs, long serviceAtMs,
            String socketState, long launchWaitMs) {
        try {
            String command = DaemonLaunchScript.command(RootLogFiles.DAEMON_LOG,
                    enabled, bootHold, dashboardFix, lidGuard,
                    receiverAtMs, serviceAtMs, socketState, launchWaitMs);
            return send(command, "daemon launch submitted enabled=" + enabled);
        } catch (IllegalArgumentException error) {
            Log.e(TAG, "daemon launch rejected before bridge call", error);
            return false;
        }
    }

    public static boolean stopLegacyDaemon() {
        // A port occupant alone is not an identity. Require our exact legacy
        // protocol first, then a *single* root app_process with D's known
        // command line. /proc/PID/environ is not available in every vendor
        // service context after package replacement, so do not depend on it.
        if (!legacyProtocolSeen()) return false;
        String command = "PICK='';COUNT=0;for P in $(pidof app_process);do "
                + "[ \"$(stat -c %u /proc/$P)\" = 0 ]||continue;"
                + "tr '\\000' ' ' </proc/$P/cmdline|"
                + "grep -Eq '^app_process / D [01] (hold|run) [01] [01]( |$)'||continue;"
                + "PICK=$P;COUNT=$((COUNT+1));done;"
                + "[ \"$COUNT\" = 1 ]&&kill \"$PICK\"";
        return send(command, "uniquely identified legacy daemon stop submitted");
    }

    /** Replaces only known older authenticated daemons while BOTH is physically ON. */
    public static boolean stopPreviousSecureDaemonIfSafe() {
        try {
            // SocketClient checks the daemon's root UID before returning either
            // response. A claimed version or PID is never authentication alone.
            String health = SocketClient.request('I', 700);
            String snapshot = SocketClient.request('Q', 2500);
            int pid = PreviousSecureDaemonIdentity.safePid(health, snapshot);
            if (pid < 1) return false;
            return send(identifiedDaemonKill(pid),
                    "identified older secure daemon stop submitted pid=" + pid);
        } catch (Throwable error) {
            Log.e(TAG, "older daemon identity check failed", error);
            return false;
        }
    }

    /**
     * Replaces only an authenticated same-version, same-intent daemon that is
     * held after a failed compositor handover, or whose starting phase has not
     * changed for {@link DaemonLaunchModel#STUCK_PHASE_MS}. Display actions are
     * held in both cases, so no BOTH requirement applies; the successor is
     * launched in hold and runs the full boot gate.
     */
    public static boolean stopReplaceableSecureDaemonIfSafe(boolean expectedFix) {
        try {
            String health = SocketClient.request('I', 700);
            int pid = DaemonLaunchModel.replaceablePid(health, DaemonIdentity.VERSION,
                    expectedFix);
            if (pid < 1) return false;
            Log.w(TAG, "replaceable daemon: " + health);
            return send(identifiedDaemonKill(pid),
                    "identified held same-version daemon stop submitted pid=" + pid);
        } catch (Throwable error) {
            Log.e(TAG, "held daemon identity check failed", error);
            return false;
        }
    }

    private static String identifiedDaemonKill(int pid) {
        return "P=" + pid + ";"
                + "[ \"$(stat -c %u /proc/$P)\" = 0 ]||exit 1;"
                + "tr '\\000' ' ' </proc/$P/cmdline|"
                + "grep -Eq '^app_process / D [01] (hold|run) [01] [01]( |$)'||exit 1;"
                + "kill \"$P\"";
    }

    /** Read-only legacy fingerprint; never sends a power-changing command. */
    private static boolean legacyProtocolSeen() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", 3804), 300);
            socket.setSoTimeout(1500);
            socket.getOutputStream().write((byte) 'Q');
            socket.getOutputStream().flush();
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));
            String response = reader.readLine();
            return LegacyDaemonIdentity.expectedResponse(response);
        } catch (Throwable ignored) { return false; }
    }

    public static boolean healthy(boolean expectedFix) {
        try {
            return healthyResponse(SocketClient.request('I', 700), expectedFix);
        } catch (Throwable ignored) { return false; }
    }

    private static boolean healthyResponse(String response, boolean expectedFix) {
        return response.startsWith("ok=1;")
                && response.contains(";protocol=" + SecureChannel.PROTOCOL + ";")
                && response.contains(";version=" + DaemonIdentity.VERSION + ";")
                && response.contains(";pid=")
                && response.contains(";boot_phase=")
                && response.contains(";boot_phase=READY;")
                && response.contains(";fix=" + (expectedFix ? "1" : "0") + ";")
                && response.endsWith(";watcher=RUNNING");
    }

    /** One authenticated 'I' request; transport or identity failure is UNREACHABLE. */
    public static DaemonLaunchModel.Probe probe(boolean expectedFix) {
        String response;
        try {
            response = SocketClient.request('I', 700);
        } catch (Throwable ignored) {
            return DaemonLaunchModel.Probe.UNREACHABLE;
        }
        if (healthyResponse(response, expectedFix)) return DaemonLaunchModel.Probe.HEALTHY;
        if (DaemonLaunchModel.starting(response, DaemonIdentity.VERSION, expectedFix)) {
            return DaemonLaunchModel.Probe.STARTING;
        }
        return DaemonLaunchModel.Probe.UNHEALTHY;
    }

    /** Classifies the socket path only after an authenticated probe found no listener. */
    public static DaemonLaunchModel.SocketState socketState() {
        return DaemonLaunchModel.classify(secureSocketExists(), reachable(),
                SecureChannel.stampedBootId(), SecureChannel.currentBootId());
    }

    /** One diagnostic sample after bounded retries, never per poll. */
    public static void logHealthFailure() {
        try {
            Log.e(TAG, "daemon health response: " + SocketClient.request('I', 700));
        } catch (Throwable error) {
            Log.e(TAG, "daemon health transport failed", error);
        }
    }

    public static boolean secureSocketExists() {
        return new File(SecureChannel.path()).exists();
    }

    /** A degraded authenticated daemon must not be mistaken for an absent one. */
    public static boolean reachable() {
        try {
            return SocketClient.request('I', 700).startsWith("ok=1;");
        } catch (Throwable ignored) { return false; }
    }

    /** Read-only check: never treats an occupant of this port as our daemon. */
    public static boolean legacyTcpOpen() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", 3804), 200);
            return true;
        } catch (Throwable ignored) { return false; }
    }

    private static boolean send(String command, String logMessage) {
        Parcel data = null;
        Parcel reply = null;
        try {
            Class<?> manager = Class.forName("android.os.ServiceManager");
            Method getService = manager.getDeclaredMethod("getService", String.class);
            getService.setAccessible(true);
            IBinder binder = (IBinder) getService.invoke(null, "PServerBinder");
            if (binder == null) return false;
            data = Parcel.obtain();
            reply = Parcel.obtain();
            data.writeStringArray(new String[]{command, "0"});
            boolean sent = binder.transact(0, data, reply, 0);
            Log.d(TAG, logMessage);
            return sent;
        } catch (Throwable error) {
            Log.e(TAG, "daemon start failed", error);
            return false;
        } finally {
            if (data != null) data.recycle();
            if (reply != null) reply.recycle();
        }
    }
}
