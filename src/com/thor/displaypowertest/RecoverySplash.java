package com.thor.displaypowertest;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.SystemClock;
import android.view.Surface;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Prototype-only compositor-owned recovery splash.
 *
 * app_process entry point: no Activity and no app-window dependency.
 * SurfaceControl is hidden API on the SDK used to build this project, so the
 * runtime uses the same reflection strategy already proven by DisplayHardware.
 * Every failure is UX-only and fails open.
 */
public final class RecoverySplash {
    private static final String SURFACE_CONTROL = "android.view.SurfaceControl";
    private static final String SURFACE_CONTROL_BUILDER =
            "android.view.SurfaceControl$Builder";
    private static final String SURFACE_CONTROL_TRANSACTION =
            "android.view.SurfaceControl$Transaction";
    private static final String TRANSACTION_COMMITTED_LISTENER =
            "android.view.SurfaceControl$TransactionCommittedListener";

    private static final int SPLASH_LAYER = 0x40000000;
    private static final long TARGET_WAIT_MS = 1500L;
    private static final long SHOW_COMMIT_MS = 750L;
    private static final long MAX_VISIBLE_MS = 6000L;
    private static final long REMOVE_COMMIT_MS = 500L;
    private static final long PROCESS_TTL_MS = 8000L;
    private static final long POLL_MS = 250L;

    private static final Executor DIRECT_EXECUTOR = new Executor() {
        @Override public void execute(Runnable command) {
            command.run();
        }
    };

    private static volatile boolean finished;
    private static volatile Object activeControl;

    private RecoverySplash() {}

    public static void main(String[] args) {
        long processStartedAt = SystemClock.elapsedRealtime();
        startProcessTtlWatchdog(processStartedAt);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            trace("SPLASH_FAIL_OPEN", "reason=UNSUPPORTED_API");
            return;
        }
        if (args.length != 4 || !positivePid(args[0]) || !positivePid(args[1])
                || !positivePid(args[2]) || !positivePid(args[3])) {
            trace("SPLASH_FAIL_OPEN", "reason=INVALID_SUCCESSOR_ARGS");
            return;
        }
        run(args[0], args[1], args[2], args[3], processStartedAt);
    }

    private static void run(String oldComposer, String expectedComposer,
            String oldSf, String expectedSf, long processStartedAt) {
        Object control = null;
        Surface surface = null;
        boolean shown = false;
        boolean removalRequested = false;
        boolean removed = false;
        try {
            if (!successorStillExact(oldComposer, expectedComposer,
                    "vendor.qti.hardware.display.composer-service")
                    || !successorStillExact(oldSf, expectedSf, "surfaceflinger")) {
                trace("SPLASH_FAIL_OPEN", "reason=SUCCESSOR_CHANGED");
                return;
            }

            // Capture the post-successor 0 state as early as possible so a fast
            // 0->1 bootanim exit edge is not lost while the buffer is created.
            boolean bootanimZeroSeen = "0".equals(property("service.bootanim.exit"));

            DisplayTarget target = waitForTarget(processStartedAt + PROCESS_TTL_MS);
            if (target == null) {
                trace("SPLASH_FAIL_OPEN", "reason=TARGET_UNAVAILABLE");
                return;
            }
            if (!successorStillExact(oldComposer, expectedComposer,
                    "vendor.qti.hardware.display.composer-service")
                    || !successorStillExact(oldSf, expectedSf, "surfaceflinger")) {
                trace("SPLASH_FAIL_OPEN", "reason=SUCCESSOR_CHANGED");
                return;
            }

            control = buildSurfaceControl(target.width, target.height);
            activeControl = control;
            surface = surfaceFromControl(control);
            draw(surface, target.width, target.height);

            CountDownLatch shownCommit = new CountDownLatch(1);
            trace("SPLASH_SHOW_REQUESTED", "composer_pid=" + expectedComposer
                    + ";sf_pid=" + expectedSf + ";backend=SURFACECONTROL");
            Object showTransaction = newTransaction();
            try {
                transactionSetLayer(showTransaction, control, SPLASH_LAYER);
                transactionShow(showTransaction, control);
                transactionAddCommittedListener(showTransaction, shownCommit);
                transactionApply(showTransaction);
            } finally {
                closeTransactionQuietly(showTransaction);
            }
            if (!shownCommit.await(SHOW_COMMIT_MS, TimeUnit.MILLISECONDS)) {
                trace("SPLASH_TIMEOUT", "phase=SHOW");
                trace("SPLASH_FAIL_OPEN", "reason=SHOW_TIMEOUT;cleanup_requested=1");
                return;
            }

            shown = true;
            long shownAt = SystemClock.elapsedRealtime();
            trace("SPLASH_SHOWN", "evidence=TRANSACTION_COMMITTED"
                    + ";composer_pid=" + expectedComposer
                    + ";sf_pid=" + expectedSf
                    + ";display_id=" + Long.toUnsignedString(target.physicalId)
                    + ";width=" + target.width + ";height=" + target.height);

            long visibleDeadline = Math.min(shownAt + MAX_VISIBLE_MS,
                    processStartedAt + PROCESS_TTL_MS);
            boolean bootanimExit = waitForBootAnimationExitEdge(
                    bootanimZeroSeen, visibleDeadline);
            String reason = bootanimExit ? "BOOTANIM_EXIT" : "VISIBLE_TIMEOUT";
            if (bootanimExit) {
                trace("SPLASH_BOOTANIM_EXIT", "from=0;to=1");
            } else {
                trace("SPLASH_TIMEOUT", "phase=VISIBLE;age_ms="
                        + (SystemClock.elapsedRealtime() - shownAt));
            }

            removalRequested = true;
            trace("SPLASH_REMOVE_REQUESTED", "reason=" + reason);

            CountDownLatch removedCommit = new CountDownLatch(1);
            Object removeTransaction = newTransaction();
            try {
                transactionReparentToNull(removeTransaction, control);
                transactionAddCommittedListener(removeTransaction, removedCommit);
                transactionApply(removeTransaction);
            } finally {
                closeTransactionQuietly(removeTransaction);
            }
            if (!removedCommit.await(REMOVE_COMMIT_MS, TimeUnit.MILLISECONDS)) {
                trace("SPLASH_TIMEOUT", "phase=REMOVE");
                trace("SPLASH_FAIL_OPEN", "reason=REMOVE_TIMEOUT;cleanup_requested=1");
                return;
            }
            removed = true;
            trace("SPLASH_REMOVED", "evidence=TRANSACTION_COMMITTED;reason=" + reason
                    + ";visible_ms=" + (SystemClock.elapsedRealtime() - shownAt));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            trace("SPLASH_FAIL_OPEN", "reason=INTERRUPTED;cleanup_requested="
                    + (shown ? "1" : "0"));
        } catch (Throwable error) {
            trace("SPLASH_FAIL_OPEN", "reason=RUNTIME_ERROR;cleanup_requested="
                    + (shown ? "1" : "0")
                    + ";type=" + safeToken(error.getClass().getSimpleName()));
        } finally {
            if (control != null && !removed) {
                if (!removalRequested && shown) {
                    trace("SPLASH_REMOVE_REQUESTED", "reason=FAILURE");
                }
                detachBestEffort(control);
            }
            if (surface != null) {
                try { surface.release(); } catch (Throwable ignored) {}
            }
            releaseControlQuietly(control);
        }
    }

    private static void startProcessTtlWatchdog(final long processStartedAt) {
        Thread watchdog = new Thread(new Runnable() {
            @Override public void run() {
                long deadline = processStartedAt + PROCESS_TTL_MS;
                while (!finished) {
                    long remaining = deadline - SystemClock.elapsedRealtime();
                    if (remaining <= 0L) break;
                    try {
                        Thread.sleep(Math.min(remaining, 250L));
                    } catch (InterruptedException ignored) {
                        return;
                    }
                }
                if (finished) return;

                trace("SPLASH_TIMEOUT", "phase=PROCESS_TTL;age_ms="
                        + (SystemClock.elapsedRealtime() - processStartedAt));
                Object control = activeControl;
                if (control != null) detachBestEffort(control);
                trace("SPLASH_FAIL_OPEN", "reason=PROCESS_TTL;cleanup_requested="
                        + (control == null ? "0" : "1"));
                System.exit(0);
            }
        }, "thor-recovery-splash-ttl");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    private static boolean successorStillExact(String baseline, String expected, String process) {
        SuccessorPidModel.Result result = SuccessorPidModel.resolve(
                baseline, command("pidof", process));
        return result.status == SuccessorPidModel.Status.FOUND
                && expected.equals(result.successorPid);
    }

    private static DisplayTarget waitForTarget(long processDeadline)
            throws InterruptedException {
        long deadline = Math.min(SystemClock.elapsedRealtime() + TARGET_WAIT_MS,
                processDeadline);
        do {
            try {
                DisplayTarget target = resolvePrimaryTopTarget();
                if (target != null) return target;
            } catch (Throwable ignored) {
                // PID existence can precede SurfaceFlinger binder readiness.
            }
            Thread.sleep(100L);
        } while (SystemClock.elapsedRealtime() < deadline);
        trace("SPLASH_TIMEOUT", "phase=TARGET");
        return null;
    }

    /**
     * No layer-stack mutation in the first prototype. The measured lower-panel
     * physical ID is excluded and ambiguous topology fails open.
     */
    private static DisplayTarget resolvePrimaryTopTarget() throws Exception {
        Class<?> surfaceControlClass = Class.forName(SURFACE_CONTROL);
        Method idsMethod = surfaceControlClass.getDeclaredMethod("getPhysicalDisplayIds");
        idsMethod.setAccessible(true);
        long[] ids = (long[]) idsMethod.invoke(null);
        if (ids == null || ids.length == 0) return null;

        long topId = 0L;
        int topCandidates = 0;
        for (long id : ids) {
            if (id != ThorHardwareProfile.BOTTOM_PHYSICAL_DISPLAY_ID) {
                topId = id;
                topCandidates++;
            }
        }
        if (topCandidates != 1 || ids[0] != topId) return null;

        Method infoMethod = surfaceControlClass.getDeclaredMethod(
                "getDynamicDisplayInfo", long.class);
        infoMethod.setAccessible(true);
        Object info = infoMethod.invoke(null, topId);
        if (info == null) return null;

        Field activeIdField = info.getClass().getField("activeDisplayModeId");
        Field modesField = info.getClass().getField("supportedDisplayModes");
        int activeId = activeIdField.getInt(info);
        Object modes = modesField.get(info);
        if (modes == null) return null;

        int count = Array.getLength(modes);
        for (int i = 0; i < count; i++) {
            Object mode = Array.get(modes, i);
            if (mode == null) continue;
            Class<?> modeClass = mode.getClass();
            if (modeClass.getField("id").getInt(mode) != activeId) continue;
            int width = modeClass.getField("width").getInt(mode);
            int height = modeClass.getField("height").getInt(mode);
            if (width < 320 || height < 240 || width > 4096 || height > 4096) {
                return null;
            }
            return new DisplayTarget(topId, width, height);
        }
        return null;
    }

    private static Object buildSurfaceControl(int width, int height) throws Exception {
        Class<?> builderClass = Class.forName(SURFACE_CONTROL_BUILDER);
        Object builder = builderClass.getDeclaredConstructor().newInstance();
        builder = invokeBuilder(builder, "setName", new Class<?>[] { String.class },
                "Thor recovery splash prototype");
        builder = invokeBuilder(builder, "setBufferSize",
                new Class<?>[] { int.class, int.class }, width, height);
        builder = invokeBuilder(builder, "setFormat", new Class<?>[] { int.class },
                PixelFormat.RGBA_8888);
        builder = invokeBuilder(builder, "setOpaque", new Class<?>[] { boolean.class }, true);
        builder = invokeBuilder(builder, "setHidden", new Class<?>[] { boolean.class }, true);
        Method build = builderClass.getMethod("build");
        return build.invoke(builder);
    }

    private static Object invokeBuilder(Object builder, String method, Class<?>[] types,
            Object... args) throws Exception {
        Method call = builder.getClass().getMethod(method, types);
        return call.invoke(builder, args);
    }

    private static Surface surfaceFromControl(Object control) throws Exception {
        Class<?> surfaceControlClass = Class.forName(SURFACE_CONTROL);
        Constructor<Surface> constructor = Surface.class.getDeclaredConstructor(surfaceControlClass);
        constructor.setAccessible(true);
        return constructor.newInstance(control);
    }

    private static Object newTransaction() throws Exception {
        Class<?> transactionClass = Class.forName(SURFACE_CONTROL_TRANSACTION);
        Constructor<?> constructor = transactionClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static void transactionSetLayer(Object transaction, Object control, int layer)
            throws Exception {
        Class<?> surfaceControlClass = Class.forName(SURFACE_CONTROL);
        Method method = transaction.getClass().getMethod(
                "setLayer", surfaceControlClass, int.class);
        method.invoke(transaction, control, layer);
    }

    private static void transactionShow(Object transaction, Object control) throws Exception {
        Class<?> surfaceControlClass = Class.forName(SURFACE_CONTROL);
        Method method = transaction.getClass().getMethod("show", surfaceControlClass);
        method.invoke(transaction, control);
    }

    private static void transactionReparentToNull(Object transaction, Object control)
            throws Exception {
        Class<?> surfaceControlClass = Class.forName(SURFACE_CONTROL);
        Method method = transaction.getClass().getMethod(
                "reparent", surfaceControlClass, surfaceControlClass);
        method.invoke(transaction, control, null);
    }

    private static void transactionAddCommittedListener(
            Object transaction, final CountDownLatch latch) throws Exception {
        Class<?> listenerClass = Class.forName(TRANSACTION_COMMITTED_LISTENER);
        InvocationHandler handler = new InvocationHandler() {
            @Override public Object invoke(Object proxy, Method method, Object[] args) {
                String name = method.getName();
                if ("onTransactionCommitted".equals(name)) {
                    latch.countDown();
                    return null;
                }
                if ("hashCode".equals(name)) return System.identityHashCode(proxy);
                if ("equals".equals(name)) return proxy == (args == null ? null : args[0]);
                if ("toString".equals(name)) return "RecoverySplashTransactionListener";
                return null;
            }
        };
        Object listener = Proxy.newProxyInstance(
                listenerClass.getClassLoader(), new Class<?>[] { listenerClass }, handler);
        Method method = transaction.getClass().getMethod(
                "addTransactionCommittedListener", Executor.class, listenerClass);
        method.invoke(transaction, DIRECT_EXECUTOR, listener);
    }

    private static void transactionApply(Object transaction) throws Exception {
        transaction.getClass().getMethod("apply").invoke(transaction);
    }

    private static void closeTransactionQuietly(Object transaction) {
        if (transaction == null) return;
        try {
            transaction.getClass().getMethod("close").invoke(transaction);
        } catch (Throwable ignored) {}
    }

    private static void releaseControlQuietly(Object control) {
        if (control == null) return;
        try {
            control.getClass().getMethod("release").invoke(control);
        } catch (Throwable ignored) {}
    }

    private static void draw(Surface surface, int width, int height) throws Exception {
        Canvas canvas = null;
        try {
            canvas = surface.lockCanvas(null);
            canvas.drawColor(Color.rgb(14, 11, 24));

            Paint brand = new Paint(Paint.ANTI_ALIAS_FLAG);
            brand.setColor(Color.WHITE);
            brand.setTextAlign(Paint.Align.CENTER);
            brand.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            brand.setTextSize(Math.max(28f, Math.min(width, height) * 0.045f));

            Paint status = new Paint(Paint.ANTI_ALIAS_FLAG);
            status.setColor(Color.WHITE);
            status.setTextAlign(Paint.Align.CENTER);
            status.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            status.setTextSize(Math.max(32f, Math.min(width, height) * 0.052f));

            Paint detail = new Paint(Paint.ANTI_ALIAS_FLAG);
            detail.setColor(Color.rgb(196, 181, 253));
            detail.setTextAlign(Paint.Align.CENTER);
            detail.setTextSize(Math.max(18f, Math.min(width, height) * 0.026f));

            float centerX = width / 2f;
            float centerY = height / 2f;
            canvas.drawText("Jesty Thor Fix", centerX,
                    centerY - status.getTextSize() * 1.6f, brand);
            canvas.drawText("Finishing startup\u2026", centerX,
                    centerY, status);
            canvas.drawText("This is expected during startup.", centerX,
                    centerY + status.getTextSize() * 1.25f, detail);
        } finally {
            if (canvas != null) surface.unlockCanvasAndPost(canvas);
        }
    }

    private static boolean waitForBootAnimationExitEdge(boolean sawZero, long deadline)
            throws InterruptedException {
        boolean zeroSeen = sawZero;
        while (SystemClock.elapsedRealtime() < deadline) {
            String value = property("service.bootanim.exit");
            if ("0".equals(value)) {
                zeroSeen = true;
            } else if (zeroSeen && "1".equals(value)) {
                return true;
            }
            Thread.sleep(POLL_MS);
        }
        return false;
    }

    private static String property(String name) {
        return command("getprop", name);
    }

    private static String command(String... command) {
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (!ProcessWait.exited(process, 350L) || process.exitValue() != 0) return "";
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String value = reader.readLine();
                return value == null ? "" : value.trim();
            }
        } catch (Throwable ignored) {
            return "";
        } finally {
            if (process != null) process.destroy();
        }
    }

    private static boolean positivePid(String value) {
        return value != null && value.matches("[1-9][0-9]*");
    }

    private static void detachBestEffort(Object control) {
        Object transaction = null;
        try {
            transaction = newTransaction();
            transactionReparentToNull(transaction, control);
            transactionApply(transaction);
        } catch (Throwable ignored) {
        } finally {
            closeTransactionQuietly(transaction);
        }
    }

    private static String safeToken(String value) {
        if (value == null || value.isEmpty()) return "UNKNOWN";
        return value.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private static void trace(String action, String detail) {
        try {
            String bootId = command("cat", "/proc/sys/kernel/random/boot_id");
            if (bootId.isEmpty()) bootId = "?";
            String line = "elapsed_ms=" + SystemClock.elapsedRealtime()
                    + ";action=" + action
                    + (detail == null || detail.isEmpty() ? "" : ";" + detail)
                    + ";pid=" + android.os.Process.myPid()
                    + ";boot_id=" + bootId
                    + ";source=splash";
            RootLogFiles.append(BootTrace.PATH, line + "\n");
        } catch (Throwable ignored) {
            // Diagnostics never affect the fail-open visual path.
        }
    }

    private static final class DisplayTarget {
        final long physicalId;
        final int width;
        final int height;

        DisplayTarget(long physicalId, int width, int height) {
            this.physicalId = physicalId;
            this.width = width;
            this.height = height;
        }
    }
}
