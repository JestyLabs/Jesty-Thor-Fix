package com.thor.displaypowertest;

import android.os.IBinder;
import android.util.Log;

import java.lang.reflect.Method;

public final class DisplayHardware {
    private static final String TAG = "ThorDisplayDaemon";
    private static final long BOTTOM_DISPLAY_ID = 0x40446d4a32a16584L;

    private DisplayHardware() {}

    public static synchronized boolean apply(boolean on, String reason) {
        try {
            setProperty(on ? "1" : "0");
            Class<?> surfaceControl = Class.forName("android.view.SurfaceControl");
            Method tokenMethod = surfaceControl.getDeclaredMethod("getPhysicalDisplayToken", long.class);
            Method powerMethod = surfaceControl.getDeclaredMethod("setDisplayPowerMode", IBinder.class, int.class);
            tokenMethod.setAccessible(true);
            powerMethod.setAccessible(true);
            IBinder token = (IBinder) tokenMethod.invoke(null, BOTTOM_DISPLAY_ID);
            if (token == null) {
                DaemonState.setLastAction(reason + ":TOKEN_NULL");
                return false;
            }
            powerMethod.invoke(null, token, on ? 2 : 0);
            DaemonState.setLastAction(reason + (on ? ":ON" : ":OFF"));
            Log.d(TAG, reason + (on ? " ON" : " OFF"));
            return true;
        } catch (Throwable error) {
            DaemonState.setLastAction(reason + ":ERROR");
            Log.e(TAG, reason + " display transition failed", error);
            return false;
        }
    }

    public static String getProperty() {
        try {
            Class<?> properties = Class.forName("android.os.SystemProperties");
            Method get = properties.getDeclaredMethod("get", String.class);
            get.setAccessible(true);
            return String.valueOf(get.invoke(null, "display.power.state"));
        } catch (Throwable ignored) {
            return "?";
        }
    }

    private static void setProperty(String value) throws Exception {
        Class<?> properties = Class.forName("android.os.SystemProperties");
        Method set = properties.getDeclaredMethod("set", String.class, String.class);
        set.setAccessible(true);
        set.invoke(null, "display.power.state", value);
    }
}
