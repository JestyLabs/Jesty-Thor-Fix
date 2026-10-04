package com.thor.displaypowertest;

import android.app.Activity;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * One authenticated 'Q' request per second, only between start() and stop().
 * A sample from an older generation (before a pause) is dropped.
 */
final class TelemetryPoller {
    interface Listener {
        /** Worker thread; values is null when the daemon did not answer. */
        void onSampleRecorded(Map<String, String> values);
        /** UI thread. */
        void onSample(Map<String, String> values);
        /** UI thread. */
        void onUnavailable();
    }

    private final Activity activity;
    private final Listener listener;
    private ScheduledExecutorService worker;
    private volatile int generation;

    TelemetryPoller(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
    }

    boolean running() { return worker != null; }

    void start() {
        if (worker != null) return;
        worker = Executors.newSingleThreadScheduledExecutor();
        generation++;
        worker.scheduleAtFixedRate(this::poll, 1L, 1L, TimeUnit.SECONDS);
    }

    void stop() {
        generation++;
        if (worker != null) worker.shutdownNow();
        worker = null;
    }

    private void poll() {
        final int current = generation;
        try {
            final Map<String, String> values = TelemetryValues.parse(SocketClient.request('Q', 2000));
            if (current != generation) return;
            listener.onSampleRecorded(values);
            activity.runOnUiThread(() -> {
                if (current == generation) listener.onSample(values);
            });
        } catch (Throwable error) {
            if (current != generation) return;
            listener.onSampleRecorded(null);
            activity.runOnUiThread(() -> {
                if (current == generation) listener.onUnavailable();
            });
        }
    }
}
