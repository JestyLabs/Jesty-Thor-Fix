package com.thor.displaypowertest;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import com.thor.displaypowertest.DaemonLaunchModel.Probe;

public final class AutoService extends Service {
    private static final String TAG = "ThorDisplayAuto";
    private volatile boolean workerRunning;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onCreate() {
        super.onCreate();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        final long serviceAt = SystemClock.elapsedRealtime();
        final long receiverAt = intent == null ? -1L
                : intent.getLongExtra("receiver_elapsed_ms", -1L);
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
                    Log.d(TAG, "start worker fromBoot=" + fromBoot
                            + " receiver_ms=" + receiverAt + " service_ms=" + serviceAt);
                    boolean protocolMigration = !state.getBoolean("daemon_protocol_unix_v2", false);
                    boolean replacingSecureDaemon = false;
                    String socketState = "NOT_CHECKED";
                    Probe probe = PServer.probe(enabled);
                    if (probe == Probe.UNREACHABLE) {
                        // A filesystem socket survives power-off. Only an inode that
                        // may belong to this kernel boot earns a short bounded grace.
                        DaemonLaunchModel.SocketState socket = PServer.socketState();
                        socketState = socket.name();
                        int grace = DaemonLaunchModel.unreachableGracePolls(socket);
                        Log.d(TAG, "secure socket " + socket + "; grace_polls=" + grace
                                + " elapsed_ms=" + SystemClock.elapsedRealtime());
                        if (socket == DaemonLaunchModel.SocketState.ALIVE) {
                            probe = PServer.probe(enabled);
                        }
                        for (int poll = 0; poll < grace && probe == Probe.UNREACHABLE; poll++) {
                            if (!pause()) return;
                            probe = PServer.probe(enabled);
                        }
                    }
                    if ((probe == Probe.STARTING || probe == Probe.UNHEALTHY)
                            && PServer.stopReplaceableSecureDaemonIfSafe(enabled)) {
                        // A failed handover or a stalled boot phase would
                        // otherwise hold display actions until the next reboot.
                        for (int poll = 0; poll < 25 && PServer.reachable(); poll++) {
                            if (!pause()) return;
                        }
                        if (PServer.reachable()) {
                            Log.e(TAG, "held daemon still listening; replacement held");
                            return;
                        }
                        Log.w(TAG, "held same-version daemon stopped; relaunching in hold");
                        replacingSecureDaemon = true;
                        probe = Probe.UNREACHABLE;
                    }
                    if (probe == Probe.UNHEALTHY) {
                        for (int poll = 0; poll < 150 && probe == Probe.UNHEALTHY; poll++) {
                            if (!pause()) return;
                            probe = PServer.probe(enabled);
                        }
                        if (probe == Probe.UNHEALTHY) {
                            if (!PServer.stopPreviousSecureDaemonIfSafe()) {
                                PServer.logHealthFailure();
                                Log.e(TAG, "existing trusted daemon is unhealthy; launch held");
                                return;
                            }
                            for (int poll = 0; poll < 25 && PServer.reachable(); poll++) {
                                if (!pause()) return;
                            }
                            if (PServer.reachable()) {
                                Log.e(TAG, "older daemon still listening; replacement held");
                                return;
                            }
                            replacingSecureDaemon = true;
                            probe = Probe.UNREACHABLE;
                        }
                    }
                    if (protocolMigration && probe == Probe.UNREACHABLE) {
                        PServer.stopLegacyDaemon();
                        for (int attempt = 0; attempt < 25 && PServer.legacyTcpOpen(); attempt++) {
                            if (!pause()) return;
                        }
                    }
                    if (PServer.legacyTcpOpen()) {
                        Log.e(TAG, "legacy TCP listener still present; migration held");
                        return;
                    }
                    boolean held = fromBoot || protocolMigration || replacingSecureDaemon;
                    for (int launch = 0; launch < 2 && probe == Probe.UNREACHABLE; launch++) {
                        long launchWait = SystemClock.elapsedRealtime() - serviceAt;
                        Log.d(TAG, "launching daemon socket=" + socketState
                                + " launch_wait_ms=" + launchWait);
                        if (!PServer.startDaemon(enabled, held, dashboardFixEnabled,
                                state.getBoolean("lid_guard_enabled", false),
                                receiverAt, serviceAt, socketState, launchWait)) break;
                        for (int poll = 0; poll < 150 && probe != Probe.HEALTHY
                                && probe != Probe.STARTING; poll++) {
                            if (!pause()) return;
                            probe = PServer.probe(enabled);
                        }
                        if (probe == Probe.UNHEALTHY) {
                            Log.e(TAG, "daemon started but failed health; retry held");
                            break;
                        }
                    }
                    if (probe != Probe.HEALTHY && probe != Probe.STARTING) {
                        Log.e(TAG, "trusted daemon did not pass health check");
                        return;
                    }
                    // STARTING is an authenticated same-version, same-intent daemon
                    // inside its boot coordinator; it owns the remaining transition.
                    if (protocolMigration && !state.edit()
                            .putBoolean("daemon_protocol_unix_v2", true).commit()) {
                        Log.e(TAG, "migration marker could not be saved");
                        return;
                    }
                    Log.d(TAG, (probe == Probe.STARTING
                            ? "trusted daemon boot coordinator active"
                            : "trusted daemon healthy") + " enabled=" + enabled
                            + " fromBoot=" + fromBoot
                            + " elapsed_ms=" + SystemClock.elapsedRealtime());
                } finally {
                    workerRunning = false;
                    stopSelf();
                }
            }
        }, "thor-start").start();
        return START_NOT_STICKY;
    }

    private static boolean pause() {
        try {
            Thread.sleep(200L);
            return true;
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
