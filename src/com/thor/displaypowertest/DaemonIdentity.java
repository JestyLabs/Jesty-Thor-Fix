package com.thor.displaypowertest;

/** Shared build identity for the app health check and its root daemon. */
public final class DaemonIdentity {
    /** Must remain identical to AndroidManifest versionName. */
    public static final String VERSION = "1.6.0";
    /**
     * Research-only wire identity so an in-place test can replace the v1.6.0
     * daemon without changing Android package/version semantics.
     */
    public static final String RUNTIME_ID = "1.6.0-watcher-exp1";

    private DaemonIdentity() {}
}
