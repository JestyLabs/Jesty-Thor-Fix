package com.thor.displaypowertest;

import android.graphics.Color;

/** Dashboard palette shared by the layout, renderer and settings controller. */
final class DashboardStyle {
    static final int PURPLE = Color.rgb(196, 92, 255);
    static final int AMBER = Color.rgb(255, 211, 102);
    static final int GREEN = Color.rgb(111, 224, 163);
    static final int RED = Color.rgb(255, 112, 126);
    static final int MUTED = Color.rgb(168, 185, 204);

    private DashboardStyle() {}

    static int color(DashboardStateModel.Tone tone) {
        if (tone == DashboardStateModel.Tone.GREEN) return GREEN;
        if (tone == DashboardStateModel.Tone.AMBER) return AMBER;
        if (tone == DashboardStateModel.Tone.RED) return RED;
        return MUTED;
    }
}
