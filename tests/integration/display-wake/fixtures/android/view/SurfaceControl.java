package android.view;
import android.os.IBinder;
import com.thor.displaypowertest.TestDisplayState;
public final class SurfaceControl {
    private static final IBinder TOKEN = new IBinder() {};
    public static IBinder getPhysicalDisplayToken(long id) { return TestDisplayState.noToken ? null : TOKEN; }
    public static void setDisplayPowerMode(IBinder token, int mode) {
        TestDisplayState.hardwareCalls++;
        android.os.SystemClock.now += TestDisplayState.callAdvanceMs;
        if (TestDisplayState.powerFailure) throw new IllegalStateException("Synthetic platform failure");
        // Synthetic platform stall (all default-off): the call blocks, the platform wake
        // path rewrites the shared flag meanwhile, then the archived compositor gate
        // (setPowerModeInternal: non-primary OFF skipped when flag == 1) is evaluated.
        if (TestDisplayState.callSleepMs > 0) {
            try { Thread.sleep(TestDisplayState.callSleepMs); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); }
        }
        if (TestDisplayState.modeDuringCall != null) TestDisplayState.mode = TestDisplayState.modeDuringCall;
        if (TestDisplayState.platformPropertyDuringCall != null)
            TestDisplayState.property = TestDisplayState.platformPropertyDuringCall;
        if (TestDisplayState.vendorGate && mode == 0 && "1".equals(TestDisplayState.property)) {
            TestDisplayState.gateRefusals++;
            return;
        }
        TestDisplayState.bottom = mode == 0 ? "0" : "1";
    }
}
