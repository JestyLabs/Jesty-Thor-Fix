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
                try {
                    // Ensure the app-owned directory exists before the root daemon binds.
                    getFilesDir();
                    boolean protocolMigration = !state.getBoolean("daemon_protocol_unix_v2", false);
                    boolean replacingSecureDaemon = false;
                    boolean healthy = PServer.healthy(enabled);
                    if (!healthy && PServer.secureSocketExists()) {
                        for (int poll = 0; poll < 150 && !healthy; poll++) {
                            try { Thread.sleep(200L); } catch (InterruptedException ignored) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                            healthy = PServer.healthy(enabled);
                        }
                        if (!healthy && PServer.reachable()) {
                            if (!PServer.stopPreviousSecureDaemonIfSafe()) {
                                PServer.logHealthFailure();
                                Log.e("ThorDisplayAuto", "existing trusted daemon is unhealthy; launch held");
                                return;
                            }
                            for (int poll = 0; poll < 25 && PServer.reachable(); poll++) {
                                try { Thread.sleep(200L); } catch (InterruptedException ignored) {
                                    Thread.currentThread().interrupt();
                                    return;
                                }
                            }
                            if (PServer.reachable()) {
                                Log.e("ThorDisplayAuto", "older daemon still listening; replacement held");
                                return;
                            }
                            replacingSecureDaemon = true;
                        }
                        if (!healthy) {
                            Log.w("ThorDisplayAuto", "secure socket is unreachable; checking for a stale inode");
                        }
                    }
                    if (!healthy && PServer.reachable()) {
                        Log.e("ThorDisplayAuto", "trusted daemon reachable but unhealthy; launch held");
                        return;
                    }
                    if (protocolMigration && !healthy) {
                        PServer.stopLegacyDaemon();
                        for (int attempt = 0; attempt < 25 && PServer.legacyTcpOpen(); attempt++) {
                            try { Thread.sleep(200L); } catch (InterruptedException ignored) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                        }
                    }
                    if (PServer.legacyTcpOpen()) {
                        Log.e("ThorDisplayAuto", "legacy TCP listener still present; migration held");
                        return;
                    }
                    boolean held = fromBoot || protocolMigration || replacingSecureDaemon;
                    for (int launch = 0; launch < 2 && !healthy; launch++) {
                        if (!PServer.startDaemon(enabled, held, dashboardFixEnabled,
                                state.getBoolean("lid_guard_enabled", false))) break;
                        for (int poll = 0; poll < 150 && !healthy; poll++) {
                            try { Thread.sleep(200L); } catch (InterruptedException ignored) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                            healthy = PServer.healthy(enabled);
                        }
                        if (!healthy && PServer.reachable()) {
                            Log.e("ThorDisplayAuto", "daemon started but failed health; retry held");
                            break;
                        }
                    }
                    if (!healthy) {
                        Log.e("ThorDisplayAuto", "trusted daemon did not pass health check");
                        return;
                    }
                    if (protocolMigration && !state.edit()
                            .putBoolean("daemon_protocol_unix_v2", true).commit()) {
                        Log.e("ThorDisplayAuto", "migration marker could not be saved");
                        return;
                    }
                    Log.d("ThorDisplayAuto", "trusted daemon healthy enabled=" + enabled
                            + " fromBoot=" + fromBoot);
                } finally {
                    workerRunning = false;
                    stopSelf();
                }
            }
        }, "thor-start").start();
        return START_NOT_STICKY;
    }
}
