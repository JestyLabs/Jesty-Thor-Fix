package com.thor.displaypowertest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Allowlisted app observations. No raw daemon replies or system logs enter exports. */
public final class AppDiagnosticReport {
    public static final int MAX_EVENTS = 64;
    public static final int MAX_EXITS = 8;
    public static final int MAX_REPORT_CHARS = 24000;
    private static final String[] EVENTS = {"APP_CREATED", "FOREGROUND", "BACKGROUND",
            "ACTIVITY_DESTROYED", "TELEMETRY_CHANGED"};
    private final List<String> events = new ArrayList<>();
    private String telemetry = "available=unknown";
    private long sampleAt = -1;
    // No second journal or persistence: classifier only tracks Q baselines.
    private final AppTelemetryTransitions transitions = new AppTelemetryTransitions();

    public AppDiagnosticReport(String stored) {
        if (stored == null || stored.length() > 16000) return;
        for (String line : stored.split("\n")) {
            if (validStoredLine(line)) {
                events.add(line);
                trim();
            }
        }
    }

    public synchronized String record(long wallMs, String event) {
        return record(wallMs, event, "");
    }

    private String record(long wallMs, String event, String detail) {
        boolean known = false;
        for (String candidate : EVENTS) known |= candidate.equals(event);
        if (!known || wallMs < 0) return stored();
        events.add(wallMs + "|" + event + "|" + detail);
        trim();
        return stored();
    }

    /** Consumes only existing foreground Q samples; persists typed edges once per batch. */
    public synchronized boolean sample(long wallMs, long elapsedMs, Map<String, String> values) {
        telemetry = snapshot(values); // Current snapshot is observational, not a confirmed edge.
        sampleAt = elapsedMs;
        boolean changed = false;
        for (AppTelemetryTransitions.Change edge : transitions.observe(values)) {
            if (AppTelemetryTransitions.allowed(edge.type, edge.detail) && wallMs >= 0) {
                events.add(wallMs + "|" + edge.type + "|" + edge.detail);
                trim();
                changed = true;
            }
        }
        return changed;
    }

    /** A paused dashboard cannot infer transitions across an unobserved interval. */
    public synchronized void pauseObservations() {
        transitions.pause();
    }

    public synchronized String stored() {
        StringBuilder out = new StringBuilder();
        for (String event : events) out.append(event).append('\n');
        return out.toString();
    }

    public synchronized String export(long wallMs, long elapsedMs, String version, int sdk,
            String exitState, List<Exit> exits) {
        return export(wallMs, elapsedMs, version, sdk, exitState, exits, null);
    }

    public synchronized String export(long wallMs, long elapsedMs, String version, int sdk,
            String exitState, List<Exit> exits, PassiveSleepTrial.Result trial) {
        StringBuilder out = new StringBuilder("JESTY_THOR_DIAGNOSTICS_V1\n");
        out.append("app_version=").append(version != null && version.matches("[0-9]+\\.[0-9]+\\.[0-9]+")
                ? version : "unknown").append(";sdk=").append(sdk >= 1 && sdk <= 100 ? sdk : 0);
        out.append("\ntelemetry_scope=last_foreground_sample;sample_age_ms=")
                .append(age(elapsedMs, sampleAt)).append(';').append(telemetry);
        out.append("\nexit_history_state=").append(exitState != null
                && exitState.matches("available|empty|unsupported|unavailable") ? exitState : "unavailable");
        if (exits != null) {
            for (int i = 0; i < Math.min(MAX_EXITS, exits.size()); i++) {
                Exit exit = exits.get(i);
                if (exit == null) continue;
                out.append("\nexit;age_ms=").append(age(wallMs, exit.timestamp))
                        .append(";reason=").append(reason(exit.reason))
                        .append(";reason_code=").append(number(exit.reason, 0, 1024))
                        .append(";status=").append(number(exit.status, -255, 65535))
                        .append(";importance=").append(number(exit.importance, 0, 1000));
            }
        }
        for (String line : events) {
            String[] parts = line.split("\\|", -1);
            out.append("\nevent;age_ms=").append(age(wallMs, Long.parseLong(parts[0])))
                    .append(";code=").append(parts[1]);
            if (!parts[2].isEmpty()) {
                if ("TELEMETRY_CHANGED".equals(parts[1])) out.append(';').append(parts[2]);
                else out.append(";detail=").append(parts[2]);
            }
        }
        if (trial != null) out.append('\n').append(trial.reportLine());
        out.append("\nlimits=activity_events_are_not_process_deaths;exit_history_is_not_complete;"
                + "crtc_flags_are_not_panel_power;foreground_samples_are_not_sleep_measurements\n");
        if (out.length() > MAX_REPORT_CHARS) throw new IllegalStateException("report bound");
        return out.toString();
    }

    public static final class Exit {
        public final long timestamp;
        public final int reason, status, importance;
        public Exit(long timestamp, int reason, int status, int importance) {
            this.timestamp = timestamp; this.reason = reason;
            this.status = status; this.importance = importance;
        }
    }

    public static String reason(int code) {
        String[] names = {"UNKNOWN", "EXIT_SELF", "SIGNALED", "LOW_MEMORY", "CRASH",
                "CRASH_NATIVE", "ANR", "INITIALIZATION_FAILURE", "PERMISSION_CHANGE",
                "EXCESSIVE_RESOURCE_USAGE", "USER_REQUESTED", "USER_STOPPED", "DEPENDENCY_DIED",
                "OTHER", "FREEZER", "PACKAGE_STATE_CHANGE", "PACKAGE_UPDATED"};
        return code >= 0 && code < names.length ? names[code] : "UNRECOGNIZED";
    }

    private static String snapshot(Map<String, String> values) {
        if (values == null) return "available=0";
        return "available=1;mode=" + choice(values.get("mode"), "0|1|2")
                + ";top_crtc=" + choice(values.get("top_crtc"), "0|1")
                + ";bottom_crtc=" + choice(values.get("bottom_crtc"), "0|1")
                + ";display_fix_requested=" + choice(values.get("fix"), "0|1")
                + ";cpu_fix_desired=" + choice(values.get("cpu_fix_desired"), "0|1")
                + ";cpu_property=" + choice(values.get("system_load_fix"), "0|1")
                + ";lid_guard_requested=" + choice(values.get("lid_guard"), "0|1")
                + ";actions_held=" + choice(values.get("display_actions_held"), "0|1");
    }

    private static String choice(String value, String allowed) {
        return value != null && value.matches(allowed) ? value : "?";
    }

    private static String age(long now, long then) {
        return then >= 0 && now >= then ? Long.toString(now - then) : "unknown";
    }

    private static String number(int value, int low, int high) {
        return value >= low && value <= high ? Integer.toString(value) : "unknown";
    }

    private static boolean validStoredLine(String line) {
        String[] parts = line.split("\\|", -1);
        if (parts.length != 3) return false;
        try { if (Long.parseLong(parts[0]) < 0) return false; }
        catch (NumberFormatException error) { return false; }
        boolean known = false;
        for (String candidate : EVENTS) known |= candidate.equals(parts[1]);
        if ("TELEMETRY_CHANGED".equals(parts[1])) {
            // Legacy #55-format snapshots are readable but no longer appended
            // as unconfirmed per-sample display events.
            return parts[2].matches("available=0|available=1;mode=[012?];top_crtc=[01?];bottom_crtc=[01?]"
                    + ";display_fix_requested=[01?];cpu_fix_desired=[01?];cpu_property=[01?]"
                    + ";lid_guard_requested=[01?];actions_held=[01?]");
        }
        if (known) return parts[2].isEmpty();
        return AppTelemetryTransitions.allowed(parts[1], parts[2]);
    }

    private void trim() { while (events.size() > MAX_EVENTS) events.remove(0); }
}
