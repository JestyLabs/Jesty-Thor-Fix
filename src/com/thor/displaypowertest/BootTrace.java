package com.thor.displaypowertest;

import android.os.SystemClock;
import android.util.Log;

/**
 * Sanitized device-local boot trace. Lines contain only phase names, hardware
 * flags, PIDs and the kernel boot ID; never settings values or personal data.
 */
public final class BootTrace {
    public static final String PATH = RootLogFiles.BOOT_TRACE;
    private static final long ROTATE_BYTES = 256L * 1024L;

    private final Object lock = new Object();
    private final String bootId;
    private final int pid;

    public BootTrace(String bootId, int pid) {
        this.bootId = bootId == null || bootId.isEmpty() ? "?" : bootId;
        this.pid = pid;
    }

    /** Full sample: one debugfs read and one CPU property read. */
    public void boot(String action) {
        String observedMode = DaemonState.getMode();
        String[] crtc = Telemetry.crtcActivePair();
        append("elapsed_ms=" + SystemClock.elapsedRealtime()
                + ";action=" + action
                + ";mode=" + (BootSafety.knownMode(observedMode) ? observedMode : "?")
                + ";top_crtc=" + crtc[0]
                + ";bottom_crtc=" + crtc[1]
                + ";cpu_fix=" + Telemetry.systemLoadFixState()
                + ";pid=" + pid
                + ";boot_id=" + bootId
                + ";source=daemon");
    }

    /** Cheap timing mark: no debugfs read and no property fork. */
    public void mark(String action, String detail) {
        append("elapsed_ms=" + SystemClock.elapsedRealtime()
                + ";action=" + action
                + (detail == null || detail.isEmpty() ? "" : ";" + detail)
                + ";pid=" + pid
                + ";boot_id=" + bootId
                + ";source=daemon");
    }

    private void append(String line) {
        synchronized (lock) {
            // Bound local storage: keep one previous generation only. The
            // directory is shell-writable, so never follow a planted link.
            RootLogFiles.rotateIfLarger(PATH, ROTATE_BYTES);
            if (!RootLogFiles.append(PATH, line + "\n")) {
                Log.w("ThorDisplayDaemon", "sanitized boot trace not written");
            }
        }
    }
}
