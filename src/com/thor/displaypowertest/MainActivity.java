package com.thor.displaypowertest;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import java.util.Map;

/**
 * Dashboard lifecycle and wiring only. Layout, rendering, settings commands,
 * telemetry polling and background media live in their own classes; this
 * activity connects them and owns the app-level actions (links, updater,
 * AutoService requests).
 */
public final class MainActivity extends Activity {
    private DashboardLayout layout;
    private BackgroundMediaController media;
    private DashboardSettingsController settings;
    private DashboardRenderer renderer;
    private TelemetryPoller telemetry;
    // Read by AppUpdater's worker before it commits an install.
    private final UpdateReadiness updateReadiness = new UpdateReadiness();
    private volatile boolean activityResumed;
    private long replacementRequestedAt = -1L;
    private AppUpdater updater;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        SharedPreferences preferences = getSharedPreferences("state", MODE_PRIVATE);
        configureWindow();
        DashboardViews views = new DashboardViews(this);
        layout = new DashboardLayout(views, new DashboardLayout.TopActions() {
            @Override public void onUpdate() {
                if (updater != null) updater.promptUpdate();
            }
            @Override public void onSupport() { openExternal("https://buymeacoffee.com/jesty"); }
            @Override public void onGithub() {
                openExternal("https://github.com/JestyLabs/Jesty-Thor-Fix");
            }
            @Override public void onGithubLongPress() {
                if (updater != null) updater.showSettings();
            }
        });
        media = new BackgroundMediaController(views, layout.backgroundImage, layout.videoTexture);
        renderer = new DashboardRenderer(layout, new DashboardRenderer.Host() {
            @Override public void onDisplayVisual(DashboardStateModel.Visual visual) {
                media.setVisual(visual);
            }
            @Override public void requestDaemonReplacement() {
                MainActivity.this.requestDaemonReplacement();
            }
        });
        settings = new DashboardSettingsController(this, preferences, layout, renderer);
        telemetry = new TelemetryPoller(this, new TelemetryPoller.Listener() {
            @Override public void onSampleRecorded(Map<String, String> values) {
                updateReadiness.record(values);
            }
            @Override public void onSample(Map<String, String> values) {
                renderer.render(values, settings);
            }
            @Override public void onUnavailable() {
                renderer.renderUnavailable(settings.displayCommandInFlight());
            }
        });
        setContentView(layout.root);
        settings.bind();

        startService(new Intent(this, AutoService.class));
        updater = new AppUpdater(this, new AppUpdater.Host() {
            @Override public void onUpdateAvailable(String version, boolean prerelease) {
                layout.updateAction.setText(
                        (prerelease ? "\u2191  TEST v" : "\u2191  UPDATE v") + version);
                layout.updateAction.setVisibility(View.VISIBLE);
            }
            @Override public void onNoUpdate() { layout.updateAction.setVisibility(View.GONE); }
            @Override public String installBlocker() {
                return updateReadiness.installBlocker(activityResumed,
                        settings.anyCommandInFlight());
            }
            @Override public String handoverNote() {
                return updateReadiness.handoverNote();
            }
            @Override public void setInstallReserved(boolean reserved) {
                settings.reserveForInstall(reserved);
            }
        });
        updater.start();
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.BLACK);
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override protected void onResume() {
        super.onResume();
        activityResumed = true;
        // State seen before a pause is not trusted for an install decision.
        updateReadiness.invalidate();
        startTelemetry();
        media.onResume();
        if (updater != null) updater.onResume();
    }

    @Override protected void onPause() {
        activityResumed = false;
        stopTelemetry();
        updateReadiness.invalidate();
        if (updater != null) updater.onPause();
        media.onPause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        stopTelemetry();
        if (updater != null) updater.shutdown();
        settings.shutdown();
        media.release();
        super.onDestroy();
    }

    private void startTelemetry() {
        if (telemetry.running()) return;
        renderer.resetForStart();
        telemetry.start();
    }

    private void stopTelemetry() {
        telemetry.stop();
        renderer.resetOnStop();
    }

    private void openExternal(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Throwable error) {
            Toast.makeText(this, "No browser is available", Toast.LENGTH_SHORT).show();
        }
    }

    /** At most once a minute; AutoService ignores a start while its worker runs. */
    private void requestDaemonReplacement() {
        long now = SystemClock.elapsedRealtime();
        if (replacementRequestedAt >= 0L && now - replacementRequestedAt < 60000L) return;
        replacementRequestedAt = now;
        try {
            startService(new Intent(this, AutoService.class));
        } catch (RuntimeException error) {
            android.util.Log.w("ThorDisplayUi", "background service request failed", error);
        }
    }
}
