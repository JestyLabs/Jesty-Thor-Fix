package com.thor.displaypowertest;

import java.util.ArrayList;
import java.util.List;

/**
 * On-demand, read-only human presentation of the already sanitized V1 report.
 * No daemon access, Activity context, extra Q requests, disk writes or timers.
 * The saved/exported report keeps its original machine-readable schema.
 */
public final class AppDiagnosticPreview {
    private AppDiagnosticPreview() {}

    public static String format(String report) {
        if (report == null || report.length() > AppDiagnosticReport.MAX_REPORT_CHARS
                || !report.startsWith("JESTY_THOR_DIAGNOSTICS_V1\n")) {
            return "No valid diagnostic report is available.";
        }
        String version = "unknown", sdk = "unknown", telemetry = null, history = "unavailable";
        List<String> exits = new ArrayList<>();
        List<String> events = new ArrayList<>();
        String[] lines = report.split("\n", -1);
        for (int i = 1; i < lines.length && i < 128; i++) {
            String line = lines[i];
            if (line.startsWith("app_version=")) {
                version = field(line, "app_version");
                sdk = field(line, "sdk");
            } else if (line.startsWith("telemetry_scope=")) {
                telemetry = line;
            } else if (line.startsWith("exit_history_state=")) {
                history = field(line, "exit_history_state");
            } else if (line.startsWith("exit;") && exits.size() < AppDiagnosticReport.MAX_EXITS) {
                exits.add(line);
            } else if (line.startsWith("event;") && events.size() < AppDiagnosticReport.MAX_EVENTS) {
                events.add(line);
            }
        }

        StringBuilder out = new StringBuilder();
        out.append("JESTY THOR FIX  ·  DIAGNOSTICS\n");
        out.append("App v").append(version).append("  ·  Android SDK ").append(sdk).append("\n\n");
        out.append("CURRENTLY REPORTED STATE\n");
        if (telemetry == null || !"1".equals(field(telemetry, "available"))) {
            out.append("No recent foreground telemetry available.\n");
        } else {
            out.append("Sample: ").append(age(field(telemetry, "sample_age_ms"))).append("\n");
            out.append("Mode: ").append(mode(field(telemetry, "mode"))).append("\n");
            out.append("Top CRTC: ").append(crtc(field(telemetry, "top_crtc"))).append("\n");
            out.append("Bottom CRTC: ").append(crtc(field(telemetry, "bottom_crtc"))).append("\n");
            out.append("Display Fix requested: ").append(flag(field(telemetry, "display_fix_requested"))).append("\n");
            out.append("CPU Fix desired: ").append(flag(field(telemetry, "cpu_fix_desired"))).append("\n");
            out.append("CPU property reported: ").append(flag(field(telemetry, "cpu_property"))).append("\n");
            out.append("Lid Guard requested: ").append(flag(field(telemetry, "lid_guard_requested"))).append("\n");
            out.append("Display actions held: ").append(flag(field(telemetry, "actions_held"))).append("\n");
            out.append("Software observations, not physical panel measurements.\n");
        }

        out.append("\nAPP EXIT HISTORY\n");
        out.append("Android app only · not the root daemon\n");
        if (exits.isEmpty()) {
            if ("empty".equals(history) || "available".equals(history))
                out.append("No app exits retained by Android.\n");
            else if ("unsupported".equals(history))
                out.append("Exit history is not supported by this Android version.\n");
            else out.append("Exit history unavailable.\n");
        } else {
            for (String exit : exits) {
                out.append("• ").append(age(field(exit, "age_ms")))
                        .append("  —  ").append(reason(field(exit, "reason")));
                String status = field(exit, "status");
                if (!"unknown".equals(status)) out.append("  (status ").append(status).append(")");
                out.append("\n");
            }
        }

        out.append("\nRECENT EVENTS  ·  ").append(events.size()).append(" retained\n");
        if (events.isEmpty()) {
            out.append("No recorded events yet.\n");
        } else {
            for (int i = events.size() - 1; i >= 0; i--) {
                String event = events.get(i);
                out.append("• ").append(age(field(event, "age_ms")))
                        .append("  —  ").append(eventName(field(event, "code"), field(event, "detail")))
                        .append("\n");
            }
        }
        out.append("\nNOTES\n");
        out.append("Activity closure does not necessarily mean a process crash.\n");
        out.append("Last foreground samples may be stale; hardware/energy states are not proven.\n");
        out.append("Use Save report for the full sanitized technical record.\n");
        return out.toString();
    }

    private static String field(String line, String key) {
        for (String part : line.split(";", -1)) {
            if (part.startsWith(key + "=")) {
                String value = part.substring(key.length() + 1);
                return value.isEmpty() ? "unknown" : value;
            }
        }
        return "unknown";
    }

    private static String age(String raw) {
        try {
            long ms = Long.parseLong(raw);
            if (ms < 0L) return "time unknown";
            if (ms < 1000L) return "just now";
            long seconds = ms / 1000L;
            if (seconds < 60L) return seconds + "s ago";
            long minutes = seconds / 60L;
            if (minutes < 60L) return minutes + "m ago";
            long hours = minutes / 60L;
            if (hours < 24L) return hours + "h ago";
            return hours / 24L + "d ago";
        } catch (NumberFormatException error) {
            return "time unknown";
        }
    }

    private static String mode(String value) {
        if ("0".equals(value)) return "BOTH";
        if ("1".equals(value)) return "TOP only";
        if ("2".equals(value)) return "BOTTOM only";
        return "Unknown";
    }

    private static String crtc(String value) {
        if ("1".equals(value)) return "Active";
        if ("0".equals(value)) return "Inactive";
        return "Unknown";
    }

    private static String flag(String value) {
        if ("1".equals(value)) return "ON";
        if ("0".equals(value)) return "OFF";
        return "Unknown";
    }

    private static String reason(String value) {
        switch (value) {
            case "CRASH": return "App crash";
            case "CRASH_NATIVE": return "Native app crash";
            case "LOW_MEMORY": return "Android reported low-memory exit";
            case "ANR": return "App not responding";
            case "USER_REQUESTED": return "User-requested app stop";
            case "USER_STOPPED": return "App stopped by user";
            case "EXIT_SELF": return "App exited itself";
            case "SIGNALED": return "Process terminated by signal";
            case "DEPENDENCY_DIED": return "Android reported dependency death";
            case "EXCESSIVE_RESOURCE_USAGE": return "Android reported high resource usage";
            case "PACKAGE_UPDATED": return "App package updated";
            case "PACKAGE_STATE_CHANGE": return "App package state changed";
            case "INITIALIZATION_FAILURE": return "App initialization failed";
            case "PERMISSION_CHANGE": return "App permission changed";
            case "FREEZER": return "Android freezer exit";
            default: return "Android reason: " + value;
        }
    }

    private static String eventName(String code, String detail) {
        switch (code) {
            case "APP_CREATED": return "App opened";
            case "FOREGROUND": return "Dashboard resumed";
            case "BACKGROUND": return "Dashboard in background";
            case "ACTIVITY_DESTROYED": return "Activity destroyed (not a confirmed crash)";
            case "DAEMON_CONNECTED": return "Daemon responding";
            case "DAEMON_UNAVAILABLE": return "Daemon response unavailable";
            case "WATCHER_RUNNING": return "Display watcher running";
            case "WATCHER_NOT_RUNNING": return "Display watcher not running";
            case "BOOT_HOLD_ENTERED": return "Boot hold: display operations paused";
            case "BOOT_HOLD_CLEARED": return "Boot hold cleared";
            case "WAKE_OBSERVED": return "Wake counter advanced";
            case "REPAIR_STATUS": return "Bottom-screen repair: " + readable(detail);
            case "CPU_PHASE": return "CPU Fix phase: " + readable(detail);
            case "TELEMETRY_CHANGED": return "Legacy telemetry observation";
            case "DISPLAY_OBSERVED":
                if (detail.matches("MODE_[012]_TOP_[01]_BOTTOM_[01]")) {
                    String[] tokens = detail.split("_");
                    return "Display: " + mode(tokens[1]) + " · top " + crtc(tokens[3])
                            + " · bottom " + crtc(tokens[5]);
                }
                return "Display state observed";
            case "CONFIG_OBSERVED":
                if (detail.matches("DISPLAY_[01?]_CPU_DESIRED_[01?]_CPU_PROP_[01?]_LID_[01?]")) {
                    String[] tokens = detail.split("_");
                    return "Settings: display " + flag(tokens[1])
                            + " · CPU desired " + flag(tokens[4])
                            + " · CPU property " + flag(tokens[7])
                            + " · lid " + flag(tokens[9]);
                }
                return "Settings observed";
            default: return "Other recorded event";
        }
    }

    private static String readable(String value) {
        if ("OFF_OK".equals(value)) return "bottom OFF confirmed";
        if ("RETRY_PENDING".equals(value)) return "retry pending";
        return value.replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
    }
}
