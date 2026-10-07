package com.thor.displaypowertest;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.thor.displaypowertest.DashboardStyle.AMBER;
import static com.thor.displaypowertest.DashboardStyle.MUTED;

/**
 * The three persistent switches. Each change saves the preference first, then
 * sends one daemon command on a single background thread; a rejected command
 * restores the previous preference, an unanswered one keeps the request and
 * lets telemetry confirm it. UI-thread fields are only touched on that thread;
 * the volatile in-flight flags are also read by AppUpdater's worker.
 */
final class DashboardSettingsController {
    static final String DISPLAY_FIX = "fix_enabled";
    static final String CPU_FIX = "dashboard_cpu_fix_enabled";
    static final String LID_GUARD = "lid_guard_enabled";

    /** Status lines owned by DashboardRenderer. */
    interface Feedback {
        void displayCommandStarted();
        void displayCommandFinished(String error, boolean uncertain);
        void cpuFixCommandStarted();
    }

    private final Activity activity;
    private final SharedPreferences preferences;
    private final Switch fixToggle;
    private final Switch dashboardFixToggle;
    private final Switch lidGuardToggle;
    private final TextView dashboardFixHelp;
    private final Feedback feedback;
    private final ExecutorService commandExecutor = Executors.newSingleThreadExecutor();
    private boolean suppressToggle;
    private boolean suppressDashboardToggle;
    private boolean suppressLidToggle;
    private volatile boolean commandInFlight;
    private volatile boolean dashboardCommandInFlight;
    private volatile boolean lidCommandInFlight;
    private volatile boolean installReserved;

    DashboardSettingsController(Activity activity, SharedPreferences preferences,
            DashboardLayout layout, Feedback feedback) {
        this.activity = activity;
        this.preferences = preferences;
        this.fixToggle = layout.fixToggle;
        this.dashboardFixToggle = layout.dashboardFixToggle;
        this.lidGuardToggle = layout.lidGuardToggle;
        this.dashboardFixHelp = layout.dashboardFixHelp;
        this.feedback = feedback;
    }

    /** Shows the saved settings, then starts listening for user changes. */
    void bind() {
        suppressToggle = true;
        fixToggle.setChecked(desiredDisplayFix());
        suppressToggle = false;

        fixToggle.setOnCheckedChangeListener((button, checked) -> {
            if (!suppressToggle) setFixEnabled(checked);
        });
        suppressDashboardToggle = true;
        dashboardFixToggle.setChecked(desiredCpuFix());
        updateDashboardFixHelp(dashboardFixToggle.isChecked());
        suppressDashboardToggle = false;
        dashboardFixToggle.setOnCheckedChangeListener((button, checked) -> {
            if (!suppressDashboardToggle) confirmDashboardCpuFix(checked);
        });
        suppressLidToggle = true;
        lidGuardToggle.setChecked(desiredLidGuard());
        suppressLidToggle = false;
        lidGuardToggle.setOnCheckedChangeListener((button, checked) -> {
            if (!suppressLidToggle) setLidGuardEnabled(checked);
        });
    }

    boolean desiredDisplayFix() { return preferences.getBoolean(DISPLAY_FIX, true); }
    boolean desiredCpuFix() { return preferences.getBoolean(CPU_FIX, false); }
    boolean desiredLidGuard() { return preferences.getBoolean(LID_GUARD, false); }

    boolean displayCommandInFlight() { return commandInFlight; }

    boolean anyCommandInFlight() {
        return commandInFlight || dashboardCommandInFlight || lidCommandInFlight;
    }

    /** From the Download tap until commit, no switch may start a command. */
    void reserveForInstall(boolean reserved) {
        installReserved = reserved;
        if (reserved) {
            fixToggle.setEnabled(false);
            dashboardFixToggle.setEnabled(false);
            lidGuardToggle.setEnabled(false);
        }
    }

    /**
     * Mirrors the saved settings and enables the switches only when the daemon
     * accepts commands. Not called while a display command is in flight.
     */
    void syncFromTelemetry(boolean bootHeld, boolean watcherReady) {
        suppressToggle = true;
        fixToggle.setChecked(desiredDisplayFix());
        suppressToggle = false;
        if (!lidCommandInFlight) {
            boolean desiredGuard = desiredLidGuard();
            if (lidGuardToggle.isChecked() != desiredGuard) {
                suppressLidToggle = true;
                lidGuardToggle.setChecked(desiredGuard);
                suppressLidToggle = false;
            }
        }
        fixToggle.setEnabled(!bootHeld && watcherReady && !commandInFlight && !installReserved);
        dashboardFixToggle.setEnabled(!bootHeld && watcherReady && !dashboardCommandInFlight
                && !installReserved);
        lidGuardToggle.setEnabled(!bootHeld && !lidCommandInFlight && !installReserved);
    }

    void shutdown() {
        commandExecutor.shutdown();
    }

    private void confirmDashboardCpuFix(boolean requested) {
        String message = requested
                ? "Android restarts now to apply. On future boots, the required display recovery happens during startup."
                : "Android restarts now to remove the CPU Fix. Open apps will close.";
        new AlertDialog.Builder(activity)
                .setTitle(requested ? "Enable Dashboard CPU Fix?" : "Disable Dashboard CPU Fix?")
                .setMessage(message)
                .setNegativeButton("CANCEL", (dialog, which) -> restoreDashboardToggle())
                .setOnCancelListener(dialog -> restoreDashboardToggle())
                .setPositiveButton(requested ? "RESTART & ENABLE" : "RESTART & DISABLE",
                        (dialog, which) -> setDashboardCpuFixEnabled(requested))
                .show();
    }

    private void restoreDashboardToggle() {
        suppressDashboardToggle = true;
        dashboardFixToggle.setChecked(desiredCpuFix());
        suppressDashboardToggle = false;
        updateDashboardFixHelp(dashboardFixToggle.isChecked());
    }

    private void setDashboardCpuFixEnabled(boolean requested) {
        if (dashboardCommandInFlight) return;
        if (installReserved) {
            restoreDashboardToggle();
            toast("Update in progress", Toast.LENGTH_SHORT);
            return;
        }
        final boolean previous = desiredCpuFix();
        dashboardCommandInFlight = true;
        dashboardFixToggle.setEnabled(false);
        feedback.cpuFixCommandStarted();
        commandExecutor.execute(() -> {
            try {
                if (!preferences.edit().putBoolean(CPU_FIX, requested).commit()) {
                    throw new IllegalStateException("CPU Fix preference could not be saved");
                }
                try {
                    EarlyCpuOptIn.setEnabled(activity, requested);
                } catch (Throwable error) {
                    preferences.edit().putBoolean(CPU_FIX, previous).commit();
                    try {
                        EarlyCpuOptIn.setEnabled(activity, previous);
                    } catch (Throwable rollbackError) {
                        android.util.Log.e("ThorDisplay",
                                "early CPU opt-in rollback failed", rollbackError);
                    }
                    throw new IllegalStateException(
                            "ok=0;error=EARLY_CPU_OPTIN_SYNC_FAILED", error);
                }
                Map<String, String> result = TelemetryValues.parse(
                        SocketClient.request(requested ? 'R' : 'L', 1800));
                if (!"1".equals(result.get("ok"))) throw new IllegalStateException("Command not accepted");
                final boolean restart = "scheduled_once".equals(result.get("composer_restart"));
                activity.runOnUiThread(() -> {
                    dashboardCommandInFlight = false;
                    dashboardFixToggle.setEnabled(true);
                    updateDashboardFixHelp(requested);
                    toast(restart ? "Android restart pending"
                            : "CPU Fix confirmed without restart", Toast.LENGTH_LONG);
                });
            } catch (Throwable error) {
                final boolean rejected = rejected(error);
                if (rejected) {
                    preferences.edit().putBoolean(CPU_FIX, previous).commit();
                    try {
                        EarlyCpuOptIn.setEnabled(activity, previous);
                    } catch (Throwable rollbackError) {
                        android.util.Log.e("ThorDisplay",
                                "early CPU opt-in rollback failed", rollbackError);
                    }
                }
                activity.runOnUiThread(() -> {
                    dashboardCommandInFlight = false;
                    dashboardFixToggle.setEnabled(true);
                    restoreDashboardToggle();
                    toast(rejected ? "CPU Fix change rejected"
                            : "CPU Fix status unknown; checking again", Toast.LENGTH_LONG);
                });
            }
        });
    }

    private void setFixEnabled(final boolean requested) {
        if (commandInFlight) return;
        if (installReserved) {
            suppressToggle = true;
            fixToggle.setChecked(desiredDisplayFix());
            suppressToggle = false;
            toast("Update in progress", Toast.LENGTH_SHORT);
            return;
        }
        final boolean previous = desiredDisplayFix();
        commandInFlight = true;
        fixToggle.setEnabled(false);
        feedback.displayCommandStarted();
        commandExecutor.execute(() -> {
            try {
                if (!preferences.edit().putBoolean(DISPLAY_FIX, requested).commit()) {
                    throw new IllegalStateException("Display Fix preference could not be saved");
                }
                SocketClient.request(requested ? 'E' : 'N', 1500);
                activity.runOnUiThread(() -> finishToggle(requested, null, false));
            } catch (Throwable error) {
                final boolean rejected = rejected(error);
                if (rejected && !preferences.edit().putBoolean(DISPLAY_FIX, previous).commit()) {
                    android.util.Log.e("ThorDisplay", "display preference rollback failed", error);
                }
                activity.runOnUiThread(() -> finishToggle(rejected ? previous : requested,
                        error.getMessage(), !rejected));
            }
        });
    }

    private void setLidGuardEnabled(final boolean requested) {
        if (lidCommandInFlight) return;
        if (installReserved) {
            suppressLidToggle = true;
            lidGuardToggle.setChecked(desiredLidGuard());
            suppressLidToggle = false;
            toast("Update in progress", Toast.LENGTH_SHORT);
            return;
        }
        final boolean previous = desiredLidGuard();
        lidCommandInFlight = true;
        lidGuardToggle.setEnabled(false);
        commandExecutor.execute(() -> {
            try {
                if (!preferences.edit().putBoolean(LID_GUARD, requested).commit()) {
                    throw new IllegalStateException("Wake Guard preference could not be saved");
                }
                SocketClient.request(requested ? 'G' : 'H', 1800);
                activity.runOnUiThread(() -> {
                    lidCommandInFlight = false;
                    lidGuardToggle.setEnabled(true);
                });
            } catch (Throwable error) {
                final boolean rejected = rejected(error);
                if (rejected && !preferences.edit().putBoolean(LID_GUARD, previous).commit()) {
                    android.util.Log.e("ThorDisplay", "lid preference rollback failed", error);
                }
                activity.runOnUiThread(() -> {
                    lidCommandInFlight = false;
                    lidGuardToggle.setEnabled(true);
                    suppressLidToggle = true;
                    lidGuardToggle.setChecked(rejected ? previous : requested);
                    suppressLidToggle = false;
                    toast(rejected ? "Wake Guard change rejected"
                            : "Wake Guard status unknown; checking again", Toast.LENGTH_LONG);
                });
            }
        });
    }

    private void finishToggle(boolean desired, String error, boolean uncertain) {
        suppressToggle = true;
        fixToggle.setChecked(desired);
        suppressToggle = false;
        fixToggle.setEnabled(true);
        commandInFlight = false;
        feedback.displayCommandFinished(error, uncertain);
        if (error != null) {
            toast(uncertain ? "Checking display state again"
                    : "Change rejected; setting restored", Toast.LENGTH_LONG);
        }
    }

    private void updateDashboardFixHelp(boolean enabled) {
        if (dashboardFixHelp == null) return;
        dashboardFixHelp.setText(enabled
                ? "Applies during startup; a slightly longer black boot phase is normal"
                : "Restarts Android UI/display once to apply");
        dashboardFixHelp.setTextColor(enabled ? AMBER : MUTED);
    }

    private void toast(String message, int duration) {
        Toast.makeText(activity, message, duration).show();
    }

    /** SocketClient reports a daemon rejection as an exception carrying "ok=0...". */
    private static boolean rejected(Throwable error) {
        return error.getMessage() != null && error.getMessage().startsWith("ok=0");
    }
}
