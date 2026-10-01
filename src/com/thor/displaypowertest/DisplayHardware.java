package com.thor.displaypowertest;

import android.os.IBinder;
import android.util.Log;

import java.lang.reflect.Method;

public final class DisplayHardware {
    private static final String TAG = "ThorDisplayDaemon";
    private static final long BOTTOM_DISPLAY_ID = 0x40446d4a32a16584L;

    private DisplayHardware() {}

    public static synchronized boolean apply(boolean on, String reason) {
        String previousProperty = getProperty();
        boolean propertyChanged = false;
        try {
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
            // Do not publish a new logical state before we know the physical
            // display exists. A missing token used to leave this property wrong.
            setProperty(on ? "1" : "0");
            propertyChanged = true;
            powerMethod.invoke(null, token, on ? 2 : 0);
            DaemonState.setLastAction(reason + (on ? ":ON" : ":OFF"));
            Log.d(TAG, reason + (on ? " ON" : " OFF"));
            return true;
        } catch (Throwable error) {
            if (propertyChanged && ("0".equals(previousProperty) || "1".equals(previousProperty))) {
                try { setProperty(previousProperty); }
                catch (Throwable rollbackError) {
                    Log.e(TAG, reason + " property rollback failed", rollbackError);
                }
            }
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

    /** On a failed verification, keep the logical flag aligned to observed hardware. */
    public static synchronized void alignPropertyWithCrtc(String bottomCrtc) {
        if (!"0".equals(bottomCrtc) && !"1".equals(bottomCrtc)) return;
        try { setProperty(bottomCrtc); }
        catch (Throwable error) { Log.e(TAG, "display property realignment failed", error); }
    }

    /** Records a pending TOP wake; the delayed repair performs the hardware OFF. */
    public static synchronized boolean markWakePending() {
        try {
            setProperty("0");
            return true;
        } catch (Throwable error) {
            Log.e(TAG, "could not record pending wake repair", error);
            DaemonState.setLastAction("WAKE_PENDING_PROPERTY_ERROR");
            return false;
        }
    }

    private static void setProperty(String value) throws Exception {
        Class<?> properties = Class.forName("android.os.SystemProperties");
        Method set = properties.getDeclaredMethod("set", String.class, String.class);
        set.setAccessible(true);
        set.invoke(null, "display.power.state", value);
    }
}
