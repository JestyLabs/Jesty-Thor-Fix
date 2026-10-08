package com.thor.displaypowertest;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Explicitly user-invoked local support view; nothing exported automatically. */
final class EventHistoryDialog {
    private EventHistoryDialog() {}

    static void show(Activity activity, EventHistoryJournal journal) {
        List<EventHistoryModel.Entry> entries = journal.entries();
        String text = format(entries);

        TextView lines = new TextView(activity);
        lines.setText(text);
        lines.setTextSize(12f);
        lines.setTextColor(Color.WHITE);
        int pad = Math.round(16f * activity.getResources().getDisplayMetrics().density);
        lines.setPadding(pad, pad, pad, pad);
        lines.setTextIsSelectable(true);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(lines, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(activity)
                .setTitle("RECENT EVENTS (" + entries.size() + "/" + EventHistoryModel.MAX_EVENTS + ")")
                .setMessage("Local app-side observations while the dashboard is open. "
                        + "A CRTC observation is not a physical panel measurement.")
                .setView(scroll)
                .setPositiveButton("COPY", (dialog, button) -> {
                    ClipboardManager clipboard = (ClipboardManager)
                            activity.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(ClipData.newPlainText("Thor Fix events", text));
                        Toast.makeText(activity, "Events copied", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNeutralButton("CLEAR", (dialog, button) -> new AlertDialog.Builder(activity)
                        .setTitle("Clear recent events?")
                        .setMessage("Delete the local event history on this device?")
                        .setNegativeButton("CANCEL", null)
                        .setPositiveButton("CLEAR", (confirm, which) -> {
                            journal.clear();
                            Toast.makeText(activity, "Event history cleared", Toast.LENGTH_SHORT).show();
                        })
                        .show())
                .setNegativeButton("CLOSE", null)
                .show();
    }

    static String format(List<EventHistoryModel.Entry> entries) {
        StringBuilder out = new StringBuilder("Jesty Thor Fix | Local event history\n")
                .append("App telemetry only; no physical display verification\n\n");
        if (entries.isEmpty()) return out.append("No events recorded yet.\n").toString();
        SimpleDateFormat stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        for (int i = entries.size() - 1; i >= 0; i--) {
            EventHistoryModel.Entry entry = entries.get(i);
            out.append(stamp.format(new Date(entry.timeMillis))).append("  ")
                    .append(describe(entry)).append('\n');
        }
        return out.toString();
    }

    private static String describe(EventHistoryModel.Entry event) {
        switch (event.type) {
            case DAEMON_CONNECTED: return "Daemon telemetry connected";
            case DAEMON_UNAVAILABLE: return "Daemon telemetry unavailable";
            case BOOT_HOLD_ENTERED: return "Boot safety hold observed";
            case BOOT_HOLD_CLEARED: return "Boot safety hold cleared";
            case WATCHER_RUNNING: return "Watcher reports RUNNING";
            case WATCHER_NOT_RUNNING: return "Watcher not RUNNING";
            case WAKE_OBSERVED: return "Wake counter advanced";
            case REPAIR_STATUS: return "Wake repair status: " + event.detail;
            case CPU_PHASE: return "CPU Fix phase: " + event.detail;
            case DISPLAY_OBSERVED:
                return "Observed display " + event.detail.replace('_', ' ');
            default: return "Diagnostic event";
        }
    }
}
