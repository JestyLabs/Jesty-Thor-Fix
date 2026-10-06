package com.thor.displaypowertest;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.view.Surface;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * TEST-BRANCH ONLY worker for a no-reboot recovery-surface targeting probe.
 *
 * This deliberately mutates only the temporary probe layer. It never changes a
 * physical display's layer stack, projection, power mode or CPU/restart state.
 *
 * Visual phases:
 *   DEFAULT  - dark red background, one white bar
 *   STACK_0  - dark purple background, two white bars
 *   STACK_4  - dark teal background, three white bars
 *
 * Each phase is temporary and the layer is detached on every exit path.
 */
public final class RecoverySurfaceTargetProbeWorker {
    private static final String SURFACE_CONTROL = "android.view.SurfaceControl";
    private static final String SURFACE_CONTROL_BUILDER =
            "android.view.SurfaceControl$Builder";
    private static final String SURFACE_CONTROL_TRANSACTION =
            "android.view.SurfaceControl$Transaction";
    private static final String TRANSACTION_COMMITTED_LISTENER =
            "android.view.SurfaceControl$TransactionCommittedListener";

    private static final int PROBE_WIDTH = 1920;
    private static final int PROBE_HEIGHT = 1080;
    private static final int PROBE_LAYER = 0x3fffff00;
    private static final long COMMIT_WAIT_MS = 750L;
    private static final long PHASE_VISIBLE_MS = 1400L;
    private static final long PROCESS_TTL_MS = 9000L;

    private static final Executor DIRECT_EXECUTOR = new Executor() {
        @Override public void execute(Runnable command) {
            command.run();
        }
    };

    private static volatile boolean finished;
    private static volatile Object activeControl;

    private RecoverySurfaceTargetProbeWorker() {}

    public static void main(String[] args) {
        long startedAt = SystemClock.elapsedRealtime();
        startTtlWatchdog(startedAt);
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                trace("TARGET_PROBE_FAIL_OPEN", "reason=UNSUPPORTED_API");
                return;
            }
            if (args != null && args.length != 0) {
                trace("TARGET_PROBE_FAIL_OPEN", "reason=ARGS_NOT_ALLOWED");
                return;
            }
            run();
        } finally {
            finished = true;
        }
    }

    private static void run() {
        Object control = null;
        Surface surface = null;
        boolean removed = false;
        String composerBefore = command("pidof", "vendor.qti.hardware.display.composer-service");
        String sfBefore = command("pidof", "surfaceflinger");
        try {
            Class<?> surfaceControlClass = Class.forName(SURFACE_CONTROL);

            Topology topology = readTopology(surfaceControlClass);
            if (!topology.knownThor) {
                trace("TARGET_PROBE_FAIL_OPEN", "reason=UNKNOWN_TOPOLOGY;count=" + topology.count);
                return;
            }

            probeReadOnlyEntryPoints(surfaceControlClass);

            control = buildSurfaceControl();
            activeControl = control;
            surface = surfaceFromControl(control);

            if (!showPhase(control, surface, "DEFAULT", null, 1,
                    Color.rgb(72, 18, 24))) {
                return;
            }
            Thread.sleep(PHASE_VISIBLE_MS);

            if (!showPhase(control, surface, "STACK_0", Integer.valueOf(0), 2,
                    Color.rgb(14, 11, 24))) {
                return;
            }
            Thread.sleep(PHASE_VISIBLE_MS);

            if (!showPhase(control, surface, "STACK_4",
                    Integer.valueOf(ThorHardwareProfile.BOTTOM_LOGICAL_DISPLAY_ID), 3,
                    Color.rgb(5, 48, 52))) {
                return;
            }
            Thread.sleep(PHASE_VISIBLE_MS);

            removed = detachCommitted(control, "COMPLETE");
            if (!removed) {
                trace("TARGET_PROBE_FAIL_OPEN", "reason=REMOVE_TIMEOUT");
                return;
            }

            String composerAfter = command("pidof",
                    "vendor.qti.hardware.display.composer-service");
            String sfAfter = command("pidof", "surfaceflinger");
            boolean unchanged = composerBefore.equals(composerAfter)
                    && sfBefore.equals(sfAfter)
                    && !composerBefore.isEmpty()
                    && !sfBefore.isEmpty();
            trace("TARGET_PROBE_PIDS",
                    "unchanged=" + (unchanged ? "1" : "0")
                            + ";composer_before=" + safeToken(composerBefore)
                            + ";composer_after=" + safeToken(composerAfter)
                            + ";sf_before=" + safeToken(sfBefore)
                            + ";sf_after=" + safeToken(sfAfter));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            trace("TARGET_PROBE_FAIL_OPEN", "reason=INTERRUPTED");
        } catch (Throwable error) {
            trace("TARGET_PROBE_FAIL_OPEN",
                    "reason=RUNTIME_ERROR;type=" + safeToken(error.getClass().getSimpleName()));
        } finally {
            if (!removed && control != null) {
                detachBestEffort(control);
            }
            if (surface != null) {
                try { surface.release(); } catch (Throwable ignored) {}
            }
            releaseControlQuietly(control);
            activeControl = null;
        }
    }

    private static Topology readTopology(Class<?> surfaceControlClass) throws Exception {
        Method idsMethod = surfaceControlClass.getDeclaredMethod("getPhysicalDisplayIds");
        idsMethod.setAccessible(true);
        long started = SystemClock.elapsedRealtime();
        long[] ids = (long[]) idsMethod.invoke(null);
        long duration = SystemClock.elapsedRealtime() - started;

        int count = ids == null ? 0 : ids.length;
        boolean top = false;
        boolean bottom = false;
        boolean unknown = false;
        if (ids != null) {
            for (long id : ids) {
                if (id == ThorHardwareProfile.TOP_PHYSICAL_DISPLAY_ID) {
                    if (top) unknown = true;
                    top = true;
                } else if (id == ThorHardwareProfile.BOTTOM_PHYSICAL_DISPLAY_ID) {
                    if (bottom) unknown = true;
                    bottom = true;
                } else {
                    unknown = true;
                }
            }
        }
        boolean known = count == 2 && top && bottom && !unknown;
        trace("TARGET_PROBE_PREFLIGHT",
                "step=PHYSICAL_IDS;duration_ms=" + duration
                        + ";count=" + count
                        + ";known=" + (known ? "1" : "0"));
        return new Topology(count, known);
    }

    private static void probeReadOnlyEntryPoints(Class<?> surfaceControlClass) {
        probePrimaryPhysicalId(surfaceControlClass);
        probePhysicalToken(surfaceControlClass,
                "TOP_TOKEN", ThorHardwareProfile.TOP_PHYSICAL_DISPLAY_ID);
        probePhysicalToken(surfaceControlClass,
                "BOTTOM_TOKEN", ThorHardwareProfile.BOTTOM_PHYSICAL_DISPLAY_ID);
        probeInternalDisplayToken(surfaceControlClass);
    }

    private static void probePrimaryPhysicalId(Class<?> surfaceControlClass) {
        long started = SystemClock.elapsedRealtime();
        try {
            Method method = surfaceControlClass.getDeclaredMethod("getPrimaryPhysicalDisplayId");
            method.setAccessible(true);
            Object value = method.invoke(null);
            long duration = SystemClock.elapsedRealtime() - started;
            long id = value instanceof Number ? ((Number) value).longValue() : 0L;
            trace("TARGET_PROBE_PREFLIGHT",
                    "step=PRIMARY_PHYSICAL_ID;duration_ms=" + duration
                            + ";available=1;matches_top="
                            + (id == ThorHardwareProfile.TOP_PHYSICAL_DISPLAY_ID ? "1" : "0")
                            + ";id=" + Long.toUnsignedString(id));
        } catch (NoSuchMethodException missing) {
            trace("TARGET_PROBE_PREFLIGHT",
                    "step=PRIMARY_PHYSICAL_ID;duration_ms="
                            + (SystemClock.elapsedRealtime() - started)
                            + ";available=0");
        } catch (Throwable error) {
            trace("TARGET_PROBE_PREFLIGHT",
                    "step=PRIMARY_PHYSICAL_ID;duration_ms="
                            + (SystemClock.elapsedRealtime() - started)
                            + ";available=1;error="
                            + safeToken(error.getClass().getSimpleName()));
        }
    }

    private static void probePhysicalToken(Class<?> surfaceControlClass,
            String step, long physicalId) {
        long started = SystemClock.elapsedRealtime();
        try {
            Method method = surfaceControlClass.getDeclaredMethod(
                    "getPhysicalDisplayToken", long.class);
            method.setAccessible(true);
            Object value = method.invoke(null, physicalId);
            long duration = SystemClock.elapsedRealtime() - started;
            trace("TARGET_PROBE_PREFLIGHT",
                    "step=" + step + ";duration_ms=" + duration
                            + ";token=" + (value instanceof IBinder ? "1" : "0"));
        } catch (Throwable error) {
            trace("TARGET_PROBE_PREFLIGHT",
                    "step=" + step + ";duration_ms="
                            + (SystemClock.elapsedRealtime() - started)
                            + ";error=" + safeToken(error.getClass().getSimpleName()));
        }
    }

    private static void probeInternalDisplayToken(Class<?> surfaceControlClass) {
        long started = SystemClock.elapsedRealtime();
        try {
            Method method = surfaceControlClass.getDeclaredMethod("getInternalDisplayToken");
            method.setAccessible(true);
            Object value = method.invoke(null);
            long duration = SystemClock.elapsedRealtime() - started;
            trace("TARGET_PROBE_PREFLIGHT",
                    "step=INTERNAL_DISPLAY_TOKEN;duration_ms=" + duration
                            + ";available=1;token=" + (value instanceof IBinder ? "1" : "0"));
        } catch (NoSuchMethodException missing) {
            trace("TARGET_PROBE_PREFLIGHT",
                    "step=INTERNAL_DISPLAY_TOKEN;duration_ms="
                            + (SystemClock.elapsedRealtime() - started)
                            + ";available=0");
        } catch (Throwable error) {
            trace("TARGET_PROBE_PREFLIGHT",
                    "step=INTERNAL_DISPLAY_TOKEN;duration_ms="
                            + (SystemClock.elapsedRealtime() - started)
                            + ";available=1;error="
                            + safeToken(error.getClass().getSimpleName()));
        }
    }

    private static boolean showPhase(Object control, Surface surface, String name,
            Integer layerStack, int bars, int background) throws Exception {
        drawPattern(surface, bars, background);
        trace("TARGET_PROBE_PHASE",
                "candidate=" + name
                        + ";layer_stack=" + (layerStack == null ? "DEFAULT" : layerStack)
                        + ";bars=" + bars);

        CountDownLatch commit = new CountDownLatch(1);
        Object transaction = newTransaction();
        try {
            transactionSetLayer(transaction, control, PROBE_LAYER);
            if (layerStack != null) {
                transactionSetLayerStack(transaction, control, layerStack.intValue());
            }
            transactionShow(transaction, control);
            transactionAddCommittedListener(transaction, commit);
            transactionApply(transaction);
        } finally {
            closeTransactionQuietly(transaction);
        }

        if (!commit.await(COMMIT_WAIT_MS, TimeUnit.MILLISECONDS)) {
            trace("TARGET_PROBE_FAIL_OPEN",
                    "reason=SHOW_TIMEOUT;candidate=" + name);
            return false;
        }
        trace("TARGET_PROBE_SHOWN",
                "candidate=" + name + ";evidence=TRANSACTION_COMMITTED");
        return true;
    }

    private static void drawPattern(Surface surface, int bars, int background)
            throws Exception {
        Canvas canvas = null;
        try {
            canvas = surface.lockCanvas(null);
            canvas.drawColor(background);

            Paint border = new Paint();
            border.setStyle(Paint.Style.STROKE);
            border.setStrokeWidth(18f);
            border.setColor(Color.rgb(235, 232, 255));
            canvas.drawRect(24f, 24f, PROBE_WIDTH - 24f, PROBE_HEIGHT - 24f, border);

            Paint bar = new Paint();
            bar.setColor(Color.rgb(235, 232, 255));
            float barWidth = 90f;
            float gap = 70f;
            float total = bars * barWidth + (bars - 1) * gap;
            float left = (Math.min(PROBE_WIDTH, 1100) - total) / 2f;
            float top = 250f;
            float bottom = 830f;
            for (int i = 0; i < bars; i++) {
                float x = left + i * (barWidth + gap);
                canvas.drawRect(x, top, x + barWidth, bottom, bar);
            }
        } finally {
            if (canvas != null) surface.unlockCanvasAndPost(canvas);
        }
    }

    private static Object buildSurfaceControl() throws Exception {
        Class<?> builderClass = Class.forName(SURFACE_CONTROL_BUILDER);
        Object builder = builderClass.getDeclaredConstructor().newInstance();
        builder = invokeBuilder(builder, "setName", new Class<?>[]{String.class},
                "Thor recovery target probe");
        builder = invokeBuilder(builder, "setBufferSize",
                new Class<?>[]{int.class, int.class}, PROBE_WIDTH, PROBE_HEIGHT);
        builder = invokeBuilder(builder, "setFormat",
                new Class<?>[]{int.class}, PixelFormat.RGBA_8888);
        builder = invokeBuilder(builder, "setOpaque",
                new Class<?>[]{boolean.class}, true);
        builder = invokeBuilder(builder, "setHidden",
                new Class<?>[]{boolean.class}, true);
        Method build = builderClass.getMethod("build");
        return build.invoke(builder);
    }

    private static Object invokeBuilder(Object builder, String method,
            Class<?>[] types, Object... args) throws Exception {
        Method call = builder.getClass().getMethod(method, types);
        return call.invoke(builder, args);
    }

    private static Surface surfaceFromControl(Object control) throws Exception {
        Class<?> surfaceControlClass = Class.forName(SURFACE_CONTROL);
        Constructor<Surface> constructor =
                Surface.class.getDeclaredConstructor(surfaceControlClass);
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

    private static void transactionSetLayerStack(Object transaction,
            Object control, int layerStack) throws Exception {
        Class<?> surfaceControlClass = Class.forName(SURFACE_CONTROL);
        Method method = transaction.getClass().getMethod(
                "setLayerStack", surfaceControlClass, int.class);
        method.invoke(transaction, control, layerStack);
    }

    private static void transactionShow(Object transaction, Object control)
            throws Exception {
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
                if ("toString".equals(name)) return "RecoverySurfaceTargetProbeListener";
                return null;
            }
        };
        Object listener = Proxy.newProxyInstance(
                listenerClass.getClassLoader(), new Class<?>[]{listenerClass}, handler);
        Method method = transaction.getClass().getMethod(
                "addTransactionCommittedListener", Executor.class, listenerClass);
        method.invoke(transaction, DIRECT_EXECUTOR, listener);
    }

    private static void transactionApply(Object transaction) throws Exception {
        transaction.getClass().getMethod("apply").invoke(transaction);
    }

    private static boolean detachCommitted(Object control, String reason) throws Exception {
        CountDownLatch commit = new CountDownLatch(1);
        Object transaction = newTransaction();
        try {
            transactionReparentToNull(transaction, control);
            transactionAddCommittedListener(transaction, commit);
            transactionApply(transaction);
        } finally {
            closeTransactionQuietly(transaction);
        }
        if (!commit.await(COMMIT_WAIT_MS, TimeUnit.MILLISECONDS)) return false;
        trace("TARGET_PROBE_REMOVED",
                "reason=" + reason + ";evidence=TRANSACTION_COMMITTED");
        return true;
    }

    private static void startTtlWatchdog(final long startedAt) {
        Thread watchdog = new Thread(new Runnable() {
            @Override public void run() {
                long deadline = startedAt + PROCESS_TTL_MS;
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

                Object control = activeControl;
                if (control != null) detachBestEffort(control);
                trace("TARGET_PROBE_FAIL_OPEN",
                        "reason=PROCESS_TTL;cleanup_requested="
                                + (control == null ? "0" : "1"));
                System.exit(0);
            }
        }, "thor-recovery-target-probe-ttl");
        watchdog.setDaemon(true);
        watchdog.start();
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

    private static String safeToken(String value) {
        if (value == null || value.isEmpty()) return "NONE";
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
                    + ";source=target-probe";
            RootLogFiles.append(BootTrace.PATH, line + "\n");
        } catch (Throwable ignored) {
            // Probe diagnostics never affect cleanup.
        }
    }

    private static final class Topology {
        final int count;
        final boolean knownThor;

        Topology(int count, boolean knownThor) {
            this.count = count;
            this.knownThor = knownThor;
        }
    }
}
