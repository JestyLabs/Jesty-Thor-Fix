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
    /** SurfaceControl physical display ID of the lower panel. */
    public static final long BOTTOM_PHYSICAL_DISPLAY_ID = 0x40446d4a32a16584L;
    /** Logical display ID of the lower panel, reported as an EXTERNAL viewport. */
    public static final int BOTTOM_LOGICAL_DISPLAY_ID = 4;
    /** DRM CRTC object IDs in the debugfs state dump. */
    public static final String TOP_CRTC_ID = "181";
    public static final String BOTTOM_CRTC_ID = "243";
    public static final String DRM_STATE_PATH = "/sys/kernel/debug/dri/0/state";
    /** Input device name of the lid Hall sensor. */
    public static final String HALL_DEVICE_NAME = "hall_switch";
    /**
     * Vendor panel-RAM bypass attribute. The framework writes it on every refresh
     * change (0 at 110 Hz and above, 1 below) through the PServer root shell; a write
     * while the lower panel is off leaves its DSI link unable to start until reboot.
     */
    public static final String BYPASS_RAM_NODE =
            "/sys/devices/virtual/bypass_ram_class/bypass_ram_device/bypass_ram";
    /** DRM connector power state of the lower panel ("On" or "Off"). */
    public static final String BOTTOM_DPMS_PATH = "/sys/class/drm/card0-DSI-2/dpms";

    private ThorHardwareProfile() {}
}
