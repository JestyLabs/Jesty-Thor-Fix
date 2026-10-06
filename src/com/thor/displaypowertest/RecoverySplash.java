package com.thor.displaypowertest;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.view.Surface;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Enumeration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

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
    private static final long TARGET_WAIT_MS = 4500L;
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
        try {
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
        } finally {
            finished = true;
        }
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

            control = buildSurfaceControl(target.surfaceWidth, target.surfaceHeight);
            activeControl = control;
            surface = surfaceFromControl(control);
            trace("SPLASH_DRAW_BEGIN", "width=" + target.surfaceWidth
                    + ";height=" + target.surfaceHeight);
            String drawBackend = draw(surface, target.surfaceWidth, target.surfaceHeight);
            trace("SPLASH_DRAW_READY", "backend=" + drawBackend);

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
                    + ";width=" + target.surfaceWidth
                    + ";height=" + target.surfaceHeight);

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
            activeControl = null;
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
        String lastFailure = "NOT_READY";
        int attempts = 0;
        do {
            attempts++;
            try {
                DisplayTarget target = resolvePrimaryTopTarget();
                if (target != null) {
                    trace("SPLASH_TARGET_READY", "attempts=" + attempts
                            + ";native_width=" + target.nativeWidth
                            + ";native_height=" + target.nativeHeight
                            + ";surface_width=" + target.surfaceWidth
                            + ";surface_height=" + target.surfaceHeight);
                    return target;
                }
                lastFailure = "METADATA_NOT_READY";
            } catch (Throwable error) {
                // PID existence precedes the Thor's display-metadata publication by
                // roughly three seconds during framework recovery. Keep this bounded
                // and diagnostic rather than treating the first binder miss as fatal.
                lastFailure = safeToken(error.getClass().getSimpleName());
            }
            long remaining = deadline - SystemClock.elapsedRealtime();
            if (remaining <= 0L) break;
            Thread.sleep(Math.min(100L, remaining));
        } while (SystemClock.elapsedRealtime() < deadline);
        trace("SPLASH_TIMEOUT", "phase=TARGET;attempts=" + attempts
                + ";last=" + lastFailure);
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

        boolean topFound = false;
        for (long id : ids) {
            if (id == ThorHardwareProfile.TOP_PHYSICAL_DISPLAY_ID) {
                if (topFound) return null;
                topFound = true;
            } else if (id != ThorHardwareProfile.BOTTOM_PHYSICAL_DISPLAY_ID) {
                // Unknown physical topology: do not guess which display should
                // own the prototype layer.
                return null;
            }
        }
        if (!topFound) return null;

        long topId = ThorHardwareProfile.TOP_PHYSICAL_DISPLAY_ID;
        Object info = dynamicDisplayInfo(surfaceControlClass, topId);
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

            int surfaceWidth = width;
            int surfaceHeight = height;
            if (ThorHardwareProfile.TOP_LAYER_STACK_SWAPS_MODE_AXES) {
                // Measured on the tested Thor: DynamicDisplayInfo reports the
                // panel's natural 1080x1920 mode while the active layer stack
                // is 1920x1080. Fail open rather than guessing if that measured
                // relationship changes on another firmware/hardware revision.
                if (height <= width) return null;
                surfaceWidth = height;
                surfaceHeight = width;
            }
            return new DisplayTarget(topId, width, height, surfaceWidth, surfaceHeight);
        }
        return null;
    }

    /**
     * Android/Qualcomm branches around Android 13 exist with both long-ID and
     * IBinder-token DynamicDisplayInfo entry points. The Thor's power-control
     * path already proves getPhysicalDisplayToken(long) on this firmware, so
     * accept either hidden-API shape instead of baking in one framework variant.
     */
    private static Object dynamicDisplayInfo(Class<?> surfaceControlClass, long topId)
            throws Exception {
        try {
            Method infoMethod = surfaceControlClass.getDeclaredMethod(
                    "getDynamicDisplayInfo", long.class);
            infoMethod.setAccessible(true);
            return infoMethod.invoke(null, topId);
        } catch (NoSuchMethodException missingLongOverload) {
            Method tokenMethod = surfaceControlClass.getDeclaredMethod(
                    "getPhysicalDisplayToken", long.class);
            tokenMethod.setAccessible(true);
            Object token = tokenMethod.invoke(null, topId);
            if (!(token instanceof IBinder)) return null;
            Method infoMethod = surfaceControlClass.getDeclaredMethod(
                    "getDynamicDisplayInfo", IBinder.class);
            infoMethod.setAccessible(true);
            return infoMethod.invoke(null, token);
        }
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

    /**
     * Draws without any Typeface/font dependency.
     *
     * Standalone app_process does not initialize Android's default Typeface
     * environment like a normal app process. On the tested Thor firmware,
     * Text rendering aborts natively in Typeface::resolveDefault(). Keep
     * this recovery renderer bitmap/primitive only.
     */
    private static String draw(Surface surface, int width, int height) throws Exception {
        Canvas canvas = null;
        Bitmap brand = null;
        String backend = "PRIMITIVE_FALLBACK";
        try {
            canvas = surface.lockCanvas(null);
            canvas.drawColor(Color.rgb(14, 11, 24));

            brand = loadBrandBitmap();
            if (brand != null && brand.getWidth() > 0 && brand.getHeight() > 0) {
                float maxWidth = width * 0.62f;
                float maxHeight = height * 0.20f;
                float scale = Math.min(maxWidth / brand.getWidth(),
                        maxHeight / brand.getHeight());
                scale = Math.min(scale, 1.0f);
                float drawWidth = Math.max(1f, brand.getWidth() * scale);
                float drawHeight = Math.max(1f, brand.getHeight() * scale);
                float left = (width - drawWidth) / 2f;
                float top = (height - drawHeight) / 2f - height * 0.04f;

                Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG
                        | Paint.FILTER_BITMAP_FLAG);
                canvas.drawBitmap(brand, null,
                        new RectF(left, top, left + drawWidth, top + drawHeight),
                        bitmapPaint);
                backend = "PACKAGED_LOCKUP";
            } else {
                // Obvious dependency-free fallback for prototype validation.
                Paint panel = new Paint(Paint.ANTI_ALIAS_FLAG);
                panel.setColor(Color.rgb(124, 58, 237));
                float size = Math.min(width, height) * 0.20f;
                float cx = width / 2f;
                float cy = height / 2f;
                canvas.drawRoundRect(new RectF(cx - size, cy - size,
                        cx + size, cy + size), size * 0.18f, size * 0.18f, panel);

                Paint cut = new Paint(Paint.ANTI_ALIAS_FLAG);
                cut.setColor(Color.rgb(14, 11, 24));
                float bar = size * 0.22f;
                canvas.drawRect(cx - bar, cy - size * 0.55f,
                        cx + bar, cy + size * 0.55f, cut);
                canvas.drawRect(cx - size * 0.55f, cy - bar,
                        cx + size * 0.55f, cy + bar, cut);
            }

            Paint progressTrack = new Paint(Paint.ANTI_ALIAS_FLAG);
            progressTrack.setColor(Color.rgb(52, 45, 70));
            Paint progress = new Paint(Paint.ANTI_ALIAS_FLAG);
            progress.setColor(Color.rgb(196, 181, 253));
            float trackWidth = width * 0.34f;
            float trackHeight = Math.max(6f, Math.min(width, height) * 0.010f);
            float trackLeft = (width - trackWidth) / 2f;
            float trackTop = height * 0.66f;
            canvas.drawRoundRect(new RectF(trackLeft, trackTop,
                    trackLeft + trackWidth, trackTop + trackHeight),
                    trackHeight / 2f, trackHeight / 2f, progressTrack);
            canvas.drawRoundRect(new RectF(trackLeft, trackTop,
                    trackLeft + trackWidth * 0.72f, trackTop + trackHeight),
                    trackHeight / 2f, trackHeight / 2f, progress);
            return backend;
        } finally {
            if (canvas != null) surface.unlockCanvasAndPost(canvas);
            if (brand != null) brand.recycle();
        }
    }

    private static Bitmap loadBrandBitmap() {
        String apk = System.getenv("CLASSPATH");
        if (apk == null || !apk.matches("/data/app/.+/base\\.apk")) return null;

        ZipFile zip = null;
        try {
            zip = new ZipFile(apk);
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name == null || !name.startsWith("res/")
                        || !name.endsWith("/jesty_thor_header_lockup.png")) {
                    continue;
                }
                try (InputStream input = zip.getInputStream(entry)) {
                    return BitmapFactory.decodeStream(input);
                }
            }
        } catch (Throwable ignored) {
            return null;
        } finally {
            if (zip != null) {
                try { zip.close(); } catch (Throwable ignored) {}
            }
        }
        return null;
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
        final int nativeWidth;
        final int nativeHeight;
        final int surfaceWidth;
        final int surfaceHeight;

        DisplayTarget(long physicalId, int nativeWidth, int nativeHeight,
                int surfaceWidth, int surfaceHeight) {
            this.physicalId = physicalId;
            this.nativeWidth = nativeWidth;
            this.nativeHeight = nativeHeight;
            this.surfaceWidth = surfaceWidth;
            this.surfaceHeight = surfaceHeight;
        }
    }
}
