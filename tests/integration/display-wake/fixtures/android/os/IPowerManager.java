package android.os;
import com.thor.displaypowertest.TestDisplayState;
/** Synthetic PowerManagerService boundary (host only): reports TestDisplayState.interactive. */
public interface IPowerManager {
    boolean isInteractive();
    abstract class Stub {
        public static IPowerManager asInterface(IBinder binder) {
            return () -> { TestDisplayState.powerQueries++; return TestDisplayState.interactive; };
        }
    }
}
