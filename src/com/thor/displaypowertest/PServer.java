package com.thor.displaypowertest;

import android.os.IBinder;
import android.os.Parcel;
import android.util.Log;

import java.lang.reflect.Method;

public final class PServer {
    private static final String TAG = "ThorDisplayAuto";

    private PServer() {}

    public static boolean startDaemon() { return startDaemon(true); }

    public static boolean startDaemon(boolean enabled) {
        return startDaemon(enabled, false, false, false);
    }

    public static boolean startDaemon(boolean enabled, boolean bootHold,
            boolean dashboardFix, boolean lidGuard) {
        String state = enabled ? "1" : "0";
        String command = "echo S>/data/local/tmp/td032.log;ss -ltn|grep -q :3804||{ "
                + "A=$(pm path com.thor.displaypowertest);A=${A#*:};CLASSPATH=$A "
                + "app_process / D " + state + (bootHold ? " hold " : " run ")
                + (dashboardFix ? "1" : "0") + " " + (lidGuard ? "1" : "0")
                + " >>/data/local/tmp/td032.log 2>&1 & }";
        return send(command, "daemon start command sent enabled=" + enabled);
    }

    public static boolean stopLegacyDaemon() {
        String command = "for P in $(pidof app_process);do "
                + "tr '\\000' ' ' </proc/$P/cmdline|grep -q '^app_process / D'&&kill $P;done";
        return send(command, "legacy daemon stop command sent");
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
