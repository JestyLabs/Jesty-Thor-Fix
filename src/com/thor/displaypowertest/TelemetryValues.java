package com.thor.displaypowertest;

import java.util.HashMap;
import java.util.Map;

/** Parsing of the daemon's {@code key=value;...} replies. Missing values stay unknown. */
final class TelemetryValues {
    private TelemetryValues() {}

    static Map<String, String> parse(String response) {
        Map<String, String> values = new HashMap<>();
        for (String part : response.split(";")) {
            int split = part.indexOf('=');
            if (split > 0) values.put(part.substring(0, split), part.substring(split + 1));
        }
        return values;
    }

    static String value(Map<String, String> values, String key) {
        String value = values.get(key);
        return value == null || value.length() == 0 ? "\u2014" : value;
    }

    static long number(Map<String, String> values, String key) {
        try { return Long.parseLong(values.get(key)); } catch (Throwable ignored) { return -1L; }
    }

    static double decimalNumber(Map<String, String> values, String key) {
        try { return Double.parseDouble(values.get(key)); } catch (Throwable ignored) { return -1d; }
    }
}
