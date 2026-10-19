import com.thor.displaypowertest.LiveRetirement;

/** One-shot privileged helper; never starts the daemon or changes its boot hold. */
public final class R {
    private R() {}

    public static void main(String[] args) {
        try {
            LiveRetirement.runHelper(args);
        } catch (Exception | LinkageError error) {
            android.util.Log.w("ThorDisplayAuto", "retirement helper failed", error);
            System.exit(1);
        }
    }
}
