package com.thor.displaypowertest;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.SystemClock;
import android.provider.Settings;

/** Explicit opt-in boundary snapshots only. No background sleep collection. */
final class PassiveSleepMonitor {
    private static final String ARMED = "passive_sleep_armed";
    private static final String ACTIVE = "passive_sleep_active";
    private static final String START = "passive_sleep_start";
    private static final String END = "passive_sleep_end";
    private final Activity activity;
    private final SharedPreferences preferences;
    PassiveSleepMonitor(Activity activity) {
        this.activity = activity;
        preferences = activity.getSharedPreferences("diagnostics", Context.MODE_PRIVATE);
    }
    boolean arm() {
        return preferences.edit().putBoolean(ARMED, true).putBoolean(ACTIVE, false)
                .remove(START).remove(END).commit();
    }
    boolean cancel() {
        return preferences.edit().putBoolean(ARMED, false).putBoolean(ACTIVE, false)
                .remove(START).commit();
    }
    boolean running() { return preferences.getBoolean(ARMED, false)
            || preferences.getBoolean(ACTIVE, false); }
    void onPause() {
        if (!preferences.getBoolean(ARMED, false) || preferences.getBoolean(ACTIVE, false)) return;
        PassiveSleepTrial.Sample first = capture();
        // Durable before the process can be killed while the Activity is paused.
        preferences.edit().putString(START, first.encode()).putBoolean(ACTIVE, true)
                .putBoolean(ARMED, false).commit();
    }
    void onResume() {
        if (!preferences.getBoolean(ACTIVE, false)) return;
        PassiveSleepTrial.Sample last = capture();
        // Atomic completion prevents recording another endpoint on a later resume.
        preferences.edit().putString(END, last.encode()).putBoolean(ACTIVE, false).commit();
    }
    PassiveSleepTrial.Result result() {
        PassiveSleepTrial.Sample start = PassiveSleepTrial.Sample.decode(
                preferences.getString(START, null));
        PassiveSleepTrial.Sample end = PassiveSleepTrial.Sample.decode(
                preferences.getString(END, null));
        return start != null && end != null ? PassiveSleepTrial.evaluate(start, end) : null;
    }
    String description() {
        if (preferences.getBoolean(ARMED, false)) return "Armed: leave the dashboard to start.";
        if (preferences.getBoolean(ACTIVE, false)) return "Running until the dashboard resumes.";
        PassiveSleepTrial.Result r = result();
        return r != null ? r.describe()
                : "No completed trial. Arm the trial, leave the app, let the Thor sleep unplugged, "
                + "then return. No periodic background sampling occurs.";
    }
    private PassiveSleepTrial.Sample capture() {
        long energy = -1, charge = -1;
        try {
            BatteryManager battery = (BatteryManager) activity.getSystemService(Context.BATTERY_SERVICE);
            if (battery != null) {
                energy = battery.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER);
                charge = battery.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
            }
        } catch (RuntimeException ignored) { }
        if (energy <= 0) energy = -1;
        if (charge < 0) charge = -1;
        int voltage = -1, temp = -1, plugged = -1;
        try {
            Intent batteryIntent = activity.registerReceiver(null,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (batteryIntent != null) {
                voltage = batteryIntent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
                temp = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
                plugged = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
            }
        } catch (RuntimeException ignored) { }
        int bootCount = -1;
        try {
            bootCount = Settings.Global.getInt(activity.getContentResolver(),
                    Settings.Global.BOOT_COUNT, -1);
        } catch (RuntimeException ignored) { }
        SharedPreferences state = activity.getSharedPreferences("state", Context.MODE_PRIVATE);
        int fixes = (state.getBoolean(DashboardSettingsController.DISPLAY_FIX, true) ? 1 : 0)
                | (state.getBoolean(DashboardSettingsController.CPU_FIX, false) ? 2 : 0)
                | (state.getBoolean(DashboardSettingsController.LID_GUARD, false) ? 4 : 0);
        // Read both clocks close together. These are Activity-boundary samples.
        return new PassiveSleepTrial.Sample(SystemClock.elapsedRealtime(),
                SystemClock.uptimeMillis(), energy, charge, voltage, temp, plugged,
                bootCount, fixes);
    }
}
