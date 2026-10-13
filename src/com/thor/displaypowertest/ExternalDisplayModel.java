package com.thor.displaypowertest;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Thor's built-in lower display is reported as an EXTERNAL viewport.
 * Unknown or malformed dumpsys evidence is not a safe reason to let Lid Guard
 * sleep the device; callers must treat null as "not established".
 */
public final class ExternalDisplayModel {
    private static final String VIEWPORT_START = "DisplayViewport{";
    private static final Pattern VIEWPORT = Pattern.compile("DisplayViewport\\{([^}]*)\\}");

    private ExternalDisplayModel() {}

    public static Boolean hasExtraExternalViewport(String viewportsLine) {
        if (viewportsLine == null || !viewportsLine.contains("mViewports=")) return null;
        Matcher viewports = VIEWPORT.matcher(viewportsLine);
        int matched = 0;
        boolean hasExtra = false;
        while (viewports.find()) {
            matched++;
            // Inspect every closed viewport, including inactive and invalid
            // entries, before concluding that no extra external display exists.
            String type = null;
            String valid = null;
            String active = null;
            Integer displayId = null;
            for (String part : viewports.group(1).split(",", -1)) {
                int equals = part.indexOf('=');
                if (equals <= 0 || equals == part.length() - 1) return null;
                String key = part.substring(0, equals).trim();
                String value = part.substring(equals + 1).trim();
                if (key.isEmpty() || value.isEmpty()) return null;
                if ("type".equals(key)) {
                    if (type != null) return null;
                    type = value;
                } else if ("valid".equals(key)) {
                    if (valid != null) return null;
                    valid = value;
                } else if ("isActive".equals(key)) {
                    if (active != null) return null;
                    active = value;
                } else if ("displayId".equals(key)) {
                    // Duplicates must be detected even when the second value
                    // is negative or otherwise malformed. Never ignore it.
                    if (displayId != null || !value.matches("[0-9]+")) return null;
                    try {
                        displayId = Integer.valueOf(value);
                    } catch (NumberFormatException malformed) {
                        return null;
                    }
                }
                // Other well-formed metadata keys (e.g. uniqueId) do not
                // participate in the safety decision.
            }
            if (!"INTERNAL".equals(type) && !"EXTERNAL".equals(type)) return null;
            if (!"true".equals(valid) && !"false".equals(valid)) return null;
            if (!"true".equals(active) && !"false".equals(active)) return null;
            if (displayId == null) return null;
            if ("EXTERNAL".equals(type) && "true".equals(valid)
                    && "true".equals(active)
                    && displayId.intValue() != ThorHardwareProfile.BOTTOM_LOGICAL_DISPLAY_ID) {
                hasExtra = true;
            }
        }
        if (matched == 0) return null;
        // An unclosed DisplayViewport fragment cannot silently disappear from
        // the safety decision, even after a valid preceding entry.
        int started = 0;
        int cursor = 0;
        while ((cursor = viewportsLine.indexOf(VIEWPORT_START, cursor)) >= 0) {
            started++;
            cursor += VIEWPORT_START.length();
        }
        if (started != matched) return null;
        return hasExtra ? Boolean.TRUE : Boolean.FALSE;
    }
}
