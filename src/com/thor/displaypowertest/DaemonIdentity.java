package com.thor.displaypowertest;

/** Shared build identity for the app health check and its root daemon. */
public final class DaemonIdentity {
    /** Must remain identical to AndroidManifest versionName. */
    public static final String VERSION = "1.5.20";
    /** Isolated wire identity so this draft can safely replace a stable 1.5.20 daemon. */
    public static final String RUNTIME_ID = VERSION + "-earlycpu-p33";

    private DaemonIdentity() {}
}
