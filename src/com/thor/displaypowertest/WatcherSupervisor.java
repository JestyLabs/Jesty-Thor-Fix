package com.thor.displaypowertest;

import android.os.SystemClock;
import android.util.Log;

/** Bounded recovery when the hidden Settings provider watcher exits. */
public final class WatcherSupervisor implements Runnable {
    private static volatile String health = "STARTING";
    private static volatile long lastSampleAt = -1L;

    private WatcherSupervisor() {}

    public static void start() {
        Thread thread = new Thread(new WatcherSupervisor(), "thor-watcher-supervisor");
        thread.setDaemon(true);
        thread.start();
    }

    public static void noteSample() {
        lastSampleAt = SystemClock.elapsedRealtime();
        health = "RUNNING";
    }

    public static String health() {
        if ("RUNNING".equals(health) && lastSampleAt >= 0L
                && SystemClock.elapsedRealtime() - lastSampleAt > 5000L) return "STALLED";
        return health;
    }

    public static long lastSampleAt() { return lastSampleAt; }

    @Override public void run() {
        int fastFailures = 0;
        while (fastFailures < 3) {
            health = "STARTING";
            long startedAt = SystemClock.elapsedRealtime();
            try {
                DaemonWatchThread watcher = new DaemonWatchThread();
                watcher.start();
                watcher.join();
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                health = "INTERRUPTED";
                return;
            } catch (Throwable error) {
                Log.e("ThorDisplayDaemon", "watcher launch failed", error);
            }
            health = "RETRYING";
            fastFailures = SystemClock.elapsedRealtime() - startedAt > 30000L
                    ? 1 : fastFailures + 1;
            try { Thread.sleep(1000L * fastFailures); }
            catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                health = "INTERRUPTED";
                return;
            }
        }
        health = "FAILED";
        Log.e("ThorDisplayDaemon", "watcher stopped after three rapid failures");
    }
}
