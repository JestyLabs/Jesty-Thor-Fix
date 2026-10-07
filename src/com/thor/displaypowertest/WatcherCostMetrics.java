package com.thor.displaypowertest;

import android.os.Process;
import android.os.SystemClock;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Test-candidate observability for the watcher cost investigation.
 *
 * Counters only: this class never changes watcher cadence or display behavior.
 */
public final class WatcherCostMetrics {
    private static final long STARTED_AT = SystemClock.elapsedRealtime();
    private static final long STARTED_CPU_MS = Process.getElapsedCpuTime();
    private static final AtomicLong MODE_SAMPLES = new AtomicLong();
    private static final AtomicLong DRM_OPEN_ATTEMPTS = new AtomicLong();
    private static final AtomicLong DISPLAY_EVENTS = new AtomicLong();

    private WatcherCostMetrics() {}

    public static void noteModeSample() { MODE_SAMPLES.incrementAndGet(); }
    public static void noteDrmOpenAttempt() { DRM_OPEN_ATTEMPTS.incrementAndGet(); }
    public static void noteDisplayEvent() { DISPLAY_EVENTS.incrementAndGet(); }

    public static String fields() {
        long elapsed = Math.max(0L, SystemClock.elapsedRealtime() - STARTED_AT);
        long cpu = Math.max(0L, Process.getElapsedCpuTime() - STARTED_CPU_MS);
        return ";watch_elapsed_ms=" + elapsed
                + ";watch_process_cpu_ms=" + cpu
                + ";watch_mode_samples=" + MODE_SAMPLES.get()
                + ";watch_drm_open_attempts=" + DRM_OPEN_ATTEMPTS.get()
                + ";watch_display_events=" + DISPLAY_EVENTS.get();
    }
}
