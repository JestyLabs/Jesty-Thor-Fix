package com.thor.displaypowertest;

import android.os.Looper;
import android.os.SystemClock;
import android.view.InputChannel;
import android.view.InputEvent;
import android.view.InputEventReceiver;
import android.view.MotionEvent;

import java.lang.reflect.Method;

/**
 * Passive, non-consuming input observer. Hidden display-id reflection is
 * resolved once during construction; unsupported firmware fails closed.
 */
final class BottomIdleTouchMonitor extends InputEventReceiver {
    private final Method displayIdMethod;
    private final long monitorEpoch;
    private long lastMoveAt = -1L;

    BottomIdleTouchMonitor(InputChannel channel, Looper looper, long epoch) {
        super(channel, looper);
        monitorEpoch = epoch;
        try {
            Method found;
            try {
                found = InputEvent.class.getDeclaredMethod("getDisplayId");
            } catch (NoSuchMethodException missing) {
                found = MotionEvent.class.getDeclaredMethod("getDisplayId");
            }
            found.setAccessible(true);
            displayIdMethod = found;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("display-id API unavailable; idle touch monitor disabled",
                    failure);
        }
    }

    @Override public void onInputEvent(InputEvent event) {
        try {
            if (!(event instanceof MotionEvent)) return;
            MotionEvent touch = (MotionEvent) event;
            int action = touch.getActionMasked();
            long now = SystemClock.elapsedRealtime();
            if (action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_POINTER_DOWN
                    && action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_MOVE) return;
            if (action == MotionEvent.ACTION_MOVE && lastMoveAt >= 0L
                    && now - lastMoveAt < 2000L) return;
            // The callback must NOT assume that a global event belongs to display 4.
            Object displayId = displayIdMethod.invoke(event);
            if (!(displayId instanceof Integer)
                    || ((Integer) displayId).intValue()
                            != ThorHardwareProfile.BOTTOM_LOGICAL_DISPLAY_ID) return;
            if (action == MotionEvent.ACTION_MOVE) lastMoveAt = now;
            BottomIdleRuntime.onBottomTouch(now, monitorEpoch);
        } catch (Throwable failure) {
            // A failed reflective read may never count as proof of inactivity.
            BottomIdleRuntime.onInputFailure(monitorEpoch);
        } finally {
            finishInputEvent(event, false);
        }
    }
}
