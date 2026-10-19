package android.view;
import android.os.IBinder;
import com.thor.displaypowertest.TestDisplayState;
public final class SurfaceControl {
    private static final IBinder TOKEN = new IBinder() {};
    public static IBinder getPhysicalDisplayToken(long id) { return TestDisplayState.noToken ? null : TOKEN; }
    public static void setDisplayPowerMode(IBinder token, int mode) {
        TestDisplayState.hardwareCalls++;
        if (TestDisplayState.powerFailure) throw new IllegalStateException("Synthetic platform failure");
        TestDisplayState.bottom = mode == 0 ? "0" : "1";
    }
}
