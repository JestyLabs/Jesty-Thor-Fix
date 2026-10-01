package com.thor.displaypowertest;

/** Strict parser for binary vendor properties used by the boot safety gate. */
public final class PropertyState {
    public static final String UNSET = "UNSET";
    public static final String MISSING_MARKER = "__JESTY_PROPERTY_MISSING__";
    private PropertyState() {}

    /** A successful getprop with a default distinguishes unset from read failure. */
    public static String observed(String value, int exitCode) {
        if (exitCode != 0 || value == null) return "?";
        String trimmed = value.trim();
        if (trimmed.isEmpty() || MISSING_MARKER.equals(trimmed)) return UNSET;
        return "0".equals(trimmed) || "1".equals(trimmed) ? trimmed : "?";
    }

    public static String binary(String value, int exitCode) {
        String result = observed(value, exitCode);
        return UNSET.equals(result) ? "?" : result;
    }
}
