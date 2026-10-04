package com.thor.displaypowertest;

/**
 * The vendor bridge accepts a shell command but does not report its exit code.
 * Keep the launch below a conservative 255 characters and retain the root-log
 * ownership check. Boot timing travels in one compact, validated environment
 * field so the bridge command does not grow with four variable names.
 */
public final class DaemonLaunchScript {
    public static final int MAX_COMMAND_CHARS = 255;

    private DaemonLaunchScript() {}

    public static String command(String logPath, boolean enabled, boolean hold,
            boolean cpuFix, boolean lidGuard, long receiverMs, long serviceMs,
            String socketState, long launchWaitMs) {
        if (logPath == null || !logPath.matches("/[A-Za-z0-9/._-]+")) {
            throw new IllegalArgumentException("invalid log path");
        }
        String metadata = metadata(receiverMs, serviceMs, socketState, launchWaitMs);
        // This package has a single base APK. A multiline pm path result would
        // fail class loading rather than run a different package's code.
        String command = "L=" + logPath
                + ";[ ! -L \"$L\" ]&&{ [ ! -e \"$L\" ]||{ [ -f \"$L\" ]"
                + "&&[ -O \"$L\" ]; }; }||L=/dev/null;"
                + "A=$(pm path com.thor.displaypowertest);A=${A#*:};"
                + "JT=" + metadata + " CLASSPATH=$A app_process / D "
                + (enabled ? "1" : "0") + (hold ? " hold " : " run ")
                + (cpuFix ? "1" : "0") + " " + (lidGuard ? "1" : "0")
                + " >>\"$L\" 2>&1 &";
        if (command.length() > MAX_COMMAND_CHARS) {
            throw new IllegalArgumentException("daemon launch command too long: "
                    + command.length());
        }
        return command;
    }

    public static String metadata(long receiverMs, long serviceMs, String socketState,
            long launchWaitMs) {
        return number(receiverMs) + "," + number(serviceMs) + ","
                + socketCode(socketState) + "," + number(launchWaitMs);
    }

    /** Returns the four display-ready trace fields, or unknown fields. */
    public static String[] traceFields(String value) {
        String[] unknown = {"?", "?", "?", "?"};
        if (value == null) return unknown;
        String[] parts = value.split(",", -1);
        if (parts.length != 4) return unknown;
        String receiver = decimal(parts[0]);
        String service = decimal(parts[1]);
        String socket = socketName(parts[2]);
        String wait = decimal(parts[3]);
        if ("?".equals(receiver) || "?".equals(service)
                || "?".equals(socket) || "?".equals(wait)) return unknown;
        return new String[]{receiver, service, socket, wait};
    }

    private static String number(long value) {
        return value < 0L ? "-" : Long.toString(value, 36);
    }

    private static String decimal(String encoded) {
        if ("-".equals(encoded)) return "-1";
        if (!encoded.matches("[0-9a-z]{1,13}")) return "?";
        try {
            long value = Long.parseLong(encoded, 36);
            return value >= 0L ? Long.toString(value) : "?";
        } catch (NumberFormatException ignored) {
            return "?";
        }
    }

    private static String socketCode(String state) {
        if ("ABSENT".equals(state)) return "A";
        if ("STALE_PREVIOUS_BOOT".equals(state)) return "P";
        if ("STALE_UNKNOWN".equals(state)) return "U";
        if ("ALIVE".equals(state)) return "L";
        return "N";
    }

    private static String socketName(String code) {
        if ("A".equals(code)) return "ABSENT";
        if ("P".equals(code)) return "STALE_PREVIOUS_BOOT";
        if ("U".equals(code)) return "STALE_UNKNOWN";
        if ("L".equals(code)) return "ALIVE";
        return "N".equals(code) ? "NOT_CHECKED" : "?";
    }
}
