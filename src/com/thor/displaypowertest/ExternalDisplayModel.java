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
    private static final Pattern DISPLAY_ID =
            Pattern.compile("(?:^|, )displayId=(\\d+)(?=,|$)");

    private ExternalDisplayModel() {}

    public static Boolean hasExtraExternalViewport(String viewportsLine) {
        if (viewportsLine == null || !viewportsLine.contains("mViewports=")) return null;
        Matcher viewports = VIEWPORT.matcher(viewportsLine);
        int matched = 0;
        while (viewports.find()) {
            matched++;
            String viewport = viewports.group(1);
            if (!viewport.contains("type=EXTERNAL") || !viewport.contains("valid=true")
                    || !viewport.contains("isActive=true")) continue;
            Matcher id = DISPLAY_ID.matcher(viewport);
            if (!id.find()) return null;
            final int displayId;
            try {
                displayId = Integer.parseInt(id.group(1));
            } catch (NumberFormatException malformed) {
                return null;
            }
            // Duplicate IDs are ambiguous. A malformed display dump must not
            // be accepted as evidence that no dock is connected.
            if (id.find()) return null;
            if (displayId != ThorHardwareProfile.BOTTOM_LOGICAL_DISPLAY_ID) return true;
        }
        if (matched == 0) return null;
        // A truncated or unclosed DisplayViewport{... fragment must never
        // silently disappear and yield a false "no extra display" verdict.
        int started = 0;
        int cursor = 0;
        while ((cursor = viewportsLine.indexOf(VIEWPORT_START, cursor)) >= 0) {
            started++;
            cursor += VIEWPORT_START.length();
        }
        return started == matched ? Boolean.FALSE : null;
    }
}
