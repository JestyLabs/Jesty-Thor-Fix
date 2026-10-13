package com.thor.displaypowertest;

import android.os.SystemClock;
import java.util.HashMap;
import java.util.Map;

/** Host characterization of the real updater-readiness/lifecycle adapter. */
public final class UpdateReadinessTest {
    private static final long START = 10000L;

    public static void main(String[] args) {
        SystemClock.setElapsedRealtimeForTest(START);
        UpdateReadiness state = new UpdateReadiness();
        check(state.installBlocker(true, false) != null,
                "initial UNKNOWN cannot install despite the activity being visible");

        state.record(snapshot(false, true));
        check(state.installBlocker(true, false) == null,
                "fresh, healthy BOTH sample allows installer handoff");
        check(state.installBlocker(false, false) != null,
                "paused Activity invalidates even a recently received sample");
        check(state.installBlocker(true, true) != null,
                "an in-flight command blocks even with fresh healthy telemetry");
        check(state.handoverNote().contains("30 seconds"),
                "BOTH mode uses the direct handover guidance");

        SystemClock.setElapsedRealtimeForTest(START + UpdateVersion.MAX_SAMPLE_AGE_MS);
        check(state.installBlocker(true, false) == null,
                "last permissible sample age is accepted");
        SystemClock.setElapsedRealtimeForTest(START + UpdateVersion.MAX_SAMPLE_AGE_MS + 1);
        check(state.installBlocker(true, false) != null,
                "first stale millisecond blocks the update");
        SystemClock.setElapsedRealtimeForTest(START - 1);
        check(state.installBlocker(true, false) != null,
                "clock rollback produces a negative age and blocks");
        SystemClock.setElapsedRealtimeForTest(START + 20000);
        state.invalidate();
        check(state.installBlocker(true, false) != null,
                "resuming without a new sample cannot reuse old readiness");

        state.record(snapshot(true, true));
        check(state.installBlocker(true, false) != null,
                "known BOOT_HOLD rejects the install");
        state.record(null);
        check(state.installBlocker(true, false) != null,
                "a failed daemon poll cannot erase an earlier known BOOT_HOLD");
        state.invalidate();
        state.record(null);
        check(state.installBlocker(true, false) != null,
                "pause plus an unavailable daemon still cannot clear known BOOT_HOLD");

        state.record(snapshot(false, false));
        check(state.installBlocker(true, false) == null,
                "only a fresh responding daemon explicitly clearing the hold releases it");
        check(state.handoverNote().contains("BOTH"),
                "TOP mode warns that immediate daemon handover is deferred");
        state.record(null);
        check(state.installBlocker(true, false) == null,
                "an unavailable daemon without a known hold may receive repair updates");
        check(state.installBlocker(true, true) != null,
                "in-flight actions still block when the daemon is unavailable");
        check(state.handoverNote().contains("not responding"),
                "unavailable daemon has recovery guidance");

        System.out.println("UpdateReadinessTest passed (host clock fixture)");
    }

    private static Map<String, String> snapshot(boolean hold, boolean both) {
        Map<String, String> values = new HashMap<>();
        values.put("display_actions_held", hold ? "1" : "0");
        values.put("mode", both ? "0" : "1");
        values.put("top_crtc", "1");
        values.put("bottom_crtc", both ? "1" : "0");
        return values;
    }

    private static void check(boolean accepted, String message) {
        if (!accepted) throw new AssertionError(message);
    }
}
