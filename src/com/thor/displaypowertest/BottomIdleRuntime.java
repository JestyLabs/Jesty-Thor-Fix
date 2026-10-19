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
    private static volatile boolean active;
    private static long monitorEpoch;
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
        final BottomIdleTouchMonitor oldReceiver;
        final Object oldMonitor;
        synchronized (LOCK) {
            restore = POLICY.suppressNativeRestore();
            POLICY.configure(minutes);
            active = minutes != 0;
            if (minutes == 0) {
                prior = handler;
                priorThread = thread;
                handler = null;
                thread = null;
                monitorEpoch++;
                oldReceiver = receiver;
                oldMonitor = monitor;
                receiver = null;
                monitor = null;
                inputState = "OFF";
            } else {
                prior = null;
                priorThread = null;
                oldReceiver = null;
                oldMonitor = null;
                POLICY.mode(DaemonState.getMode());
                if (handler == null) {
                    thread = new HandlerThread("thor-bottom-idle");
                    thread.start();
                    handler = new Handler(thread.getLooper());
                    inputState = "SUSPENDED";
                } else {
                    handler.removeCallbacks(TIMEOUT);
                    inputState = receiver == null ? "SUSPENDED" : "WAITING_FOR_TOUCH";
                }
                reconcileMonitoringLocked(DaemonState.getMode());
            }
        }
        if (restore && minutes != 0 && handler != null) {
            handler.post(DisplayActionCoordinator::restoreIdleBottom);
        }
        if (minutes == 0 && prior != null) {
            prior.removeCallbacks(TIMEOUT);
            prior.post(() -> {
                if (restore) DisplayActionCoordinator.restoreIdleBottom();
                // Cleanup only this generation: never dispose a newer session.
                if (oldReceiver != null) oldReceiver.dispose();
                disposeMonitor(oldMonitor);
                if (priorThread != null) priorThread.quitSafely();
            });
        }
        return "ok=1;idle_minutes=" + minutes;
    }

    public static void onModeSample(String mode) {
        if (!active) return;
        synchronized (LOCK) {
            if (!POLICY.enabled()) return;
            if (POLICY.mode(mode)) {
                if (handler != null) handler.removeCallbacks(TIMEOUT);
                if (!"UNAVAILABLE".equals(inputState)) {
                    inputState = receiver == null ? "SUSPENDED" : "WAITING_FOR_TOUCH";
                }
            }
            reconcileMonitoringLocked(mode);
        }
    }

    /**
     * No input monitor when the lower panel is not in BOTH or the True Off
     * display path is disabled. Attach/detach only on mode/setting edges.
     * Never retry an unavailable hidden API on every watcher sample.
     */
    private static void reconcileMonitoringLocked(String mode) {
        if (handler == null || !POLICY.enabled()) return;
        boolean needed = "0".equals(mode) && DaemonState.isEnabled();
        if (needed) {
            if (receiver == null && !"CONNECTING".equals(inputState)
                    && !"UNAVAILABLE".equals(inputState)) {
                inputState = "CONNECTING";
                long epoch = ++monitorEpoch;
                handler.post(() -> attachMonitor(epoch));
            }
            return;
        }
        if (receiver == null && !"CONNECTING".equals(inputState)) {
            if (!"UNAVAILABLE".equals(inputState)) inputState = "SUSPENDED";
            return;
        }
        // Drop ownership before async cleanup so old events cannot re-arm.
        long unusedEpoch = ++monitorEpoch;
        BottomIdleTouchMonitor closingReceiver = receiver;
        Object closingMonitor = monitor;
        receiver = null;
        monitor = null;
        inputState = "SUSPENDED";
        handler.post(() -> {
            if (closingReceiver != null) closingReceiver.dispose();
            disposeMonitor(closingMonitor);
        });
    }

    /** A display-on notification invalidates pre-sleep evidence and timers. */
    public static void onDisplayWake() {
        if (!active) return;
        synchronized (LOCK) {
            if (!POLICY.enabled()) return;
            POLICY.wake();
            if (handler != null) handler.removeCallbacks(TIMEOUT);
            inputState = receiver == null ? inputState : "WAITING_FOR_TOUCH";
        }
    }

    public static void onBottomTouch(long now, long epoch) {
        final Handler active;
        final boolean restore;
        synchronized (LOCK) {
            if (receiver == null || epoch != monitorEpoch || !POLICY.enabled()
                    || "UNAVAILABLE".equals(inputState) || !DaemonState.isEnabled()) return;
            restore = POLICY.touch(now);
            if (!POLICY.inputVerified()) return;
            inputState = "OBSERVED";
            active = handler;
            scheduleLocked(now);
        }
        if (restore && active != null) active.post(DisplayActionCoordinator::restoreIdleBottom);
    }

    /** Revoke earlier touch proof if hidden display-id decoding stops working. */
    public static void onInputFailure(long epoch) {
        final boolean restore;
        synchronized (LOCK) {
            if (epoch != monitorEpoch || !POLICY.enabled()) return;
            restore = POLICY.suppressNativeRestore();
            POLICY.wake();
            if (handler != null) handler.removeCallbacks(TIMEOUT);
            inputState = "UNAVAILABLE";
            final Handler owner = handler;
            if (owner != null) owner.post(() -> {
                synchronized (LOCK) {
                    if (handler == owner && epoch == monitorEpoch
                            && "UNAVAILABLE".equals(inputState)) {
                        closeMonitor();
                    }
                }
            });
        }
        // Never abandon physical ownership of a darkened panel silently.
        if (restore) DisplayActionCoordinator.restoreIdleBottom();
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

    public static boolean isOff() {
        synchronized (LOCK) { return POLICY.isOff(); }
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

    private static final String INACTIVE_SNAPSHOT =
            ";idle_enabled=0;idle_minutes=0;idle_input=OFF;idle_off=0";

    public static String snapshotFields() {
        if (!active) return INACTIVE_SNAPSHOT;
        synchronized (LOCK) {
            return ";idle_enabled=" + (POLICY.enabled() ? "1" : "0")
                    + ";idle_minutes=" + POLICY.minutes()
                    + ";idle_input=" + inputState
                    + ";idle_off=" + (POLICY.isOff() ? "1" : "0");
        }
    }

    private static void attachMonitor(long epoch) {
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
            createdReceiver = new BottomIdleTouchMonitor(input, android.os.Looper.myLooper(), epoch);
            synchronized (LOCK) {
                if (handler != null && POLICY.enabled() && epoch == monitorEpoch
                        && handler.getLooper() == android.os.Looper.myLooper()) {
                    monitor = created;
                    receiver = createdReceiver;
                    inputState = "WAITING_FOR_TOUCH";
                    return;
                }
            }
        } catch (Throwable error) {
            Log.w(TAG, "bottom input monitor unavailable; auto-off remains disarmed", error);
            synchronized (LOCK) {
                if (POLICY.enabled() && epoch == monitorEpoch) inputState = "UNAVAILABLE";
            }
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
