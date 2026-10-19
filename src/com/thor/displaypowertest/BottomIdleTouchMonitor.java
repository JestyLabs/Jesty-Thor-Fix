package com.thor.displaypowertest;

import android.os.Looper;
import android.os.SystemClock;
import android.view.InputChannel;
import android.view.InputEvent;
import android.view.InputEventReceiver;
import android.view.MotionEvent;

/**
 * Passive listener for *one* logical display, not a global accessibility
 * service, event injector or another poller. All events are unhandled.
 */
final class BottomIdleTouchMonitor extends InputEventReceiver {
    private long lastMoveAt;

    BottomIdleTouchMonitor(InputChannel channel, Looper looper) {
        super(channel, looper);
    }

    @Override public void onInputEvent(InputEvent event) {
        try {
            if (!(event instanceof MotionEvent)) return;
            MotionEvent touch = (MotionEvent) event;
            if (touch.getDisplayId() != ThorHardwareProfile.BOTTOM_LOGICAL_DISPLAY_ID) return;
            int action = touch.getActionMasked();
            long now = SystemClock.elapsedRealtime();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN
                    || action == MotionEvent.ACTION_UP
                    || (action == MotionEvent.ACTION_MOVE && now - lastMoveAt >= 2000L)) {
                if (action == MotionEvent.ACTION_MOVE) lastMoveAt = now;
                BottomIdleRuntime.onBottomTouch(now);
            }
        } finally {
            finishInputEvent(event, false);
        }
    }
}
