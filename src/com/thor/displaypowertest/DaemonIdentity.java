package com.thor.displaypowertest;

/** Shared build identity for the app health check and its root daemon. */
public final class DaemonIdentity {
    /** Must remain identical to AndroidManifest versionName. */
    public static final String VERSION = "1.6.0";
    /** Stable wire identity; kept equal to the packaged version for health checks. */
    public static final String RUNTIME_ID = VERSION;

    private DaemonIdentity() {}
}
