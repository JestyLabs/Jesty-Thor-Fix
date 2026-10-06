package com.thor.displaypowertest;

/** Pure gate for the opt-in recovery-splash prototype. */
public final class RecoverySplashGateModel {
    public enum Decision {
        ARMED,
        NOT_BOOT_SCOPED,
        BOOTANIM_SUPPRESSION_INACTIVE,
        FLAG_DISABLED,
        CLASSPATH_UNUSABLE
    }

    private RecoverySplashGateModel() {}

    public static Decision decide(boolean bootScoped, boolean suppressionArmed,
            boolean flagEnabled, boolean classPathUsable) {
        if (!bootScoped) return Decision.NOT_BOOT_SCOPED;
        if (!suppressionArmed) return Decision.BOOTANIM_SUPPRESSION_INACTIVE;
        if (!flagEnabled) return Decision.FLAG_DISABLED;
        if (!classPathUsable) return Decision.CLASSPATH_UNUSABLE;
        return Decision.ARMED;
    }
}
