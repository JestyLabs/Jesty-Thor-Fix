package android.system;

/** Host stand-in: the request-file watch test never creates a directory. */
public final class Os {
    private Os() {}

    public static void chmod(String path, int mode) {
        throw new UnsupportedOperationException("host fixture");
    }
}
