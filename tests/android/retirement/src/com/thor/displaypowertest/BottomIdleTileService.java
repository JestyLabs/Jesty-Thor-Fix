package com.thor.displaypowertest;

/** Retirement fixture only. The Android QS tile is never created here. */
final class BottomIdleTileService {
    static void requestRefresh(android.content.Context context) {
        // A display-independent no-op; no TileService, Binder or QS callbacks.
    }
}
