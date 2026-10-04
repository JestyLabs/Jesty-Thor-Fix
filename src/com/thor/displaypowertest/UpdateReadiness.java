package com.thor.displaypowertest;

import android.os.SystemClock;

import java.util.Map;

/**
 * Latest dashboard evidence for AppUpdater's install decision. Written by the
 * telemetry worker and lifecycle callbacks, read by the updater's worker.
 * A sample seen before a pause is not trusted; a known boot hold is kept
 * until a daemon response clears it.
 */
final class UpdateReadiness {
    private volatile UpdateVersion.DeviceState deviceState = UpdateVersion.DeviceState.UNKNOWN;
    private volatile long sampleAt;
    private volatile boolean bootHeld;
    private volatile boolean bothActive;

    /** Null means the daemon did not answer; a previously seen boot hold is kept. */
    void record(Map<String, String> values) {
        if (values != null) {
            bootHeld = "1".equals(values.get("display_actions_held"));
            bothActive = "0".equals(values.get("mode"))
                    && "1".equals(values.get("top_crtc")) && "1".equals(values.get("bottom_crtc"));
        }
        deviceState = values != null ? UpdateVersion.DeviceState.RESPONDING
                : UpdateVersion.DeviceState.UNAVAILABLE;
        sampleAt = SystemClock.elapsedRealtime();
    }

    void invalidate() {
        deviceState = UpdateVersion.DeviceState.UNKNOWN;
    }

    String installBlocker(boolean activityResumed, boolean commandInFlight) {
        UpdateVersion.DeviceState state = activityResumed
                ? deviceState : UpdateVersion.DeviceState.UNKNOWN;
        return UpdateVersion.installBlocker(state,
                SystemClock.elapsedRealtime() - sampleAt, bootHeld, commandInFlight);
    }

    String handoverNote() {
        return UpdateVersion.handoverNote(deviceState, bothActive);
    }
}
