package com.thor.displaypowertest;

import android.os.IBinder;
import android.os.Parcel;
import android.util.Log;

import java.lang.reflect.Method;
import java.io.File;
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

    /** A verified, narrow candidate for a same-boot replacement. No root operation here. */
    public static int safeLivePredecessor(boolean displayFix, boolean cpuFix, boolean lidGuard) {
        try {
            String health = SocketClient.request('I', 700);
            String snapshot = SocketClient.request('Q', 2500);
            return LiveHandoverPolicy.safePid(health, snapshot, displayFix, cpuFix, lidGuard);
        } catch (Throwable error) {
            Log.w(TAG, "live predecessor could not be verified");
            return -1;
        }
    }

    /** Returns only a fresh descriptor-confirmed exit receipt, never a bridge ACK. */
    public static synchronized int stopLivePredecessorIfSafe(boolean displayFix, boolean cpuFix,
            boolean lidGuard) {
        try {
            return LiveRetirement.retire(displayFix, cpuFix, lidGuard,
                    command -> send(command, "retirement helper submitted; exit unconfirmed"));
        } catch (java.io.IOException | IllegalArgumentException | SecurityException | LinkageError error) {
            Log.w(TAG, "live retirement unavailable; normal restart required", error);
            return -1;
        }
    }

    /**
     * Replaces only an authenticated same-version, same-intent daemon that is
     * held after a failed compositor handover, or whose starting phase has not
     * changed for {@link DaemonLaunchModel#STUCK_PHASE_MS}. Display actions are
     * held in both cases, so no BOTH requirement applies; the successor is
     * launched in hold and runs the full boot gate.
     */
    static synchronized boolean stopReplaceableSecureDaemonIfSafe(boolean expectedFix,
            LiveRetirement.CommitGate gate) {
        try {
            return LiveRetirement.recover(expectedFix, gate,
                    command -> send(command, "recovery helper submitted; exit unconfirmed")) > 100;
        } catch (java.io.IOException | IllegalArgumentException | SecurityException | LinkageError error) {
            Log.w(TAG, "held daemon recovery unavailable; normal restart required", error);
            return false;
        }
    }

    public static boolean healthy(boolean expectedFix) {
        try {
            return healthyResponse(SocketClient.request('I', 700), expectedFix);
        } catch (Throwable ignored) { return false; }
    }

    private static boolean healthyResponse(String response, boolean expectedFix) {
        return response.startsWith("ok=1;")
                && response.contains(";protocol=" + SecureChannel.PROTOCOL + ";")
                && response.contains(";version=" + DaemonIdentity.RUNTIME_ID + ";")
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
        if (DaemonLaunchModel.starting(response, DaemonIdentity.RUNTIME_ID, expectedFix)) {
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
        String stage = "SERVICE_CLASS";
        try {
            Class<?> manager = Class.forName("android.os.ServiceManager");
            stage = "METHOD_LOOKUP";
            Method getService = manager.getDeclaredMethod("getService", String.class);
            getService.setAccessible(true);
            stage = "SERVICE_BINDER";
            IBinder binder = (IBinder) getService.invoke(null, "PServerBinder");
            if (binder == null) {
                // Event only on a failed request, not an extra Binder probe.
                Log.w(TAG, "bridge_result=MISSING;stage=SERVICE_BINDER"
                        + ";elapsed_ms=" + android.os.SystemClock.elapsedRealtime());
                return false;
            }
            stage = "PARCEL";
            data = Parcel.obtain();
            reply = Parcel.obtain();
            data.writeStringArray(new String[]{command, "0"});
            stage = "TRANSACT";
            boolean sent = binder.transact(0, data, reply, 0);
            if (sent) {
                Log.d(TAG, logMessage);
            } else {
                Log.w(TAG, "bridge_result=TRANSACT_REJECTED;stage=TRANSACT"
                        + ";elapsed_ms=" + android.os.SystemClock.elapsedRealtime());
            }
            return sent;
        } catch (Throwable error) {
            // Never log the shell command, exception message, device identifiers,
            // or the contents of a Parcel. These codes do not imply root cause.
            Log.e(TAG, "bridge_result=" + BridgeFailureDiagnostic.failureReason(error)
                    + ";stage=" + stage
                    + ";elapsed_ms=" + android.os.SystemClock.elapsedRealtime());
            return false;
        } finally {
            if (data != null) data.recycle();
            if (reply != null) reply.recycle();
        }
    }
}
