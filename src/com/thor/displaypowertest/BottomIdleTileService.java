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
        final boolean previous = prefs.getBoolean("bottom_idle_enabled", false);
        final boolean enabled = !previous;
        int saved = prefs.getInt("bottom_idle_minutes",
                BottomIdleSettings.minutesAt(BottomIdleSettings.DEFAULT_INDEX));
        final int minutes = BottomIdleSettings.validMinutes(saved) ? saved
                : BottomIdleSettings.minutesAt(BottomIdleSettings.DEFAULT_INDEX);
        Tile tile = getQsTile();
        if (tile != null) {
            tile.setState(Tile.STATE_UNAVAILABLE);
            tile.updateTile();
        }
        new Thread(() -> {
            try {
                if (!prefs.edit().putBoolean("bottom_idle_enabled", enabled).commit())
                    throw new IllegalStateException("could not save Quick Settings intent");
                SocketClient.request(enabled ? BottomIdleSettings.commandFor(minutes)
                        : BottomIdleSettings.DISABLE_COMMAND, 1800);
            } catch (Throwable failure) {
                prefs.edit().putBoolean("bottom_idle_enabled", previous).commit();
                Log.w(TAG, "Quick Settings change not acknowledged; rolled back", failure);
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
        boolean enabled = getSharedPreferences("state", MODE_PRIVATE)
                .getBoolean("bottom_idle_enabled", false);
        tile.setState(CHANGING.get() ? Tile.STATE_UNAVAILABLE
                : enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }
}
