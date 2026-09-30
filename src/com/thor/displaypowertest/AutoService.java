package com.thor.displaypowertest;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public final class AutoService extends Service {
    private volatile boolean workerRunning;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onCreate() {
        super.onCreate();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        final boolean fromBoot = intent != null && intent.getBooleanExtra("from_boot", false);
        if (workerRunning) return START_NOT_STICKY;
        workerRunning = true;
        final android.content.SharedPreferences state = getSharedPreferences("state", MODE_PRIVATE);
        final boolean enabled = state.getBoolean("fix_enabled", true);
        final boolean dashboardFixEnabled = state.getBoolean("dashboard_cpu_fix_enabled", false);
        new Thread(new Runnable() {
            @Override public void run() {
                boolean protocolMigration = !state.getBoolean("daemon_protocol_48", false);
                // A display-framework restart can send BOOT_COMPLETED again in
                // the same kernel boot. Do not kill the already staged daemon:
                // startDaemon below is port-guarded and only rescues a missing one.
                if (protocolMigration) {
                    if (PServer.stopLegacyDaemon()) {
                        try { Thread.sleep(500L); } catch (InterruptedException ignored) {}
                    }
                    if (protocolMigration) {
                        state.edit().putBoolean("daemon_protocol_48", true).commit();
                    }
                }
                boolean held = fromBoot || protocolMigration;
                boolean started = PServer.startDaemon(enabled, held,
                        dashboardFixEnabled, state.getBoolean("lid_guard_enabled", false));
                if (!started) {
                    try { Thread.sleep(40L); } catch (InterruptedException ignored) {}
                    PServer.startDaemon(enabled, held, dashboardFixEnabled,
                            state.getBoolean("lid_guard_enabled", false));
                }
                // The daemon owns the staged boot sequence. Sending E here before
                // its mode watcher knows TOP caused an unwanted lower-panel ON.
                Log.d("ThorDisplayAuto", "reconcile enabled=" + enabled + " fromBoot=" + fromBoot);
                workerRunning = false;
                stopSelf();
            }
        }, "thor-start").start();
        return START_NOT_STICKY;
    }
}
