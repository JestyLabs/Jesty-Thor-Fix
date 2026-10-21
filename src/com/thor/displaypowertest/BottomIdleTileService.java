package com.thor.displaypowertest;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;

/** User-added Quick Settings control; the tile itself never touches hardware. */
public final class BottomIdleTileService extends TileService {
    private static final String TAG = "ThorIdleTile";
    private static final AtomicBoolean CHANGING = new AtomicBoolean();

    @Override public void onStartListening() {
        super.onStartListening();
        refresh();
    }

    @Override public void onClick() {
        super.onClick();
        if (!CHANGING.compareAndSet(false, true)) return;
        final SharedPreferences prefs = getSharedPreferences("state", MODE_PRIVATE);
        Tile tile = getQsTile();
        if (tile != null) {
            tile.setState(Tile.STATE_UNAVAILABLE);
            tile.updateTile();
        }
        new Thread(() -> {
            try {
                synchronized (BottomIdleSettings.class) {
                    final boolean previous = prefs.getBoolean("bottom_idle_enabled", false);
                    final boolean enabled = !previous;
                    int saved = prefs.getInt("bottom_idle_minutes",
                            BottomIdleSettings.minutesAt(BottomIdleSettings.DEFAULT_INDEX));
                    int minutes = BottomIdleSettings.validMinutes(saved) ? saved
                            : BottomIdleSettings.minutesAt(BottomIdleSettings.DEFAULT_INDEX);
                    try {
                        if (!prefs.edit().putBoolean("bottom_idle_enabled", enabled).commit())
                            throw new IllegalStateException("ok=0;error=IDLE_PREFERENCE_WRITE_FAILED");
                        SocketClient.request(enabled ? BottomIdleSettings.commandFor(minutes)
                                : BottomIdleSettings.DISABLE_COMMAND, 1800);
                    } catch (Throwable failure) {
                        // No response is NOT an explicit rejection: the daemon
                        // may already have applied it. Keep intent for recovery.
                        if (failure.getMessage() != null
                                && failure.getMessage().startsWith("ok=0")) {
                            if (!prefs.edit().putBoolean("bottom_idle_enabled", previous).commit())
                                Log.e(TAG, "Quick Settings rollback could not be persisted");
                        }
                        Log.w(TAG, "Quick Settings command not confirmed", failure);
                    }
                }
            } finally {
                CHANGING.set(false);
                requestRefresh(getApplicationContext());
                new Handler(Looper.getMainLooper()).post(this::refresh);
            }
        }, "thor-idle-tile").start();
    }

    /** Keep an already-visible tile in sync after dashboard changes. */
    static void requestRefresh(Context context) {
        try {
            TileService.requestListeningState(context.getApplicationContext(),
                    new ComponentName(context, BottomIdleTileService.class));
        } catch (RuntimeException error) {
            Log.w(TAG, "Quick Settings refresh unavailable", error);
        }
    }

    private void refresh() {
        Tile tile = getQsTile();
        if (tile == null) return;
        SharedPreferences prefs = getSharedPreferences("state", MODE_PRIVATE);
        boolean enabled = prefs.getBoolean("bottom_idle_enabled", false);
        tile.setState(CHANGING.get() ? Tile.STATE_UNAVAILABLE
                : enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        // The timer only acts with True Bottom Screen Off; an ACTIVE tile alone
        // would suggest it is working. The tile stays clickable to turn it off.
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            tile.setSubtitle(enabled && !prefs.getBoolean("fix_enabled", true)
                    ? "Needs True Off" : null);
        }
        tile.updateTile();
    }
}
