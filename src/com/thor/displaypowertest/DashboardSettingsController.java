package com.thor.displaypowertest;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.widget.Switch;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.thor.displaypowertest.DashboardStyle.AMBER;
import static com.thor.displaypowertest.DashboardStyle.MUTED;

/**
 * Persistent switches and the optional bottom-idle slider. Changes save intent first, then
 * sends one daemon command on a single background thread; a rejected command
 * restores the previous preference, an unanswered one keeps the request and
 * lets telemetry confirm it. UI-thread fields are only touched on that thread;
 * the volatile in-flight flags are also read by AppUpdater's worker.
 */
final class DashboardSettingsController {
    static final String DISPLAY_FIX = "fix_enabled";
    static final String CPU_FIX = "dashboard_cpu_fix_enabled";
    static final String LID_GUARD = "lid_guard_enabled";
    static final String BOTTOM_IDLE = "bottom_idle_enabled";
    static final String BOTTOM_IDLE_MINUTES = "bottom_idle_minutes";

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
    private final Switch bottomIdleToggle;
    private final SeekBar bottomIdleSlider;
    private final TextView bottomIdleValue;
    private final TextView bottomIdleHelp;
    private final TextView dashboardFixHelp;
    private final Feedback feedback;
    private final ExecutorService commandExecutor = Executors.newSingleThreadExecutor();
    private boolean suppressToggle;
    private boolean suppressDashboardToggle;
    private boolean suppressLidToggle;
    private boolean suppressIdleControls;
    private volatile boolean commandInFlight;
    private volatile boolean dashboardCommandInFlight;
    private volatile boolean lidCommandInFlight;
    private volatile boolean idleCommandInFlight;
    private final Runnable commitIdleSlider;
    private volatile boolean installReserved;
    private volatile boolean activationPending = true;

    DashboardSettingsController(Activity activity, SharedPreferences preferences,
            DashboardLayout layout, Feedback feedback) {
        this.activity = activity;
        this.preferences = preferences;
        this.fixToggle = layout.fixToggle;
        this.dashboardFixToggle = layout.dashboardFixToggle;
        this.lidGuardToggle = layout.lidGuardToggle;
        this.bottomIdleToggle = layout.bottomIdleToggle;
        this.bottomIdleSlider = layout.bottomIdleSlider;
        this.commitIdleSlider = () -> {
            int minutes = BottomIdleSettings.minutesAt(bottomIdleSlider.getProgress());
            if (minutes != desiredBottomIdleMinutes())
                changeIdle(desiredBottomIdle(), minutes);
        };
        this.bottomIdleValue = layout.bottomIdleValue;
        this.bottomIdleHelp = layout.bottomIdleHelp;
        this.dashboardFixHelp = layout.dashboardFixHelp;
        this.feedback = feedback;
    }

    void setActivationPending(boolean pending) {
        activationPending = pending;
        if (pending) {
            fixToggle.setEnabled(false);
            dashboardFixToggle.setEnabled(false);
            lidGuardToggle.setEnabled(false);
            bottomIdleToggle.setEnabled(false);
            bottomIdleSlider.setEnabled(false);
        }
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
        suppressIdleControls = true;
        bottomIdleToggle.setChecked(desiredBottomIdle());
        bottomIdleSlider.setProgress(BottomIdleSettings.indexOf(desiredBottomIdleMinutes()));
        setIdleValue(bottomIdleSlider.getProgress());
        suppressIdleControls = false;
        bottomIdleToggle.setOnCheckedChangeListener((button, checked) -> {
            if (!suppressIdleControls) changeIdle(checked, desiredBottomIdleMinutes());
        });
        bottomIdleSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                setIdleValue(progress);
                // D-pad/accessibility adjustments have no stop-tracking callback.
                // Coalesce them without sending IPC for every slider increment.
                if (fromUser && !suppressIdleControls && !bar.isPressed()) {
                    bar.removeCallbacks(commitIdleSlider);
                    bar.postDelayed(commitIdleSlider, 350L);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {
                bar.removeCallbacks(commitIdleSlider);
                if (!suppressIdleControls) commitIdleSlider.run();
            }
        });
        setActivationPending(true);
    }

    boolean desiredBottomIdle() { return preferences.getBoolean(BOTTOM_IDLE, false); }
    int desiredBottomIdleMinutes() {
        int saved = preferences.getInt(BOTTOM_IDLE_MINUTES,
                BottomIdleSettings.minutesAt(BottomIdleSettings.DEFAULT_INDEX));
        return BottomIdleSettings.validMinutes(saved) ? saved
                : BottomIdleSettings.minutesAt(BottomIdleSettings.DEFAULT_INDEX);
    }

    boolean desiredDisplayFix() { return preferences.getBoolean(DISPLAY_FIX, true); }
    boolean desiredCpuFix() { return preferences.getBoolean(CPU_FIX, false); }
    boolean desiredLidGuard() { return preferences.getBoolean(LID_GUARD, false); }

    boolean displayCommandInFlight() { return commandInFlight; }

    boolean anyCommandInFlight() {
        return commandInFlight || dashboardCommandInFlight || lidCommandInFlight
                || idleCommandInFlight;
    }

    /** From the Download tap until commit, no switch may start a command. */
    void reserveForInstall(boolean reserved) {
        installReserved = reserved;
        if (reserved) {
            fixToggle.setEnabled(false);
            dashboardFixToggle.setEnabled(false);
            lidGuardToggle.setEnabled(false);
            bottomIdleToggle.setEnabled(false);
            bottomIdleSlider.setEnabled(false);
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
        fixToggle.setEnabled(!activationPending && !bootHeld && watcherReady && !commandInFlight && !installReserved);
        dashboardFixToggle.setEnabled(!activationPending && !bootHeld && watcherReady && !dashboardCommandInFlight
                && !installReserved);
        lidGuardToggle.setEnabled(!activationPending && !bootHeld && !lidCommandInFlight && !installReserved);
        if (!idleCommandInFlight) {
            suppressIdleControls = true;
            bottomIdleToggle.setChecked(desiredBottomIdle());
            int index = BottomIdleSettings.indexOf(desiredBottomIdleMinutes());
            if (!bottomIdleSlider.isPressed()) bottomIdleSlider.setProgress(index);
            suppressIdleControls = false;
        }
        boolean idleReady = !activationPending && !bootHeld && !idleCommandInFlight && !installReserved;
        bottomIdleToggle.setEnabled(idleReady);
        bottomIdleSlider.setEnabled(idleReady);
    }

    void syncIdleStatus(String inputState) {
        if (!desiredBottomIdle()) {
            bottomIdleHelp.setText("Only in BOTH. Set the timeout, then enable here or in Android Quick Settings.");
            bottomIdleHelp.setTextColor(MUTED);
        } else if ("UNAVAILABLE".equals(inputState)) {
            bottomIdleHelp.setText("Bottom touch monitor unavailable — automatic off will not activate.");
            bottomIdleHelp.setTextColor(AMBER);
        } else if ("SUSPENDED".equals(inputState)) {
            bottomIdleHelp.setText("Waiting for BOTH mode with True Bottom Screen Off enabled.");
            bottomIdleHelp.setTextColor(MUTED);
        } else if ("WAITING_FOR_TOUCH".equals(inputState)) {
            bottomIdleHelp.setText("Touch the bottom screen once in BOTH to start the inactivity timer.");
            bottomIdleHelp.setTextColor(MUTED);
        } else if ("OBSERVED".equals(inputState)) {
            bottomIdleHelp.setText("Timer active in BOTH. Passive video without touch also counts as idle.");
            bottomIdleHelp.setTextColor(MUTED);
        }
    }

    void shutdown() {
        bottomIdleSlider.removeCallbacks(commitIdleSlider);
        commandExecutor.shutdown();
    }

    private void setIdleValue(int index) {
        int minutes = BottomIdleSettings.minutesAt(index);
        bottomIdleValue.setText("OFF AFTER " + minutes + (minutes == 1 ? " MINUTE" : " MINUTES"));
        bottomIdleSlider.setContentDescription(
                "Auto Bottom Off timeout: " + minutes + (minutes == 1 ? " minute" : " minutes"));
    }

    private void restoreIdleControls() {
        suppressIdleControls = true;
        bottomIdleToggle.setChecked(desiredBottomIdle());
        int progress = BottomIdleSettings.indexOf(desiredBottomIdleMinutes());
        bottomIdleSlider.setProgress(progress);
        setIdleValue(progress);
        suppressIdleControls = false;
    }

    /** Only one IPC change when the user releases the slider, never per tick. */
    private void changeIdle(boolean enabled, int minutes) {
        bottomIdleSlider.removeCallbacks(commitIdleSlider);
        if (idleCommandInFlight || activationPending || installReserved) {
            restoreIdleControls();
            return;
        }
        // Slider changes must preserve a concurrent QS tile toggle, rather
        // than silently disabling the option with a stale checked snapshot.
        final boolean sliderOnly = enabled == desiredBottomIdle();
        idleCommandInFlight = true;
        bottomIdleToggle.setEnabled(false);
        bottomIdleSlider.setEnabled(false);
        commandExecutor.execute(() -> {
            // Dashboard and tile are in the same Android app process. Serialize
            // preference writes with their matching authenticated daemon ACK.
            synchronized (BottomIdleSettings.class) {
                final boolean previousEnabled = desiredBottomIdle();
                final int previousMinutes = desiredBottomIdleMinutes();
                final boolean targetEnabled = sliderOnly ? previousEnabled : enabled;
                try {
                    if (!preferences.edit().putBoolean(BOTTOM_IDLE, targetEnabled)
                            .putInt(BOTTOM_IDLE_MINUTES, minutes).commit()) {
                        throw new IllegalStateException("ok=0;error=IDLE_PREFERENCE_WRITE_FAILED");
                    }
                    if (targetEnabled || previousEnabled) {
                        SocketClient.request(targetEnabled ? BottomIdleSettings.commandFor(minutes)
                                : BottomIdleSettings.DISABLE_COMMAND, 1800);
                    }
                    activity.runOnUiThread(() -> finishIdleChange(null));
                } catch (Throwable error) {
                    final boolean rejected = rejected(error);
                    if (rejected && !preferences.edit().putBoolean(BOTTOM_IDLE, previousEnabled)
                            .putInt(BOTTOM_IDLE_MINUTES, previousMinutes).commit()) {
                        android.util.Log.e("ThorIdle", "preference rollback failed", error);
                    }
                    activity.runOnUiThread(() -> finishIdleChange(rejected
                            ? "Auto Bottom Off rejected; setting restored"
                            : "Auto Bottom Off status uncertain; check again"));
                }
            }
        });
    }

    private void finishIdleChange(String message) {
        idleCommandInFlight = false;
        restoreIdleControls();
        boolean ready = !activationPending && !installReserved;
        bottomIdleToggle.setEnabled(ready);
        bottomIdleSlider.setEnabled(ready);
        BottomIdleTileService.requestRefresh(activity);
        if (message != null) toast(message, Toast.LENGTH_LONG);
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
        if (installReserved || activationPending) {
            restoreDashboardToggle();
            toast(activationPending ? "Background service activation pending" : "Update in progress", Toast.LENGTH_SHORT);
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
                    dashboardFixToggle.setEnabled(!activationPending && !installReserved);
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
                    dashboardFixToggle.setEnabled(!activationPending && !installReserved);
                    restoreDashboardToggle();
                    toast(rejected ? "CPU Fix change rejected"
                            : "CPU Fix status unknown; checking again", Toast.LENGTH_LONG);
                });
            }
        });
    }

    private void setFixEnabled(final boolean requested) {
        if (commandInFlight) return;
        if (installReserved || activationPending) {
            suppressToggle = true;
            fixToggle.setChecked(desiredDisplayFix());
            suppressToggle = false;
            toast(activationPending ? "Background service activation pending" : "Update in progress", Toast.LENGTH_SHORT);
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
        if (installReserved || activationPending) {
            suppressLidToggle = true;
            lidGuardToggle.setChecked(desiredLidGuard());
            suppressLidToggle = false;
            toast(activationPending ? "Background service activation pending" : "Update in progress", Toast.LENGTH_SHORT);
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
                    lidGuardToggle.setEnabled(!activationPending && !installReserved);
                });
            } catch (Throwable error) {
                final boolean rejected = rejected(error);
                if (rejected && !preferences.edit().putBoolean(LID_GUARD, previous).commit()) {
                    android.util.Log.e("ThorDisplay", "lid preference rollback failed", error);
                }
                activity.runOnUiThread(() -> {
                    lidCommandInFlight = false;
                    lidGuardToggle.setEnabled(!activationPending && !installReserved);
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
        fixToggle.setEnabled(!activationPending && !installReserved);
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
                ? "Applies at startup. Manual activation restarts the Android UI, which may look like a reboot."
                : "Applies at startup. Manual activation restarts the Android UI, which may look like a reboot.");
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
