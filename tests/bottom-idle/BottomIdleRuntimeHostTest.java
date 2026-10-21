package com.thor.displaypowertest;

import android.os.Looper;
import android.os.SystemClock;
import android.view.InputEventReceiver;
import android.view.MotionEvent;

import java.lang.reflect.Field;
import java.util.function.BooleanSupplier;

/**
 * The production BottomIdleRuntime, BottomIdlePolicy, BottomIdleRestoreQueue,
 * BottomIdleTouchMonitor and DisplayActionCoordinator run together. Only the
 * Android Looper/input boundary, the Settings mode read and the vendor panel
 * call are host doubles; the panel follows a successful scripted command.
 */
public final class BottomIdleRuntimeHostTest {
    private static final long FIVE_MINUTES = 5L * 60000L;
    private static int checks;

    private static void check(boolean condition, String id) {
        checks++;
        if (!condition) throw new AssertionError("RT-" + id);
    }

    private static void waitFor(BooleanSupplier condition, String id) throws InterruptedException {
        long end = System.nanoTime() + 5_000_000_000L;
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > end) throw new AssertionError("RT-" + id + " (timed out)");
            Thread.sleep(2L);
        }
        checks++;
    }

    /** The power worker sets IDLE_OFF last, after it left the policy lock. */
    private static void waitIdleOff(String id) throws InterruptedException {
        waitFor(() -> BottomIdleRuntime.isOff() && "0".equals(Telemetry.bottom)
                && "IDLE_OFF".equals(DisplayActionCoordinator.status()), id);
    }

    private static void waitRestored(String id) throws InterruptedException {
        waitFor(() -> "1".equals(Telemetry.bottom) && !BottomIdleRuntime.isOffOrPending()
                && "CONFIRMED".equals(DisplayActionCoordinator.status()), id);
    }

    private static void advance(long ms) {
        SystemClock.setElapsedRealtime(SystemClock.elapsedRealtime() + ms);
    }

    private static Looper looper() { return Looper.latest; }

    private static void touch(int displayId, int action) {
        InputEventReceiver receiver = InputEventReceiver.latest;
        check(receiver != null && !receiver.disposed, "monitor-live");
        receiver.onInputEvent(new MotionEvent(displayId, action));
    }

    private static void tap() {
        touch(ThorHardwareProfile.BOTTOM_LOGICAL_DISPLAY_ID, MotionEvent.ACTION_DOWN);
        advance(80L);
        touch(ThorHardwareProfile.BOTTOM_LOGICAL_DISPLAY_ID, MotionEvent.ACTION_UP);
    }

    private static void sample(String mode, String top, String bottom) {
        ScriptedMode.current = mode;
        Telemetry.top = top;
        Telemetry.bottom = bottom;
        DisplayActionCoordinator.onWatcherSample(mode);
    }

    private static String field(String snapshot, String name) {
        for (String entry : snapshot.split(";")) {
            if (entry.startsWith(name + "=")) return entry.substring(name.length() + 1);
        }
        return null;
    }

    private static void setBackoff(boolean value) throws Exception {
        Field backoff = DisplayActionCoordinator.class.getDeclaredField("lowerOnBackoff");
        backoff.setAccessible(true);
        backoff.set(null, value);
    }

    public static void main(String[] args) throws Exception {
        DisplayHardware.scriptedApply = true;
        DisplayHardware.applySucceeds = true;
        DisplayHardware.panelFollows = true;

        // Disabled: no Looper, monitor or timer even while BOTH is sampled.
        sample("0", "1", "1");
        check(looper() == null && android.os.HandlerThread.started == 0, "disabled-dormant");
        check("0".equals(field(BottomIdleRuntime.snapshotFields(), "idle_enabled")),
                "disabled-snapshot");

        // Enabling in BOTH attaches one passive monitor to the lower display only.
        check(BottomIdleRuntime.configure(5).startsWith("ok=1"), "configure");
        looper().runDue();
        check(android.hardware.input.InputManager.lastDisplayId
                == ThorHardwareProfile.BOTTOM_LOGICAL_DISPLAY_ID, "monitor-display-4");
        check("WAITING_FOR_TOUCH".equals(field(BottomIdleRuntime.snapshotFields(), "idle_input")),
                "waiting-for-touch");

        // Silence and touches on another display never prove inactivity.
        advance(61L * 60000L);
        looper().runDue();
        touch(0, MotionEvent.ACTION_DOWN);
        touch(0, MotionEvent.ACTION_UP);
        check(looper().nextFutureDueAt() < 0L && DisplayHardware.actionCount == 0, "silence-unarmed");

        // A verified lower tap arms exactly one deadline; it fires only when due.
        tap();
        long due = looper().nextFutureDueAt();
        check(due == SystemClock.elapsedRealtime() + FIVE_MINUTES, "tap-arms");
        SystemClock.setElapsedRealtime(due - 1L);
        looper().runDue();
        check(DisplayHardware.actionCount == 0, "not-early");
        SystemClock.setElapsedRealtime(due);
        looper().runDue();
        waitIdleOff("timeout-off");
        check(DisplayHardware.actionCount == 1
                && Boolean.FALSE.equals(DisplayHardware.lastRequestedOn), "single-off-command");

        // The real watcher must hold the owned OFF instead of native BOTH restore.
        sample("0", "1", "0");
        check(DisplayHardware.actionCount == 1, "HOLD");
        check("IDLE_OFF".equals(DisplayActionCoordinator.status()), "HOLD-status");

        // A logical display-on caused by our own OFF must not revoke ownership.
        DisplayActionCoordinator.noteDisplayOn();
        check(BottomIdleRuntime.isOff(), "OWNED");

        // Tap to restore. DOWN and UP both arrive while the restore is still
        // queued behind the coordinator lock, as during a real panel transition.
        long upAt;
        synchronized (DisplayHardware.class) {
            touch(ThorHardwareProfile.BOTTOM_LOGICAL_DISPLAY_ID, MotionEvent.ACTION_DOWN);
            advance(80L);
            touch(ThorHardwareProfile.BOTTOM_LOGICAL_DISPLAY_ID, MotionEvent.ACTION_UP);
            upAt = SystemClock.elapsedRealtime();
        }
        waitRestored("restore-on");
        check(Boolean.TRUE.equals(DisplayHardware.lastRequestedOn)
                && DisplayHardware.actionCount == 2, "single-restore-command");
        // With no further touch the panel must still idle off again.
        waitFor(() -> looper().nextFutureDueAt() == upAt + FIVE_MINUTES, "REARM");
        SystemClock.setElapsedRealtime(upAt + FIVE_MINUTES);
        looper().runDue();
        waitIdleOff("rearmed-off");
        check(DisplayHardware.actionCount == 3, "rearmed-single-command");

        // Restore again; then an unrelated display-on resets touch evidence.
        tap();
        waitRestored("second-restore");
        waitFor(() -> looper().nextFutureDueAt() > 0L, "second-rearm");
        DisplayActionCoordinator.noteDisplayOn();
        check(looper().nextFutureDueAt() < 0L, "display-on-cancels-timer");
        check("WAITING_FOR_TOUCH".equals(field(BottomIdleRuntime.snapshotFields(), "idle_input")),
                "display-on-needs-touch");

        // BOTH -> TOP detaches the monitor and cancels the deadline.
        tap();
        check(looper().nextFutureDueAt() > 0L, "armed-before-top");
        int disposedBefore = android.view.InputMonitor.disposed;
        sample("1", "1", "0");
        looper().runDue();
        check(looper().nextFutureDueAt() < 0L, "top-cancels-timer");
        check(InputEventReceiver.latest.disposed
                && android.view.InputMonitor.disposed == disposedBefore + 1, "top-detaches");
        check("SUSPENDED".equals(field(BottomIdleRuntime.snapshotFields(), "idle_input")),
                "top-suspended");

        // TOP -> BOTH attaches a fresh monitor that again needs a real touch.
        int monitors = android.hardware.input.InputManager.monitors;
        sample("0", "1", "1");
        looper().runDue();
        check(android.hardware.input.InputManager.monitors == monitors + 1
                && !InputEventReceiver.latest.disposed, "both-reattaches");
        check(looper().nextFutureDueAt() < 0L, "both-needs-touch");

        // v1.7.5 stall containment refuses the timed OFF and defers it.
        setBackoff(true);
        int actions = DisplayHardware.actionCount;
        tap();
        due = looper().nextFutureDueAt();
        SystemClock.setElapsedRealtime(due);
        looper().runDue();
        long retryAt = due + FIVE_MINUTES;
        waitFor(() -> looper().nextFutureDueAt() == retryAt, "backoff-deferred");
        check(DisplayHardware.actionCount == actions && !BottomIdleRuntime.isOffOrPending(),
                "backoff-no-command");
        setBackoff(false);

        // Disabling while dark restores the lower panel and tears everything down.
        SystemClock.setElapsedRealtime(retryAt);
        looper().runDue();
        waitIdleOff("disable-setup-off");
        Looper owner = looper();
        check(BottomIdleRuntime.configure(0).startsWith("ok=1"), "disable");
        waitRestored("disable-restores");
        owner.runDue();
        check(owner.quit && InputEventReceiver.latest.disposed, "disable-teardown");
        check("0".equals(field(BottomIdleRuntime.snapshotFields(), "idle_enabled"))
                && !BottomIdleRuntime.isOffOrPending(), "disable-snapshot");

        System.out.println("BottomIdleRuntimeHostTest passed: " + checks
                + " checks; real runtime/policy/queue/touch monitor + real coordinator;"
                + " Android input routing/vendor panel/Thor NOT RUN");
    }
}

/** Replaces the coordinator's Settings process read in the compiled test copy. */
final class ScriptedMode {
    static volatile String current = "?";
}
