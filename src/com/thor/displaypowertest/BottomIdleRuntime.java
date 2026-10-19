package com.thor.displaypowertest;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputChannel;

import java.lang.reflect.Method;

/**
 * Optional, event-driven lower-screen inactivity watcher. One dormant Looper
 * exists only while enabled, with a single outstanding deadline. Monitoring
 * must be attached to display 4 and must first OBSERVE a real touch: otherwise
 * silence cannot trigger a display action.
 */
public final class BottomIdleRuntime {
    private static final String TAG = "ThorBottomIdle";
    private static final Object LOCK = new Object();
    private static final BottomIdlePolicy POLICY = new BottomIdlePolicy();
    private static HandlerThread thread;
    private static Handler handler;
    private static BottomIdleTouchMonitor receiver;
    private static Object monitor;
    private static String inputState = "OFF";
    private static final Runnable TIMEOUT = BottomIdleRuntime::onTimeout;

    private BottomIdleRuntime() {}

    public static String configure(int minutes) {
        if (minutes != 0 && !BottomIdleSettings.validMinutes(minutes))
            return "ok=0;error=INVALID_IDLE_TIMEOUT";
        final Handler prior;
        final HandlerThread priorThread;
        final boolean restore;
        synchronized (LOCK) {
            restore = POLICY.suppressNativeRestore();
            POLICY.configure(minutes);
            if (minutes == 0) {
                prior = handler;
                priorThread = thread;
                handler = null;
                thread = null;
                inputState = "OFF";
            } else {
                prior = null;
                priorThread = null;
                POLICY.mode(DaemonState.getMode());
                if (handler == null) {
                    thread = new HandlerThread("thor-bottom-idle");
                    thread.start();
                    handler = new Handler(thread.getLooper());
                    inputState = "CONNECTING";
                    handler.post(BottomIdleRuntime::attachMonitor);
                } else {
                    inputState = receiver == null ? "UNAVAILABLE" : "WAITING_FOR_TOUCH";
                    handler.removeCallbacks(TIMEOUT);
                }
            }
        }
        if (minutes == 0 && prior != null) {
            prior.removeCallbacks(TIMEOUT);
            prior.post(() -> {
                if (restore) DisplayActionCoordinator.restoreIdleBottom();
                closeMonitor();
                if (priorThread != null) priorThread.quitSafely();
            });
        }
        return "ok=1;idle_minutes=" + minutes;
    }

    public static void onModeSample(String mode) {
        synchronized (LOCK) {
            if (!POLICY.enabled()) return;
            if (POLICY.mode(mode)) {
                if (handler != null) handler.removeCallbacks(TIMEOUT);
                inputState = receiver == null ? inputState : "WAITING_FOR_TOUCH";
            }
        }
    }

    /** A display-on notification invalidates pre-sleep evidence and timers. */
    public static void onDisplayWake() {
        synchronized (LOCK) {
            if (!POLICY.enabled()) return;
            POLICY.wake();
            if (handler != null) handler.removeCallbacks(TIMEOUT);
            inputState = receiver == null ? inputState : "WAITING_FOR_TOUCH";
        }
    }

    public static void onBottomTouch(long now) {
        final Handler active;
        final boolean restore;
        synchronized (LOCK) {
            if (receiver == null || !POLICY.enabled()) return;
            restore = POLICY.touch(now);
            if (!POLICY.inputVerified()) return;
            inputState = "OBSERVED";
            active = handler;
            scheduleLocked(now);
        }
        if (restore && active != null) active.post(DisplayActionCoordinator::restoreIdleBottom);
    }

    private static void scheduleLocked(long now) {
        if (handler == null) return;
        handler.removeCallbacks(TIMEOUT);
        long delay = POLICY.delayMs(now);
        if (delay >= 0L) handler.postDelayed(TIMEOUT, Math.max(1L, delay));
    }

    private static void onTimeout() {
        long ticket;
        synchronized (LOCK) { ticket = POLICY.beginOff(SystemClock.elapsedRealtime()); }
        if (ticket >= 0L) DisplayActionCoordinator.requestIdleBottomOff(ticket);
    }

    public static boolean isOffOrPending() {
        synchronized (LOCK) { return POLICY.suppressNativeRestore(); }
    }

    public static boolean isPending(long ticket) {
        synchronized (LOCK) { return POLICY.isPending(ticket); }
    }

    /** Called inside the coordinator's display lock to avoid a restore race. */
    public static void completeOff(long ticket, boolean confirmed) {
        synchronized (LOCK) {
            boolean applied = POLICY.completeOff(ticket, confirmed, SystemClock.elapsedRealtime());
            if (!applied) scheduleLocked(SystemClock.elapsedRealtime());
        }
    }

    public static String snapshotFields() {
        synchronized (LOCK) {
            return ";idle_enabled=" + (POLICY.enabled() ? "1" : "0")
                    + ";idle_minutes=" + POLICY.minutes()
                    + ";idle_input=" + inputState
                    + ";idle_off=" + (POLICY.isOff() ? "1" : "0");
        }
    }

    private static void attachMonitor() {
        Object created = null;
        BottomIdleTouchMonitor createdReceiver = null;
        try {
            Class<?> managerClass = Class.forName("android.hardware.input.InputManager");
            Method instanceMethod = managerClass.getDeclaredMethod("getInstance");
            instanceMethod.setAccessible(true);
            Object manager = instanceMethod.invoke(null);
            Method gesture = managerClass.getDeclaredMethod("monitorGestureInput",
                    String.class, int.class);
            gesture.setAccessible(true);
            created = gesture.invoke(manager, "jesty-bottom-idle",
                    ThorHardwareProfile.BOTTOM_LOGICAL_DISPLAY_ID);
            if (created == null) throw new IllegalStateException("monitor unavailable");
            Method channel = created.getClass().getDeclaredMethod("getInputChannel");
            channel.setAccessible(true);
            InputChannel input = (InputChannel) channel.invoke(created);
            if (input == null) throw new IllegalStateException("channel unavailable");
            createdReceiver = new BottomIdleTouchMonitor(input, android.os.Looper.myLooper());
            synchronized (LOCK) {
                if (handler != null && POLICY.enabled()) {
                    monitor = created;
                    receiver = createdReceiver;
                    inputState = "WAITING_FOR_TOUCH";
                    return;
                }
            }
        } catch (Throwable error) {
            Log.w(TAG, "bottom input monitor unavailable; auto-off remains disarmed", error);
            synchronized (LOCK) { if (POLICY.enabled()) inputState = "UNAVAILABLE"; }
        }
        if (createdReceiver != null) createdReceiver.dispose();
        disposeMonitor(created);
    }

    private static void closeMonitor() {
        BottomIdleTouchMonitor toClose;
        Object toDispose;
        synchronized (LOCK) {
            toClose = receiver;
            toDispose = monitor;
            receiver = null;
            monitor = null;
        }
        if (toClose != null) toClose.dispose();
        disposeMonitor(toDispose);
    }

    private static void disposeMonitor(Object value) {
        if (value == null) return;
        try { value.getClass().getMethod("dispose").invoke(value); }
        catch (Throwable error) { Log.w(TAG, "input monitor cleanup failed", error); }
    }
}
