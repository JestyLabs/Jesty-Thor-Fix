package com.thor.displaypowertest;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import static com.thor.displaypowertest.DashboardStyle.AMBER;
import static com.thor.displaypowertest.DashboardStyle.MUTED;
import static com.thor.displaypowertest.DashboardStyle.RED;

/**
 * Builds the dashboard view tree once and exposes the widgets that the
 * renderer, settings controller and media controller update. No behavior.
 */
final class DashboardLayout {
    /** Top-bar actions; MainActivity owns what they do. */
    interface TopActions {
        void onUpdate();
        void onSupport();
        void onGithub();
        void onGithubLongPress();
    }

    final FrameLayout root;
    final ImageView backgroundImage;
    final TextureView videoTexture;
    final TextView updateAction;
    final Switch fixToggle;
    final Switch dashboardFixToggle;
    final Switch lidGuardToggle;
    final TextView dashboardFixHelp;
    final TextView stateText;
    final TextView stateDetail;
    final TextView littleValue;
    final TextView bigValue;
    final TextView primeValue;
    final TextView displayValue;
    final TextView displayDetail;
    final TextView powerValue;
    final TextView warningValue;
    final TextView lidValue;

    DashboardLayout(DashboardViews views, TopActions actions) {
        Activity activity = views.activity();
        root = new FrameLayout(activity);
        root.setBackgroundColor(Color.BLACK);
        backgroundImage = new ImageView(activity);
        backgroundImage.setImageResource(views.resource("drawable", "jesty_thor_background"));
        backgroundImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(backgroundImage, views.match());

        // BackgroundMediaController installs the surface listener.
        videoTexture = new TextureView(activity);
        videoTexture.setOpaque(false);
        root.addView(videoTexture, views.match());

        View shade = new View(activity);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFC080510, 0xF012091B, 0xB3211031, 0x22311242, 0x00000000}));
        root.addView(shade, views.match());

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        int panelWidth = Math.round(activity.getResources().getDisplayMetrics().widthPixels * 0.56f);
        root.addView(scroll, new FrameLayout.LayoutParams(panelWidth,
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START));

        LinearLayout openSourceBadge = buildOpenSourceBadge(views);
        FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, views.dp(38), Gravity.BOTTOM | Gravity.END);
        badgeParams.setMargins(0, 0, views.dp(18), views.dp(16));
        root.addView(openSourceBadge, badgeParams);

        LinearLayout topActions = new LinearLayout(activity);
        topActions.setOrientation(LinearLayout.HORIZONTAL);
        topActions.setGravity(Gravity.CENTER_VERTICAL);
        // Shown only after AppUpdater finds a newer verifiable release.
        updateAction = views.topAction("\u2191  UPDATE");
        updateAction.setTextColor(Color.rgb(36, 19, 56));
        GradientDrawable highlight = new GradientDrawable();
        highlight.setColor(AMBER);
        highlight.setCornerRadius(views.dp(21));
        updateAction.setBackground(highlight);
        updateAction.setVisibility(View.GONE);
        updateAction.setOnClickListener(v -> actions.onUpdate());
        LinearLayout.LayoutParams updateParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, views.dp(38));
        updateParams.setMargins(0, 0, views.dp(8), 0);
        topActions.addView(updateAction, updateParams);

        TextView support = views.topAction("\u2615  SUPPORT");
        support.setOnClickListener(v -> actions.onSupport());
        topActions.addView(support);

        TextView github = views.topAction("\u2605  GITHUB");
        github.setOnClickListener(v -> actions.onGithub());
        github.setOnLongClickListener(v -> {
            actions.onGithubLongPress();
            return true;
        });
        LinearLayout.LayoutParams githubParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, views.dp(38));
        githubParams.setMargins(views.dp(8), 0, 0, 0);
        topActions.addView(github, githubParams);

        FrameLayout.LayoutParams actionParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, views.dp(42), Gravity.TOP | Gravity.END);
        actionParams.setMargins(0, views.dp(16), views.dp(18), 0);
        root.addView(topActions, actionParams);

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(views.dp(24), views.dp(5), views.dp(24), views.dp(5));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        content.addView(buildHeader(views));
        TextView subtitle = views.text(appSubtitle(activity), 13f, MUTED, false);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, 0, 0, views.dp(8));
        content.addView(subtitle);

        stateDetail = views.text("", 12f, MUTED, false);
        stateDetail.setPadding(0, views.dp(2), 0, views.dp(5));
        stateDetail.setVisibility(View.GONE);
        content.addView(stateDetail);

        stateText = views.text("", 1f, Color.TRANSPARENT, false);
        stateText.setVisibility(View.GONE);
        content.addView(stateText, new LinearLayout.LayoutParams(0, 0));

        LinearLayout controls = views.panel();
        fixToggle = views.makeSwitch("", Color.WHITE, 14f);
        controls.addView(views.featureToggleRow("TRUE BOTTOM SCREEN OFF",
                "Actually powers off the lower screen in Top Only mode", fixToggle).row,
                new LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT));

        View divider = new View(activity);
        divider.setBackgroundColor(0x338B4AE2);
        controls.addView(divider, new LinearLayout.LayoutParams(-1, views.dp(1)));

        dashboardFixToggle = views.makeSwitch("", Color.WHITE, 14f);
        DashboardViews.ToggleRow cpuRow = views.featureToggleRow("AYN DASHBOARD CPU FIX",
                "Restarts Android UI/display once to apply", dashboardFixToggle);
        dashboardFixHelp = cpuRow.help;
        controls.addView(cpuRow.row,
                new LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT));
        View guardDivider = new View(activity);
        guardDivider.setBackgroundColor(0x338B4AE2);
        controls.addView(guardDivider, new LinearLayout.LayoutParams(-1, views.dp(1)));
        lidGuardToggle = views.makeSwitch("", Color.WHITE, 14f);
        controls.addView(views.featureToggleRow("CLOSED-LID WAKE GUARD",
                "Returns the Thor to sleep after an accidental wake while closed",
                lidGuardToggle).row,
                new LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(controls);

        LinearLayout cpuPanel = views.panel();
        TextView displayHeading = views.text("DISPLAY MODE", 10f, MUTED, true);
        displayHeading.setLetterSpacing(0.09f);
        cpuPanel.addView(displayHeading);
        displayValue = views.text("\u2014", 18f, Color.WHITE, true);
        displayValue.setGravity(Gravity.CENTER_VERTICAL);
        displayValue.setPadding(0, views.dp(2), 0, 0);
        displayValue.setMinHeight(views.dp(26));
        cpuPanel.addView(displayValue, new LinearLayout.LayoutParams(-1,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        displayDetail = views.text("Waiting for hardware\u2026", 10f, MUTED, false);
        displayDetail.setPadding(0, 0, 0, views.dp(3));
        cpuPanel.addView(displayDetail, new LinearLayout.LayoutParams(-1,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        powerValue = views.text("BATTERY DRAW  \u2014  \u00B7  UNPLUG USB", 10f, MUTED, true);
        powerValue.setPadding(0, 0, 0, views.dp(5));
        cpuPanel.addView(powerValue, new LinearLayout.LayoutParams(-1,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        View statusDivider = new View(activity);
        statusDivider.setBackgroundColor(0x338B4AE2);
        cpuPanel.addView(statusDivider, new LinearLayout.LayoutParams(-1, views.dp(1)));

        TextView cpuHeading = views.text("CPU SPEEDS", 11f, Color.WHITE, true);
        cpuHeading.setLetterSpacing(0.09f);
        cpuHeading.setPadding(0, views.dp(5), 0, 0);
        cpuPanel.addView(cpuHeading);
        LinearLayout clockRow = views.row();
        littleValue = views.addMetric(clockRow, "LITTLE", "\u2014");
        bigValue = views.addMetric(clockRow, "BIG", "\u2014");
        primeValue = views.addMetric(clockRow, "PRIME", "\u2014");
        cpuPanel.addView(clockRow, new LinearLayout.LayoutParams(-1,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        warningValue = views.text("", 10f, RED, true);
        warningValue.setPadding(0, views.dp(4), 0, views.dp(2));
        cpuPanel.addView(warningValue, new LinearLayout.LayoutParams(-1,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        lidValue = views.text("LID UNKNOWN  \u00B7  WAKE GUARD OFF  \u00B7  0 BLOCKED",
                9f, MUTED, true);
        lidValue.setPadding(0, views.dp(2), 0, 0);
        cpuPanel.addView(lidValue, new LinearLayout.LayoutParams(-1,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        content.addView(cpuPanel);
    }

    private static LinearLayout buildOpenSourceBadge(DashboardViews views) {
        LinearLayout badge = new LinearLayout(views.activity());
        badge.setOrientation(LinearLayout.VERTICAL);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(views.dp(16), views.dp(4), views.dp(16), views.dp(4));
        TextView title = views.text("JESTY APPS ARE FREE & OPEN SOURCE", 9f, AMBER, true);
        title.setGravity(Gravity.CENTER);
        title.setLetterSpacing(0.05f);
        badge.addView(title);
        GradientDrawable bubble = new GradientDrawable();
        bubble.setColor(0xB5100B19);
        bubble.setCornerRadius(views.dp(25));
        bubble.setStroke(views.dp(1), 0x668B4AE2);
        badge.setBackground(bubble);
        return badge;
    }

    private static View buildHeader(DashboardViews views) {
        LinearLayout header = new LinearLayout(views.activity());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER);

        ImageView lockup = new ImageView(views.activity());
        lockup.setImageResource(views.resource("drawable", "jesty_thor_header_lockup"));
        lockup.setScaleType(ImageView.ScaleType.FIT_CENTER);
        lockup.setAdjustViewBounds(true);
        lockup.setContentDescription("Jesty Thor Fix");
        header.addView(lockup, new LinearLayout.LayoutParams(views.dp(242), views.dp(68)));

        header.setLayoutParams(new LinearLayout.LayoutParams(-1, views.dp(70)));
        return header;
    }

    private static String appSubtitle(Activity activity) {
        String label = "Display and CPU fixes for AYN Thor";
        try {
            String version = activity.getPackageManager()
                    .getPackageInfo(activity.getPackageName(), 0).versionName;
            if (version != null && !version.trim().isEmpty()) return label + " (v" + version.trim() + ")";
        } catch (PackageManager.NameNotFoundException ignored) {
            // Keep the descriptive label even if package metadata is unavailable.
        }
        return label;
    }
}
