package com.thor.displaypowertest;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/** App-only, bounded diagnostics. No shell, privileged probes or daemon commands. */
final class AppDiagnostics {
    static final int SAVE_REQUEST = 2401;
    private final Activity activity;
    private final SharedPreferences storage;
    private final AppDiagnosticReport report;
    private final PassiveSleepMonitor sleepMonitor;
    private String pendingExport;
    private boolean collecting;
    private volatile boolean closed;

    AppDiagnostics(Activity activity, String pendingExport) {
        this.activity = activity;
        storage = activity.getSharedPreferences("diagnostics", Context.MODE_PRIVATE);
        report = new AppDiagnosticReport(storage.getString("events", ""));
        sleepMonitor = new PassiveSleepMonitor(activity);
        this.pendingExport = pendingExport;
        record("APP_CREATED");
    }

    void record(String event) {
        synchronized (report) {
            if ("BACKGROUND".equals(event)) report.pauseObservations();
            storage.edit().putString("events", report.record(System.currentTimeMillis(), event)).apply();
        }
    }

    void sample(Map<String, String> values) {
        if (closed) return;
        if (values != null) {
            values = new HashMap<>(values);
            values.put("cpu_fix_desired", activity.getSharedPreferences("state", Context.MODE_PRIVATE)
                    .getBoolean(DashboardSettingsController.CPU_FIX, false) ? "1" : "0");
        }
        synchronized (report) {
            if (report.sample(System.currentTimeMillis(), SystemClock.elapsedRealtime(), values)) {
                storage.edit().putString("events", report.stored()).apply();
            }
        }
    }

    void onPause() { sleepMonitor.onPause(); }

    void onResume() { sleepMonitor.onResume(); }

    void show() {
        if (collecting || closed) return;
        collecting = true;
        new Thread(() -> {
            List<AppDiagnosticReport.Exit> exits = new ArrayList<>();
            String state = "unsupported";
            if (Build.VERSION.SDK_INT >= 30) {
                try {
                    // Only this package's Android-managed processes, not the root daemon.
                    ActivityManager manager = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
                    List<ApplicationExitInfo> entries = manager.getHistoricalProcessExitReasons(
                            activity.getPackageName(), 0, AppDiagnosticReport.MAX_EXITS);
                    if (entries != null) {
                        for (ApplicationExitInfo entry : entries) {
                            if (exits.size() == AppDiagnosticReport.MAX_EXITS) break;
                            if (entry == null) continue;
                            exits.add(new AppDiagnosticReport.Exit(entry.getTimestamp(), entry.getReason(),
                                    entry.getStatus(), entry.getImportance()));
                        }
                    }
                    state = exits.isEmpty() ? "empty" : "available";
                } catch (RuntimeException error) { state = "unavailable"; }
            }
            String version = "unknown";
            try { version = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName; }
            catch (Exception ignored) {}
            final String text = report.export(System.currentTimeMillis(), SystemClock.elapsedRealtime(),
                    version, Build.VERSION.SDK_INT, state, exits, sleepMonitor.result());
            activity.runOnUiThread(() -> {
                collecting = false;
                if (closed || activity.isFinishing() || activity.isDestroyed()) return;
                TextView body = new TextView(activity);
                int padding = (int) (16 * activity.getResources().getDisplayMetrics().density);
                body.setPadding(padding, padding, padding, padding);
                body.setText("Recent Android exit reasons and app events. This report does not measure sleep power.\n\n" + text);
                body.setTextIsSelectable(true);
                body.setTextSize(12);
                ScrollView scroll = new ScrollView(activity);
                scroll.addView(body);
                new AlertDialog.Builder(activity).setTitle("App diagnostics").setView(scroll)
                        .setNegativeButton("Close", null)
                        .setNeutralButton("Sleep trial", (dialog, which) -> showSleepTrial())
                        .setPositiveButton("Save report", (dialog, which) -> save(text)).show();
            });
        }, "app-diagnostics").start();
    }

    private void showSleepTrial() {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle("Passive sleep trial")
                .setMessage(sleepMonitor.description()
                        + "\n\nTwo snapshots at Activity pause/resume only. Leave the app, "
                        + "let the Thor sleep unplugged for at least five minutes, "
                        + "then return. Any awake time around the sleep is included. "
                        + "No timer, wakelock, sleep polling or panel power measurement.")
                .setNegativeButton("Close", null);
        if (sleepMonitor.running()) {
            builder.setPositiveButton("Cancel trial", (dialog, which) -> {
                if (!sleepMonitor.cancel()) {
                    Toast.makeText(activity, "Could not save cancellation", Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            builder.setPositiveButton("Start trial", (dialog, which) -> {
                if (sleepMonitor.arm()) {
                    Toast.makeText(activity, "Trial armed — leave the app to start", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(activity, "Could not arm trial", Toast.LENGTH_SHORT).show();
                }
            });
        }
        builder.show();
    }

    private void save(String text) {
        pendingExport = text;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, "jesty-thor-diagnostics.txt");
        try { activity.startActivityForResult(intent, SAVE_REQUEST); }
        catch (RuntimeException error) {
            pendingExport = null;
            Toast.makeText(activity, "No document picker is available", Toast.LENGTH_SHORT).show();
        }
    }

    String pendingExport() { return pendingExport; }
    void close() { closed = true; }

    void onResult(int resultCode, Intent data) {
        final String text = pendingExport;
        pendingExport = null;
        if (resultCode != Activity.RESULT_OK || text == null || data == null) return;
        final Uri uri = data.getData();
        if (uri == null || !"content".equals(uri.getScheme())) return;
        new Thread(() -> {
            boolean saved = false;
            try (OutputStream stream = activity.getContentResolver().openOutputStream(uri, "wt")) {
                if (stream != null) {
                    stream.write(text.getBytes(StandardCharsets.UTF_8));
                    saved = true;
                }
            } catch (Exception ignored) {}
            final boolean success = saved;
            activity.runOnUiThread(() -> {
                if (!closed) Toast.makeText(activity, success ? "Report saved" : "Report could not be saved",
                        Toast.LENGTH_SHORT).show();
            });
        }, "diagnostics-export").start();
    }
}
