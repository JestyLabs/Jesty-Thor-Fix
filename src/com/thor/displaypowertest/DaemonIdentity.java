package com.thor.displaypowertest;

/** Shared build identity for the app health check and its root daemon. */
public final class DaemonIdentity {
    /** Must remain identical to AndroidManifest versionName. */
    public static final String VERSION = "1.7.0";
    /** Production wire identity follows the Android package version. */
    public static final String RUNTIME_ID = VERSION;

    private DaemonIdentity() {}
}
