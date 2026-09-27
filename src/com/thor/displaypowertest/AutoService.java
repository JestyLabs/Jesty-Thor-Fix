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
                boolean protocolMigration = !state.getBoolean("daemon_protocol_45", false);
                if (fromBoot || protocolMigration) {
                    if (PServer.stopLegacyDaemon()) {
                        try { Thread.sleep(500L); } catch (InterruptedException ignored) {}
                    }
                    if (protocolMigration) {
                        state.edit().putBoolean("daemon_protocol_45", true).commit();
                    }
                }
                boolean started = PServer.startDaemon(enabled);
                if (!started) {
                    try { Thread.sleep(40L); } catch (InterruptedException ignored) {}
                    PServer.startDaemon(enabled);
                }
                for (int attempt = 0; attempt < 20; attempt++) {
                    try {
                        SocketClient.request(enabled ? 'E' : 'N', 500);
                        break;
                    } catch (Throwable ignored) {
                        try { Thread.sleep(100L); } catch (InterruptedException interrupted) { break; }
                    }
                }
                if (fromBoot) {
                    for (int attempt = 0; attempt < 8; attempt++) {
                        try {
                            String result = SocketClient.request(dashboardFixEnabled ? 'R' : 'L', 1800);
                            if (result != null && result.startsWith("ok=1")) {
                                Log.d("ThorDisplayAuto", "boot dashboard CPU reconcile=" + result);
                                break;
                            }
                        } catch (Throwable ignored) {
                            try { Thread.sleep(500L); } catch (InterruptedException interrupted) { break; }
                        }
                    }
                }
                Log.d("ThorDisplayAuto", "reconcile enabled=" + enabled + " fromBoot=" + fromBoot);
                workerRunning = false;
                stopSelf();
            }
        }, "thor-start").start();
        return START_NOT_STICKY;
    }
}
