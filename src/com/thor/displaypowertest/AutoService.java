package com.thor.displaypowertest;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public final class AutoService extends Service {
    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        final android.content.SharedPreferences state = getSharedPreferences("state", MODE_PRIVATE);
        final boolean enabled = state.getBoolean("fix_enabled", true);
        new Thread(new Runnable() {
            @Override public void run() {
                if (!state.getBoolean("daemon_protocol_32", false)) {
                    if (PServer.stopLegacyDaemon()) {
                        try { Thread.sleep(500L); } catch (InterruptedException ignored) {}
                        state.edit().putBoolean("daemon_protocol_32", true).commit();
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
                Log.d("ThorDisplayAuto", "boot reconcile enabled=" + enabled);
                stopSelf();
            }
        }, "thor-start").start();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }
}
