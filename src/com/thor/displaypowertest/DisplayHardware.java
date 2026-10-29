package com.thor.displaypowertest;

import android.os.IBinder;
import android.util.Log;

import java.lang.reflect.Method;

public final class DisplayHardware {
    private static final String TAG = "ThorDisplayDaemon";

    /** Observer of lower ON calls and watcher samples (the panel-RAM bypass interceptor). */
    public interface LowerPanelListener {
        void onLowerPoweredOn(String reason);
        void onWatcherSample(boolean fixEnabled, String bottomCrtc);
        String status();
    }

    private static volatile LowerPanelListener lowerPanelListener;

    private DisplayHardware() {}

    public static void setLowerPanelListener(LowerPanelListener listener) {
        lowerPanelListener = listener;
    }

    /** Must stay cheap: the watcher calls it on every sample. */
    public static void notifyWatcherSample(boolean fixEnabled, String bottomCrtc) {
        LowerPanelListener listener = lowerPanelListener;
        if (listener == null) return;
        try { listener.onWatcherSample(fixEnabled, bottomCrtc); }
        catch (Throwable error) { Log.e(TAG, "lower panel listener failed", error); }
    }

    public static String lowerPanelStatus() {
        LowerPanelListener listener = lowerPanelListener;
        return listener == null ? "NONE" : listener.status();
    }

    public static synchronized boolean apply(boolean on, String reason) {
        String previousProperty = getProperty();
        boolean propertyChanged = false;
        try {
            Class<?> surfaceControl = Class.forName("android.view.SurfaceControl");
            Method tokenMethod = surfaceControl.getDeclaredMethod("getPhysicalDisplayToken", long.class);
            Method powerMethod = surfaceControl.getDeclaredMethod("setDisplayPowerMode", IBinder.class, int.class);
            tokenMethod.setAccessible(true);
            powerMethod.setAccessible(true);
            IBinder token = (IBinder) tokenMethod.invoke(null,
                    ThorHardwareProfile.BOTTOM_PHYSICAL_DISPLAY_ID);
            if (token == null) {
                DaemonState.setLastAction(reason + ":TOKEN_NULL");
                return false;
            }
            // Do not publish a new logical state before we know the physical
            // display exists. A missing token used to leave this property wrong.
            setProperty(on ? "1" : "0");
            propertyChanged = true;
            long callStartedNs = System.nanoTime();
            powerMethod.invoke(null, token, on ? 2 : 0);
            long callMs = (System.nanoTime() - callStartedNs) / 1000000L;
            DaemonState.setLastAction(reason + (on ? ":ON" : ":OFF"));
            Log.d(TAG, reason + (on ? " ON" : " OFF"));
            // Observation only: a normal return does not prove the compositor applied
            // the request. Record how long the synchronous call blocked and whether
            // the shared flag was rewritten by the platform before it returned.
            Log.d(TAG, "HW_CALL_OBSERVED reason=" + reason + ";target=" + (on ? "1" : "0")
                    + ";call_ms=" + callMs + ";property_after=" + getProperty());
            LowerPanelListener listener = lowerPanelListener;
            if (on && listener != null) {
                try { listener.onLowerPoweredOn(reason); }
                catch (Throwable error) { Log.e(TAG, "lower panel listener failed", error); }
            }
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

    private static void setProperty(String value) throws Exception {
        Class<?> properties = Class.forName("android.os.SystemProperties");
        Method set = properties.getDeclaredMethod("set", String.class, String.class);
        set.setAccessible(true);
        set.invoke(null, "display.power.state", value);
    }
}
