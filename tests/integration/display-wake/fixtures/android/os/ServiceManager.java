package android.os;
import com.thor.displaypowertest.TestDisplayState;
/** Synthetic service lookup boundary (host only). */
public final class ServiceManager {
    private static final IBinder POWER = new IBinder() {};
    public static IBinder getService(String name) {
        return "power".equals(name) && !TestDisplayState.powerServiceMissing ? POWER : null;
    }
}
