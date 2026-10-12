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
    private final PassiveSleepSession session;
    PassiveSleepMonitor(Activity activity) {
        this.activity = activity;
        final SharedPreferences preferences =
                activity.getSharedPreferences("diagnostics", Context.MODE_PRIVATE);
        session = new PassiveSleepSession(new PassiveSleepSession.Store() {
            public boolean arm() {
                return preferences.edit().putBoolean(ARMED, true).putBoolean(ACTIVE, false)
                        .remove(START).remove(END).commit();
            }
            public boolean cancel() {
                return preferences.edit().putBoolean(ARMED, false).putBoolean(ACTIVE, false)
                        .remove(START).commit();
            }
            public boolean start(String sample) {
                return preferences.edit().putString(START, sample).putBoolean(ACTIVE, true)
                        .putBoolean(ARMED, false).commit();
            }
            public boolean finish(String sample) {
                return preferences.edit().putString(END, sample).putBoolean(ACTIVE, false)
                        .commit();
            }
            public boolean armed() { return preferences.getBoolean(ARMED, false); }
            public boolean active() { return preferences.getBoolean(ACTIVE, false); }
            public String startSample() { return preferences.getString(START, null); }
            public String endSample() { return preferences.getString(END, null); }
        }, new PassiveSleepPersistence());
    }
    boolean arm() { return session.arm(); }
    boolean cancel() { return session.cancel(); }
    boolean running() { return session.running(); }
    void onPause() { session.onPause(this::capture); }
    void onResume() { session.onResume(this::capture); }
    PassiveSleepTrial.Result result() { return session.result(); }
    String description() { return session.description(); }
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
