package com.thor.displaypowertest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pure-Java classifier for already received foreground Q samples. Holds only
 * observation baselines: AppDiagnosticReport owns the sole event history.
 * It neither persists data nor requests telemetry or hardware operations.
 */
final class AppTelemetryTransitions {
    static final class Change {
        final String type;
        final String detail;
        Change(String type, String detail) { this.type = type; this.detail = detail; }
    }

    private Boolean connected;
    private String lastHold, lastWatcher, lastDisplay, candidateDisplay;
    private String lastRepair, lastCpuPhase, lastConfiguration;
    private int candidateSamples;
    private long lastWakeId = -1L;

    void pause() {
        connected = null;
        clearEvidence();
        lastConfiguration = null;
    }

    private void clearEvidence() {
        lastHold = lastWatcher = lastDisplay = candidateDisplay = null;
        lastRepair = lastCpuPhase = null;
        candidateSamples = 0;
        lastWakeId = -1L;
    }

    List<Change> observe(Map<String, String> values) {
        List<Change> changes = new ArrayList<>();
        if (values == null) {
            if (!Boolean.FALSE.equals(connected)) {
                changes.add(new Change("DAEMON_UNAVAILABLE", "NO_Q_REPLY"));
            }
            connected = Boolean.FALSE;
            clearEvidence();
            lastConfiguration = null;
            return changes;
        }
        if (!Boolean.TRUE.equals(connected)) {
            connected = Boolean.TRUE;
            changes.add(new Change("DAEMON_CONNECTED", "Q_REPLY"));
        }

        // These are requested/desired settings, not verified effective states.
        String config = "DISPLAY_" + flag(values.get("fix"))
                + "_CPU_DESIRED_" + flag(values.get("cpu_fix_desired"))
                + "_CPU_PROP_" + flag(values.get("system_load_fix"))
                + "_LID_" + flag(values.get("lid_guard"));
        if (!config.equals(lastConfiguration)) {
            changes.add(new Change("CONFIG_OBSERVED", config));
            lastConfiguration = config;
        }

        String hold = binary(values.get("display_actions_held"));
        if (hold != null && !hold.equals(lastHold)) {
            if ("1".equals(hold)) changes.add(new Change("BOOT_HOLD_ENTERED", "DISPLAY_ACTIONS_HELD"));
            else if ("1".equals(lastHold)) changes.add(new Change("BOOT_HOLD_CLEARED", "DISPLAY_ACTIONS_RESUMED"));
            lastHold = hold;
        }

        String watcher = values.get("watcher_health");
        if (token(watcher)) {
            String status = "RUNNING".equals(watcher) ? "RUNNING" : "NOT_RUNNING";
            if (!status.equals(lastWatcher)) {
                changes.add(new Change("RUNNING".equals(status) ? "WATCHER_RUNNING" : "WATCHER_NOT_RUNNING", status));
                lastWatcher = status;
            }
        }

        String mode = values.get("mode"), top = binary(values.get("top_crtc"));
        String bottom = binary(values.get("bottom_crtc"));
        String observed = ("0".equals(mode) || "1".equals(mode) || "2".equals(mode))
                && top != null && bottom != null
                ? "MODE_" + mode + "_TOP_" + top + "_BOTTOM_" + bottom : null;
        if (observed == null) {
            candidateDisplay = null;
            candidateSamples = 0;
        } else {
            if (!observed.equals(candidateDisplay)) {
                candidateDisplay = observed;
                candidateSamples = 1;
            } else if (candidateSamples < 2) {
                candidateSamples++;
            }
            if (candidateSamples >= 2 && !observed.equals(lastDisplay)) {
                changes.add(new Change("DISPLAY_OBSERVED", observed));
                lastDisplay = observed;
            }
        }

        long wake = nonNegativeLong(values.get("wake_id"));
        if (wake >= 0) {
            if (lastWakeId >= 0 && wake > lastWakeId) {
                changes.add(new Change("WAKE_OBSERVED", "WAKE_COUNTER_ADVANCED"));
            }
            lastWakeId = wake;
        }

        String repair = values.get("repair_result");
        if (repairAllowed(repair) && !repair.equals(lastRepair)) {
            if (lastRepair != null && !"NONE".equals(repair)) {
                changes.add(new Change("REPAIR_STATUS", repair));
            }
            lastRepair = repair;
        }

        String phase = values.get("cpu_fix_phase");
        if (cpuAllowed(phase) && !phase.equals(lastCpuPhase)) {
            if (lastCpuPhase != null || "ERROR".equals(phase)) {
                changes.add(new Change("CPU_PHASE", phase));
            }
            lastCpuPhase = phase;
        }
        return changes;
    }

    /** Also validate all persisted type/detail pairs, not only new samples. */
    static boolean allowed(String type, String detail) {
        if (type == null || detail == null) return false;
        switch (type) {
            case "DAEMON_CONNECTED": return "Q_REPLY".equals(detail);
            case "DAEMON_UNAVAILABLE": return "NO_Q_REPLY".equals(detail);
            case "BOOT_HOLD_ENTERED": return "DISPLAY_ACTIONS_HELD".equals(detail);
            case "BOOT_HOLD_CLEARED": return "DISPLAY_ACTIONS_RESUMED".equals(detail);
            case "WATCHER_RUNNING": return "RUNNING".equals(detail);
            case "WATCHER_NOT_RUNNING": return "NOT_RUNNING".equals(detail);
            case "WAKE_OBSERVED": return "WAKE_COUNTER_ADVANCED".equals(detail);
            case "REPAIR_STATUS": return repairAllowed(detail);
            case "CPU_PHASE": return cpuAllowed(detail);
            case "DISPLAY_OBSERVED": return detail.matches("MODE_[012]_TOP_[01]_BOTTOM_[01]");
            case "CONFIG_OBSERVED":
                return detail.matches("DISPLAY_[01?]_CPU_DESIRED_[01?]_CPU_PROP_[01?]_LID_[01?]");
            default: return false;
        }
    }

    private static boolean repairAllowed(String value) {
        return "NONE".equals(value) || "SCHEDULED".equals(value)
                || "RESCHEDULED".equals(value) || "RUNNING".equals(value)
                || "CANCELLED".equals(value) || "OFF_OK".equals(value)
                || "RETRY_PENDING".equals(value);
    }

    private static boolean cpuAllowed(String value) {
        return "PENDING".equals(value) || "CONFIRMED".equals(value)
                || "ERROR".equals(value) || "UNKNOWN".equals(value)
                || "MISMATCH".equals(value);
    }

    private static String flag(String value) {
        return "0".equals(value) || "1".equals(value) ? value : "?";
    }

    private static String binary(String value) {
        return "0".equals(value) || "1".equals(value) ? value : null;
    }

    private static boolean token(String value) {
        if (value == null || value.length() == 0 || value.length() > 64) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_')) return false;
        }
        return true;
    }

    private static long nonNegativeLong(String value) {
        if (value == null || value.isEmpty() || value.length() > 18) return -1L;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) < '0' || value.charAt(i) > '9') return -1L;
        }
        try { return Long.parseLong(value); }
        catch (NumberFormatException ignored) { return -1L; }
    }
}
