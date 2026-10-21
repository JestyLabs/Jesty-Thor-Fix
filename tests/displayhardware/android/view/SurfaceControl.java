package android.view;

import android.os.IBinder;
import android.os.SystemProperties;
import com.thor.displaypowertest.ThorHardwareProfile;

/** Reflection boundary only: records requests; cannot control any display. */
public final class SurfaceControl {
    public static final IBinder TOKEN = new IBinder() {};
    public static boolean missingToken;
    public static boolean powerFails;
    public static int tokens;
    public static int requests;
    public static int lastMode;
    public static String propertyAtPower;
    public static IBinder getPhysicalDisplayToken(long id) {
        if (id != ThorHardwareProfile.BOTTOM_PHYSICAL_DISPLAY_ID)
            throw new AssertionError("unexpected display ID");
        tokens++;
        return missingToken ? null : TOKEN;
    }
    public static void setDisplayPowerMode(IBinder token, int mode) {
        if (token != TOKEN) throw new AssertionError("unexpected token");
        requests++;
        lastMode = mode;
        propertyAtPower = SystemProperties.value;
        if (powerFails) throw new IllegalStateException("synthetic vendor failure");
    }
}
