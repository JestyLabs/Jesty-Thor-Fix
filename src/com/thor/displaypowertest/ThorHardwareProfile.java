package com.thor.displaypowertest;

/**
 * Every identifier measured on the tested AYN Thor (Android 13, firmware
 * TKQ1.231222.001). A different hardware revision or firmware must be checked
 * against all of these before any value is changed.
 *
 * DisplayEventCallback.smali repeats BOTTOM_LOGICAL_DISPLAY_ID as a literal
 * (const/4 0x4); scripts/test-boot-lid.ps1 fails if the two disagree.
 */
public final class ThorHardwareProfile {
    /** SurfaceControl physical display ID of the upper/main panel. */
    public static final long TOP_PHYSICAL_DISPLAY_ID = 0x40446d40c8d6b683L;
    /** SurfaceControl physical display ID of the lower panel. */
    public static final long BOTTOM_PHYSICAL_DISPLAY_ID = 0x40446d4a32a16584L;
    /**
     * The tested Thor exposes the upper panel's active mode in natural portrait
     * coordinates (1080x1920), while SurfaceFlinger's active layer stack is
     * landscape (1920x1080, viewport orientation 1). Recovery layers therefore
     * use swapped mode axes. This describes measured geometry only; it must not
     * be used to change the display projection.
     */
    public static final boolean TOP_LAYER_STACK_SWAPS_MODE_AXES = true;
    /** Measured upper-panel geometry on the tested Thor. */
    public static final int TOP_NATIVE_WIDTH = 1080;
    public static final int TOP_NATIVE_HEIGHT = 1920;
    public static final int TOP_RECOVERY_WIDTH = 1920;
    public static final int TOP_RECOVERY_HEIGHT = 1080;
    /** Square early-recovery curtain covers either portrait or landscape projection. */
    public static final int RECOVERY_CURTAIN_SIZE = 1920;
    /** Logical display ID of the lower panel, reported as an EXTERNAL viewport. */
    public static final int BOTTOM_LOGICAL_DISPLAY_ID = 4;
    /** DRM CRTC object IDs in the debugfs state dump. */
    public static final String TOP_CRTC_ID = "181";
    public static final String BOTTOM_CRTC_ID = "243";
    public static final String DRM_STATE_PATH = "/sys/kernel/debug/dri/0/state";
    /** Input device name of the lid Hall sensor. */
    public static final String HALL_DEVICE_NAME = "hall_switch";

    private ThorHardwareProfile() {}
}
