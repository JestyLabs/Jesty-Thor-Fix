package com.thor.displaypowertest;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputChannel;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private static long pendingAttachEpoch = -1L;
    private static HandlerThread thread;
    private static Handler handler;
    private static BottomIdleTouchMonitor receiver;
    private static Object monitor;
    private static String inputState = "OFF";
    private static final Runnable TIMEOUT = BottomIdleRuntime::onTimeout;
    /** Limit vendor calls even if a 70s stall spans another touch/timeout lease. */
    private static final AtomicBoolean POWER_IN_FLIGHT = new AtomicBoolean();

    private BottomIdleRuntime() {}

    public static String configure(int minutes) {
        if (minutes != 0 && !BottomIdleSettings.validMinutes(minutes))
            return "ok=0;error=INVALID_IDLE_TIMEOUT";
        final Handler prior;
        final Handler currentHandler;
        final HandlerThread priorThread;
        final boolean restore;
        final long restoreLease;
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
                pendingAttachEpoch = -1L;
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
            // Mint the restore lease only after configure/mode have invalidated
            // previous generations, including a disable/re-enable edge.
            restoreLease = restore ? POLICY.requireRestore() : -1L;
            currentHandler = handler;
        }
        if (restore && minutes != 0 && currentHandler != null) {
            currentHandler.post(() -> DisplayActionCoordinator.restoreIdleBottom(restoreLease));
        }
        if (minutes == 0 && prior != null) {
            prior.removeCallbacks(TIMEOUT);
            prior.post(() -> {
                if (restore) DisplayActionCoordinator.restoreIdleBottom(restoreLease);
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
        boolean needed = BottomIdlePolicy.shouldObserve(mode, DaemonState.isEnabled());
        if (needed) {
            if (receiver == null && pendingAttachEpoch == -1L
                    && !"UNAVAILABLE".equals(inputState)) {
                inputState = "CONNECTING";
                long epoch = ++monitorEpoch;
                pendingAttachEpoch = epoch;
                handler.post(() -> attachMonitor(epoch));
            }
            return;
        }
        // A cancelled attach must not succeed if its Binder call returns late.
        if (pendingAttachEpoch >= 0L) {
            monitorEpoch++;
            pendingAttachEpoch = -1L;
        }
        if (receiver == null) {
            if (!"UNAVAILABLE".equals(inputState)) inputState = "SUSPENDED";
            return;
        }
        // Drop ownership before async cleanup so old events cannot re-arm.
        monitorEpoch++;
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

    public static void onBottomTouch(long now, long epoch, int action) {
        final Handler owner;
        final long restoreLease;
        synchronized (LOCK) {
            if (receiver == null || epoch != monitorEpoch
                    || "UNAVAILABLE".equals(inputState) || !DaemonState.isEnabled()) return;
            final int pointerState =
                    action == android.view.MotionEvent.ACTION_DOWN
                    || action == android.view.MotionEvent.ACTION_POINTER_DOWN
                    || action == android.view.MotionEvent.ACTION_MOVE ? 1
                    : action == android.view.MotionEvent.ACTION_UP
                    || action == android.view.MotionEvent.ACTION_CANCEL ? 0 : -1;
            // One atomic policy operation covers touch evidence, pointer state
            // and the generation of any queued restoration.
            restoreLease = POLICY.touchEvent(now, pointerState);
            if (restoreLease == BottomIdlePolicy.REJECTED_TOUCH) return;
            inputState = "OBSERVED";
            owner = handler;
            scheduleLocked(now);
        }
        if (restoreLease >= 0L && owner != null)
            owner.post(() -> DisplayActionCoordinator.restoreIdleBottom(restoreLease));
    }

    /** Revoke earlier touch proof if hidden display-id decoding stops working. */
    public static void onInputFailure(long epoch) {
        final boolean restore;
        final long restoreLease;
        final Handler owner;
        synchronized (LOCK) {
            if (epoch != monitorEpoch || !POLICY.enabled()) return;
            restore = POLICY.suppressNativeRestore();
            POLICY.wake();
            restoreLease = restore ? POLICY.requireRestore() : -1L;
            if (handler != null) handler.removeCallbacks(TIMEOUT);
            inputState = "UNAVAILABLE";
            owner = handler;
            if (owner != null) owner.post(() -> closeMonitorIfFailed(owner, epoch));
        }
        // Finish the current input event before potentially expensive
        // display reconciliation; do not hold the input-dispatch callback.
        if (restore && owner != null)
            owner.post(() -> DisplayActionCoordinator.restoreIdleBottom(restoreLease));
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
        if (ticket < 0L) return;
        // Touch cancels a policy lease while the old composer call may still be
        // blocked. A fresh timeout must never create a second vendor worker.
        if (!POWER_IN_FLIGHT.compareAndSet(false, true)) {
            completeOff(ticket, false); // fail closed and retry on a later deadline
            return;
        }
        try {
            new Thread(() -> {
                try {
                    DisplayActionCoordinator.requestIdleBottomOff(ticket);
                } finally {
                    POWER_IN_FLIGHT.set(false);
                }
            }, "thor-idle-power").start();
        } catch (Throwable failure) {
            POWER_IN_FLIGHT.set(false);
            completeOff(ticket, false);
            Log.w(TAG, "bottom idle power worker unavailable; timeout deferred", failure);
        }
    }

    public static boolean isOffOrPending() {
        synchronized (LOCK) { return POLICY.suppressNativeRestore(); }
    }

    public static boolean isOff() {
        synchronized (LOCK) { return POLICY.isOff(); }
    }

    public static boolean canRestore(long lease) {
        synchronized (LOCK) { return POLICY.canRestore(lease); }
    }

    public static void completeRestore(long lease, boolean confirmed) {
        synchronized (LOCK) { POLICY.completeRestore(lease, confirmed); }
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
                    pendingAttachEpoch = -1L;
                    inputState = "WAITING_FOR_TOUCH";
                    return;
                }
            }
        } catch (Throwable error) {
            Log.w(TAG, "bottom input monitor unavailable; auto-off remains disarmed", error);
            synchronized (LOCK) {
                if (epoch == pendingAttachEpoch) pendingAttachEpoch = -1L;
                if (POLICY.enabled() && epoch == monitorEpoch) inputState = "UNAVAILABLE";
            }
        }
        synchronized (LOCK) {
            if (pendingAttachEpoch == epoch) pendingAttachEpoch = -1L;
        }
        if (createdReceiver != null) createdReceiver.dispose();
        disposeMonitor(created);
    }

    /** Only the still-owned monitor may be disposed; never hold LOCK in vendor cleanup. */
    private static void closeMonitorIfFailed(Handler owner, long epoch) {
        final BottomIdleTouchMonitor toClose;
        final Object toDispose;
        synchronized (LOCK) {
            if (handler != owner || epoch != monitorEpoch
                    || !"UNAVAILABLE".equals(inputState)) return;
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
