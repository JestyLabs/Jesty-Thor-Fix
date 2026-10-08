package com.thor.displaypowertest;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Read-only, app-side event observations. Only changes in allow-listed Q fields
 * produce events. No polling, daemon commands, arbitrary error text or PIDs.
 * Pure Java for deterministic host testing.
 */
public final class EventHistoryModel {
    public static final int MAX_EVENTS = 64;
    private static final String HEADER = "THOR_EVENTS_V1";
    private static final int MAX_STORED_CHARS = 8192;

    public enum Type {
        DAEMON_CONNECTED, DAEMON_UNAVAILABLE, BOOT_HOLD_ENTERED, BOOT_HOLD_CLEARED,
        WATCHER_RUNNING, WATCHER_NOT_RUNNING, DISPLAY_OBSERVED, WAKE_OBSERVED,
        REPAIR_STATUS, CPU_PHASE
    }

    public static final class Entry {
        public final long timeMillis;
        public final Type type;
        public final String detail;

        Entry(long timeMillis, Type type, String detail) {
            this.timeMillis = timeMillis;
            this.type = type;
            this.detail = detail;
        }
    }

    private final ArrayDeque<Entry> events = new ArrayDeque<>();
    private Boolean connected;
    private String lastHold;
    private String lastWatcher;
    private String lastDisplay;
    private String candidateDisplay;
    private int candidateSamples;
    private long lastWakeId = -1L;
    private String lastRepair;
    private String lastCpuPhase;

    public List<Entry> entries() { return new ArrayList<>(events); }
    public int size() { return events.size(); }

    public void clear() { events.clear(); }

    public boolean unavailable(long timeMillis) {
        if (Boolean.FALSE.equals(connected)) return false;
        connected = Boolean.FALSE;
        // A later response must establish new evidence. Never infer
        // display/wake transitions across a disconnected telemetry interval.
        lastHold = lastWatcher = lastRepair = lastCpuPhase = null;
        lastDisplay = candidateDisplay = null;
        candidateSamples = 0;
        lastWakeId = -1L;
        return append(timeMillis, Type.DAEMON_UNAVAILABLE, "NO_Q_REPLY");
    }

    public boolean sample(Map<String, String> values, long timeMillis) {
        if (values == null) return unavailable(timeMillis);
        boolean added = false;
        if (!Boolean.TRUE.equals(connected)) {
            connected = Boolean.TRUE;
            added |= append(timeMillis, Type.DAEMON_CONNECTED, "Q_REPLY");
        }

        String held = binary(values.get("display_actions_held"));
        if (held != null && !held.equals(lastHold)) {
            if ("1".equals(held)) {
                added |= append(timeMillis, Type.BOOT_HOLD_ENTERED, "DISPLAY_ACTIONS_HELD");
            } else if ("1".equals(lastHold)) {
                added |= append(timeMillis, Type.BOOT_HOLD_CLEARED, "DISPLAY_ACTIONS_RESUMED");
            }
            lastHold = held;
        }

        String watcher = values.get("watcher_health");
        if (watcher != null && token(watcher) && !watcher.equals(lastWatcher)) {
            String status = "RUNNING".equals(watcher) ? "RUNNING" : "NOT_RUNNING";
            String oldStatus = "RUNNING".equals(lastWatcher) ? "RUNNING"
                    : lastWatcher == null ? null : "NOT_RUNNING";
            if (!status.equals(oldStatus)) {
                added |= append(timeMillis, "RUNNING".equals(status)
                        ? Type.WATCHER_RUNNING : Type.WATCHER_NOT_RUNNING, status);
            }
            lastWatcher = watcher;
        }

        String mode = values.get("mode");
        String top = binary(values.get("top_crtc"));
        String bottom = binary(values.get("bottom_crtc"));
        String state = mode != null && ("0".equals(mode) || "1".equals(mode) || "2".equals(mode))
                && top != null && bottom != null
                ? "MODE_" + mode + "_TOP_" + top + "_BOTTOM_" + bottom : null;
        if (state == null) {
            candidateDisplay = null;
            candidateSamples = 0;
        } else {
            if (!state.equals(candidateDisplay)) {
                candidateDisplay = state;
                candidateSamples = 1;
            } else if (candidateSamples < 2) {
                candidateSamples++;
            }
            if (candidateSamples >= 2 && !state.equals(lastDisplay)) {
                added |= append(timeMillis, Type.DISPLAY_OBSERVED, state);
                lastDisplay = state;
            }
        }

        long wake = nonNegativeLong(values.get("wake_id"));
        if (wake >= 0L) {
            if (lastWakeId >= 0L && wake > lastWakeId) {
                added |= append(timeMillis, Type.WAKE_OBSERVED, "WAKE_COUNTER_ADVANCED");
            }
            // A smaller value after restart is not a new wake event.
            lastWakeId = wake;
        }

        String repair = values.get("repair_result");
        if (repair != null && repairStatus(repair) && !repair.equals(lastRepair)) {
            if (lastRepair != null && !"NONE".equals(repair)) {
                added |= append(timeMillis, Type.REPAIR_STATUS, repair);
            }
            lastRepair = repair;
        }

        String cpu = values.get("cpu_fix_phase");
        if (cpu != null && cpuPhase(cpu) && !cpu.equals(lastCpuPhase)) {
            if (lastCpuPhase != null || "ERROR".equals(cpu)) {
                added |= append(timeMillis, Type.CPU_PHASE, cpu);
            }
            lastCpuPhase = cpu;
        }
        return added;
    }

    private boolean append(long timestamp, Type type, String detail) {
        if (timestamp < 0L || type == null || !token(detail)) return false;
        events.addLast(new Entry(timestamp, type, detail));
        while (events.size() > MAX_EVENTS) events.removeFirst();
        return true;
    }

    public String encode() {
        StringBuilder out = new StringBuilder(HEADER).append('\n');
        for (Entry entry : events) {
            out.append(entry.timeMillis).append('|').append(entry.type.name())
                    .append('|').append(entry.detail).append('\n');
        }
        return out.toString();
    }

    /** Rejects malicious/old/corrupt data; never restores arbitrary text. */
    public static EventHistoryModel decode(String data) {
        EventHistoryModel result = new EventHistoryModel();
        if (data == null || data.length() > MAX_STORED_CHARS
                || !data.startsWith(HEADER + "\n")) return result;
        String[] lines = data.split("\n", -1);
        for (int i = 1; i < lines.length; i++) {
            String[] parts = lines[i].split("\\|", -1);
            if (parts.length != 3 || !token(parts[2])) continue;
            try {
                long timestamp = Long.parseLong(parts[0]);
                Type type = Type.valueOf(parts[1]);
                // Serialized values are untrusted, even though live samples are allow-listed.
                if (detailAllowed(type, parts[2])) result.append(timestamp, type, parts[2]);
            } catch (IllegalArgumentException ignored) {
                // Entries are observational only; damaged history is disposable.
            }
        }
        return result;
    }

    /** Verify each stored type/detail pair, not just its character set. */
    private static boolean detailAllowed(Type type, String detail) {
        if (type == null || detail == null) return false;
        switch (type) {
            case DAEMON_CONNECTED: return "Q_REPLY".equals(detail);
            case DAEMON_UNAVAILABLE: return "NO_Q_REPLY".equals(detail);
            case BOOT_HOLD_ENTERED: return "DISPLAY_ACTIONS_HELD".equals(detail);
            case BOOT_HOLD_CLEARED: return "DISPLAY_ACTIONS_RESUMED".equals(detail);
            case WATCHER_RUNNING: return "RUNNING".equals(detail);
            case WATCHER_NOT_RUNNING: return "NOT_RUNNING".equals(detail);
            case WAKE_OBSERVED: return "WAKE_COUNTER_ADVANCED".equals(detail);
            case REPAIR_STATUS: return repairStatus(detail);
            case CPU_PHASE: return cpuPhase(detail);
            case DISPLAY_OBSERVED:
                return detail.matches("MODE_[012]_TOP_[01]_BOTTOM_[01]");
            default: return false;
        }
    }

    /** Explicit firmware/daemon values only; never persist arbitrary IPC text. */
    private static boolean repairStatus(String value) {
        return "NONE".equals(value) || "SCHEDULED".equals(value)
                || "RESCHEDULED".equals(value) || "RUNNING".equals(value)
                || "CANCELLED".equals(value) || "OFF_OK".equals(value)
                || "RETRY_PENDING".equals(value);
    }

    private static boolean cpuPhase(String value) {
        return "PENDING".equals(value) || "CONFIRMED".equals(value)
                || "ERROR".equals(value) || "UNKNOWN".equals(value)
                || "MISMATCH".equals(value);
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
        if (value == null || value.length() > 18 || value.length() == 0) return -1L;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) < '0' || value.charAt(i) > '9') return -1L;
        }
        try { return Long.parseLong(value); } catch (NumberFormatException ignored) { return -1L; }
    }
}
