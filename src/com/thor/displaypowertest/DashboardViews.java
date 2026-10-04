package com.thor.displaypowertest;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import static com.thor.displaypowertest.DashboardStyle.AMBER;
import static com.thor.displaypowertest.DashboardStyle.MUTED;
import static com.thor.displaypowertest.DashboardStyle.PURPLE;

/** Small view factories for the code-built dashboard. No state. */
final class DashboardViews {
    /** A settings row and its description line, which the CPU Fix row updates. */
    static final class ToggleRow {
        final LinearLayout row;
        final TextView help;

        ToggleRow(LinearLayout row, TextView help) {
            this.row = row;
            this.help = help;
        }
    }

    private final Activity activity;

    DashboardViews(Activity activity) {
        this.activity = activity;
    }

    Activity activity() { return activity; }

    LinearLayout panel() {
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(5), dp(14), dp(5));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xCF151021);
        background.setCornerRadius(dp(14));
        background.setStroke(dp(1), 0x88C45CFF);
        panel.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(6));
        panel.setLayoutParams(params);
        return panel;
    }

    LinearLayout row() {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setWeightSum(3f);
        return row;
    }

    TextView addMetric(LinearLayout row, String label, String initial) {
        LinearLayout metric = new LinearLayout(activity);
        metric.setOrientation(LinearLayout.VERTICAL);
        metric.setGravity(Gravity.CENTER_VERTICAL);
        metric.setPadding(dp(2), 0, dp(5), 0);
        TextView heading = text(label, 10f, MUTED, true);
        heading.setLetterSpacing(0.09f);
        metric.addView(heading);
        TextView value = text(initial, 15f, Color.WHITE, true);
        value.setPadding(0, dp(2), 0, 0);
        metric.addView(value);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(metric, params);
        return value;
    }

    ToggleRow featureToggleRow(String title, String description, Switch toggle) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(46));
        row.setPadding(0, dp(4), 0, dp(4));

        LinearLayout copy = new LinearLayout(activity);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = text(title, 14f, Color.WHITE, true);
        heading.setLetterSpacing(0.025f);
        copy.addView(heading);
        TextView help = text(description, 10f, MUTED, false);
        help.setPadding(0, dp(1), dp(8), 0);
        copy.addView(help);
        row.addView(copy, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(toggle, new LinearLayout.LayoutParams(dp(58), dp(40)));
        return new ToggleRow(row, help);
    }

    Switch makeSwitch(String label, int color, float size) {
        Switch control = new Switch(activity);
        control.setText(label);
        control.setTextColor(color);
        control.setTextSize(size);
        control.setTypeface(Typeface.DEFAULT_BOLD);
        int[][] states = new int[][] { new int[] { android.R.attr.state_checked }, new int[] {} };
        control.setThumbTintList(new ColorStateList(states,
                new int[] { AMBER, Color.rgb(128, 105, 146) }));
        control.setTrackTintList(new ColorStateList(states,
                new int[] { PURPLE, Color.rgb(67, 50, 78) }));
        return control;
    }

    TextView topAction(String label) {
        TextView action = text(label, 10f, AMBER, true);
        action.setGravity(Gravity.CENTER);
        action.setPadding(dp(14), 0, dp(14), 0);
        GradientDrawable bubble = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xE0241338, 0xE0441B55});
        bubble.setCornerRadius(dp(21));
        bubble.setStroke(dp(1), 0xCCC45CFF);
        action.setBackground(bubble);
        action.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)));
        return action;
    }

    TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    FrameLayout.LayoutParams match() { return new FrameLayout.LayoutParams(-1, -1); }

    int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    int resource(String type, String name) {
        return activity.getResources().getIdentifier(name, type, activity.getPackageName());
    }
}
