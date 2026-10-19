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
        final RebootActivation.Ticket activation = RebootActivation.ticket(this, state);
        final boolean bootAuthorized = RebootActivation.allowStart(this, state);
        if (!bootAuthorized && activation.evidence() != RebootActivation.Evidence.RESTART_REQUIRED) {
            workerRunning = false;
            Log.w(TAG, "activation blocked: missing durable identity or storage proof");
            stopSelf();
            return START_NOT_STICKY;
        }
        final boolean liveHandover = !bootAuthorized;
        final boolean enabled = state.getBoolean("fix_enabled", true);
        final boolean dashboardFixEnabled = state.getBoolean("dashboard_cpu_fix_enabled", false);
        try {
            EarlyCpuOptIn.setEnabled(this, dashboardFixEnabled);
        } catch (Throwable error) {
            Log.e(TAG, "early CPU Direct-Boot opt-in sync failed; normal path retained", error);
        }
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    // Ensure the app-owned directory exists before the root daemon binds.
                    getFilesDir();
                    Log.d(TAG, "start worker fromBoot=" + fromBoot
                            + " receiver_ms=" + receiverAt + " service_ms=" + serviceAt);
                    boolean protocolMigration = !state.getBoolean("daemon_protocol_unix_v2", false);
                    if (liveHandover) {
                        // Only the two exact published predecessors with confirmed BOTH,
                        // CPU/display/lid state may be retired; no blind process kills.
                        int previousPid = PServer.stopLivePredecessorIfSafe(enabled,
                                dashboardFixEnabled, state.getBoolean("lid_guard_enabled", false));
                        if (previousPid < 1) {
                            Log.w(TAG, "live handover held; safe predecessor not verified");
                            return;
                        }
                        for (int poll = 0; poll < 50 && PServer.reachable(); poll++) {
                            if (!pause()) return;
                        }
                        if (PServer.reachable()) {
                            Log.e(TAG, "predecessor still responding; successor launch forbidden");
                            return;
                        }
                    }
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
                    reconcileBottomIdle(state);
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
                        probe = Probe.UNREACHABLE;
                    }
                    if (probe == Probe.UNHEALTHY) {
                        for (int poll = 0; poll < 150 && probe == Probe.UNHEALTHY; poll++) {
                            if (!pause()) return;
                            probe = PServer.probe(enabled);
                        }
                        if (probe == Probe.UNHEALTHY) {
                            PServer.logHealthFailure();
                            Log.e(TAG, "existing daemon cannot activate this package; normal reboot required");
                            return;
                        }
                    }
                    if (PServer.legacyTcpOpen()) {
                        Log.e(TAG, "legacy listener present; normal reboot required");
                        return;
                    }
                    // Foreground recovery after a missed broadcast also enters the boot coordinator.
                    boolean held = true;
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
                    // inside its boot coordinator; its actions remain held until READY.
                    if (liveHandover && !activation.confirmLiveHandover()) {
                        Log.e(TAG, "live handover identity found but durable proof failed");
                        return;
                    }
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

    /**
     * Boot/startup reconciliation is bounded and runs only while the temporary
     * AutoService worker is alive. The daemon never polls application prefs.
     */
    private static void reconcileBottomIdle(android.content.SharedPreferences prefs) {
        boolean desired = prefs.getBoolean("bottom_idle_enabled", false);
        int minutes = prefs.getInt("bottom_idle_minutes",
                BottomIdleSettings.minutesAt(BottomIdleSettings.DEFAULT_INDEX));
        if (!BottomIdleSettings.validMinutes(minutes)) minutes =
                BottomIdleSettings.minutesAt(BottomIdleSettings.DEFAULT_INDEX);
        char command = desired ? BottomIdleSettings.commandFor(minutes)
                : BottomIdleSettings.DISABLE_COMMAND;
        int attempts = desired ? 30 : 1;
        for (int i = 0; i < attempts; i++) {
            try {
                String identity = SocketClient.request('I', 800);
                if (identity.contains(";boot_phase=READY;")) {
                    SocketClient.request(command, 1800);
                    return;
                }
            } catch (Throwable failure) {
                if (i == attempts - 1) {
                    Log.w(TAG, "bottom idle preference could not be reconciled", failure);
                }
            }
            if (i + 1 < attempts) {
                try { Thread.sleep(1500L); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
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
