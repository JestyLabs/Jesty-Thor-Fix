package com.thor.displaypowertest;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The Thor's built-in lower display is reported as an EXTERNAL viewport. */
public final class ExternalDisplayModel {
    private static final Pattern VIEWPORT = Pattern.compile("DisplayViewport\\{([^}]*)\\}");
    private static final Pattern DISPLAY_ID = Pattern.compile("(?:^|, )displayId=(\\d+)(?:,|$)");

    private ExternalDisplayModel() {}

    public static Boolean hasExtraExternalViewport(String viewportsLine) {
        if (viewportsLine == null || !viewportsLine.contains("mViewports=")) return null;
        Matcher viewports = VIEWPORT.matcher(viewportsLine);
        while (viewports.find()) {
            String viewport = viewports.group(1);
            if (!viewport.contains("type=EXTERNAL") || !viewport.contains("valid=true")
                    || !viewport.contains("isActive=true")) continue;
            Matcher id = DISPLAY_ID.matcher(viewport);
            if (!id.find()) return null;
            int displayId = Integer.parseInt(id.group(1));
            if (displayId != ThorHardwareProfile.BOTTOM_LOGICAL_DISPLAY_ID) return true;
        }
        return false;
    }
}
