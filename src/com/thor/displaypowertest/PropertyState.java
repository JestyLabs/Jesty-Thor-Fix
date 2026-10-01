package com.thor.displaypowertest;

/** Strict parser for binary vendor properties used by the boot safety gate. */
public final class PropertyState {
    private PropertyState() {}

    public static String binary(String value, int exitCode) {
        if (exitCode != 0 || value == null) return "?";
        String trimmed = value.trim();
        return "0".equals(trimmed) || "1".equals(trimmed) ? trimmed : "?";
    }
}
