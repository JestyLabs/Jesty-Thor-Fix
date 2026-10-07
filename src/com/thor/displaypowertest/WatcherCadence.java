package com.thor.displaypowertest;

import android.os.SystemClock;
import android.util.Log;

/**
 * Runtime bridge around {@link WatcherCadenceModel}.
 *
 * The Settings watcher sleeps at a low idle cadence, but DisplayManager events
 * wake it immediately and open a short 20 ms burst. Event callbacks are only
 * accelerators; the fixed safety poll preserves correctness if callbacks are
 * absent or lost.
 */
public final class WatcherCadence {
    private static final String TAG = "ThorDisplayDaemon";
    private static final Object LOCK = new Object();
    private static final WatcherCadenceModel MODEL = new WatcherCadenceModel();

    private static long samples;
    private static long displayEvents;
    private static long modeChanges;
    private static long wakeRepairPulses;
    private static long drmReads;
    private static long idleWaits;
    private static long burstWaits;
    private static long lastMetricsLogAt = SystemClock.elapsedRealtime();

    private WatcherCadence() {}

    public static void noteSample() {
        String metrics = null;
        synchronized (LOCK) {
            samples++;
            long now = SystemClock.elapsedRealtime();
            if (now - lastMetricsLogAt >= 60000L) {
                metrics = metricsLocked(now);
                lastMetricsLogAt = now;
            }
        }
        if (metrics != null) Log.d(TAG, "WATCHER_METRICS " + metrics);
    }

    /** Called only for relevant TOP/BOTTOM DisplayManager callbacks. */
    public static void onDisplayEvent() {
        synchronized (LOCK) {
            displayEvents++;
            MODEL.onDisplayEvent(SystemClock.elapsedRealtime());
            LOCK.notifyAll();
        }
    }

    /** A mode edge discovered by the authoritative Settings read. */
    public static void onModeChanged() {
        synchronized (LOCK) {
            modeChanges++;
            MODEL.onModeChanged(SystemClock.elapsedRealtime());
            LOCK.notifyAll();
        }
    }

    /** A delayed wake repair must not wait for the idle safety cadence. */
    public static void onWakeRepairScheduled() {
        synchronized (LOCK) {
            wakeRepairPulses++;
            MODEL.onDisplayEvent(SystemClock.elapsedRealtime());
            LOCK.notifyAll();
        }
    }

    /**
     * Returns a generation >= 0 when the caller should take one paired DRM
     * snapshot, or -1 when the cached hardware state is still fresh enough.
     */
    public static long beginDrmRead(long lastDrmReadAtMs,
            boolean modeChanged, boolean repairDue, boolean stableTopWake) {
        synchronized (LOCK) {
            return MODEL.beginDrmRead(SystemClock.elapsedRealtime(), lastDrmReadAtMs,
                    modeChanged, repairDue, stableTopWake);
        }
    }

    /** Complete exactly the generation captured before the debugfs read. */
    public static void completeDrmRead(long generationAtStart) {
        synchronized (LOCK) {
            MODEL.completeDrmRead(generationAtStart);
            drmReads++;
        }
    }

    /**
     * Sleep until the next safety sample. A relevant display event calls
     * notifyAll(), so the watcher wakes immediately instead of waiting for the
     * idle timeout.
     */
    public static void awaitNextSample() throws InterruptedException {
        synchronized (LOCK) {
            long now = SystemClock.elapsedRealtime();
            long delay = MODEL.nextDelayMs(now);
            if (MODEL.inBurst(now)) burstWaits++;
            else idleWaits++;
            LOCK.wait(delay);
        }
    }

    /** Semicolon-prefixed fields safe to append directly to daemon Q output. */
    public static String snapshotFields() {
        synchronized (LOCK) {
            return ";" + metricsLocked(SystemClock.elapsedRealtime());
        }
    }

    private static String metricsLocked(long now) {
        return "watcher_samples=" + samples
                + ";watcher_display_events=" + displayEvents
                + ";watcher_mode_changes=" + modeChanges
                + ";watcher_repair_pulses=" + wakeRepairPulses
                + ";watcher_drm_reads=" + drmReads
                + ";watcher_idle_waits=" + idleWaits
                + ";watcher_burst_waits=" + burstWaits
                + ";watcher_burst=" + (MODEL.inBurst(now) ? "1" : "0")
                + ";watcher_poll_ms=" + MODEL.nextDelayMs(now);
    }
}
