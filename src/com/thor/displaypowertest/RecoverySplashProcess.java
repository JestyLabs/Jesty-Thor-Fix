package com.thor.displaypowertest;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.Surface;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Standalone recovery overlay for the compositor-restart gap.
 *
 * This deliberately is not an Activity: it is launched by the root helper only
 * after the replacement SurfaceFlinger PID is observed, so it does not depend
 * on the system_server/WindowManager lifecycle that is recovering in parallel.
 */
public final class RecoverySplashProcess {
    public static final int DEFAULT_TIMEOUT_MS = 8000;
    private static final int MIN_TIMEOUT_MS = 1000;
    private static final int MAX_TIMEOUT_MS = 10000;
    private static final long CREATE_RETRY_MS = 1800L;
    private static final String LOGO_NAME = "jesty_thor_header_lockup.png";

    private RecoverySplashProcess() {}

    public static void main(String[] args) {
        final long timeoutMs = timeout(args);
        trace("RECOVERY_SPLASH_PROCESS_START", "timeout_ms=" + timeoutMs);

        Layer layer = null;
        Throwable lastError = null;
        long deadline = SystemClock.elapsedRealtime() + CREATE_RETRY_MS;
        do {
            try {
                layer = createLayer();
                break;
            } catch (Throwable error) {
                lastError = error;
                SystemClock.sleep(100L);
            }
        } while (SystemClock.elapsedRealtime() < deadline);

        if (layer == null) {
            trace("RECOVERY_SPLASH_CREATE_FAILED",
                    "reason=" + safeReason(lastError));
            return;
        }

        final Layer ownedLayer = layer;
        Thread shutdownHook = new Thread(() -> {
            if (ownedLayer.close()) {
                trace("RECOVERY_SPLASH_RELEASED", "reason=SIGNAL");
            }
        }, "recovery-splash-cleanup");

        try {
            Runtime.getRuntime().addShutdownHook(shutdownHook);
            trace("RECOVERY_SPLASH_SHOWN",
                    "width=" + layer.width + ";height=" + layer.height
                            + ";size_source=" + layer.sizeSource
                            + ";asset=" + (layer.logoDrawn ? "lockup" : "text"));
            SystemClock.sleep(timeoutMs);
            trace("RECOVERY_SPLASH_TIMEOUT", "timeout_ms=" + timeoutMs);
        } finally {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (Throwable ignored) {}
            if (layer.close()) {
                trace("RECOVERY_SPLASH_RELEASED", "reason=TIMEOUT_OR_EXIT");
            }
        }
    }

    private static Layer createLayer() throws Exception {
        Class<?> surfaceControlClass = Class.forName("android.view.SurfaceControl");
        DisplaySpec spec = displaySpec(surfaceControlClass);

        Class<?> sessionClass = Class.forName("android.view.SurfaceSession");
        Constructor<?> sessionConstructor = sessionClass.getDeclaredConstructor();
        sessionConstructor.setAccessible(true);
        Object session = sessionConstructor.newInstance();

        Class<?> builderClass = Class.forName("android.view.SurfaceControl$Builder");
        Object builder = newBuilder(builderClass, sessionClass, session);
        call(builderClass, builder, "setName", new Class<?>[]{String.class},
                "JestyThorRecoverySplash");
        call(builderClass, builder, "setBufferSize",
                new Class<?>[]{int.class, int.class}, spec.width, spec.height);
        optionalCall(builderClass, builder, "setFormat",
                new Class<?>[]{int.class}, PixelFormat.RGBA_8888);
        optionalCall(builderClass, builder, "setOpaque",
                new Class<?>[]{boolean.class}, true);
        Object control = call(builderClass, builder, "build", new Class<?>[0]);

        Surface surface = new Surface();
        Method copyFrom = Surface.class.getDeclaredMethod("copyFrom", surfaceControlClass);
        copyFrom.setAccessible(true);
        copyFrom.invoke(surface, control);

        boolean logoDrawn = draw(surface, spec.width, spec.height);

        Class<?> transactionClass = Class.forName("android.view.SurfaceControl$Transaction");
        Constructor<?> transactionConstructor = transactionClass.getDeclaredConstructor();
        transactionConstructor.setAccessible(true);
        Object transaction = transactionConstructor.newInstance();
        // Android bootanimation uses the default layer stack and a very high
        // layer. Mirror that direct-SurfaceFlinger shape without an Activity.
        optionalCall(transactionClass, transaction, "setLayerStack",
                new Class<?>[]{surfaceControlClass, int.class}, control, 0);
        call(transactionClass, transaction, "setLayer",
                new Class<?>[]{surfaceControlClass, int.class}, control, 0x40000000);
        call(transactionClass, transaction, "show",
                new Class<?>[]{surfaceControlClass}, control);
        call(transactionClass, transaction, "apply", new Class<?>[0]);

        return new Layer(surface, session, control, surfaceControlClass,
                transactionClass, spec.width, spec.height, spec.source, logoDrawn);
    }

    private static Object newBuilder(Class<?> builderClass, Class<?> sessionClass,
            Object session) throws Exception {
        try {
            Constructor<?> constructor = builderClass.getDeclaredConstructor(sessionClass);
            constructor.setAccessible(true);
            return constructor.newInstance(session);
        } catch (NoSuchMethodException ignored) {
            Constructor<?> constructor = builderClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        }
    }

    private static DisplaySpec displaySpec(Class<?> surfaceControlClass) throws Exception {
        try {
            Method tokenMethod = surfaceControlClass.getDeclaredMethod("getInternalDisplayToken");
            tokenMethod.setAccessible(true);
            Object token = tokenMethod.invoke(null);
            if (token instanceof IBinder) {
                Method dynamicMethod = surfaceControlClass.getDeclaredMethod(
                        "getDynamicDisplayInfo", IBinder.class);
                dynamicMethod.setAccessible(true);
                Object info = dynamicMethod.invoke(null, token);
                DisplaySpec dynamic = activeDisplayMode(info);
                if (dynamic != null) return dynamic;
            }
        } catch (Throwable ignored) {
            // Fall through to system resources. Creation remains fail-open.
        }

        DisplayMetrics metrics = Resources.getSystem().getDisplayMetrics();
        if (metrics != null && metrics.widthPixels > 0 && metrics.heightPixels > 0) {
            return new DisplaySpec(metrics.widthPixels, metrics.heightPixels,
                    "system_resources");
        }
        throw new IllegalStateException("display_size_unavailable");
    }

    private static DisplaySpec activeDisplayMode(Object dynamicInfo) {
        if (dynamicInfo == null) return null;
        try {
            Class<?> infoClass = dynamicInfo.getClass();
            Field modesField = infoClass.getField("supportedDisplayModes");
            Field activeField = infoClass.getField("activeDisplayModeId");
            Object modes = modesField.get(dynamicInfo);
            int activeId = activeField.getInt(dynamicInfo);
            if (modes == null) return null;

            int length = Array.getLength(modes);
            Object fallback = null;
            for (int i = 0; i < length; i++) {
                Object mode = Array.get(modes, i);
                if (mode == null) continue;
                int width = intField(mode, "width");
                int height = intField(mode, "height");
                if (width <= 0 || height <= 0) continue;
                if (fallback == null) fallback = mode;
                if (intField(mode, "id") == activeId) {
                    return new DisplaySpec(width, height, "surfacecontrol_active_mode");
                }
            }
            if (fallback != null) {
                return new DisplaySpec(intField(fallback, "width"),
                        intField(fallback, "height"), "surfacecontrol_first_mode");
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static int intField(Object target, String name) throws Exception {
        Field field = target.getClass().getField(name);
        field.setAccessible(true);
        return field.getInt(target);
    }

    private static boolean draw(Surface surface, int width, int height) throws Exception {
        Canvas canvas = null;
        Bitmap logo = null;
        try {
            canvas = surface.lockCanvas(null);
            canvas.drawColor(Color.BLACK);

            logo = loadLogo();
            boolean logoDrawn = logo != null && logo.getWidth() > 0 && logo.getHeight() > 0;
            if (logoDrawn) {
                float maxWidth = width * 0.58f;
                float maxHeight = height * 0.30f;
                float scale = Math.min(maxWidth / logo.getWidth(),
                        maxHeight / logo.getHeight());
                scale = Math.min(scale, 1.0f);
                float drawWidth = logo.getWidth() * scale;
                float drawHeight = logo.getHeight() * scale;
                float left = (width - drawWidth) / 2.0f;
                float top = height * 0.43f - drawHeight / 2.0f;
                canvas.drawBitmap(logo, null,
                        new android.graphics.RectF(left, top,
                                left + drawWidth, top + drawHeight), null);
            } else {
                Paint title = textPaint(width, height, true);
                canvas.drawText("JESTY THOR FIX", width / 2.0f, height * 0.48f, title);
            }

            Paint subtitle = textPaint(width, height, false);
            canvas.drawText("Restoring display\u2026",
                    width / 2.0f, height * 0.66f, subtitle);
            return logoDrawn;
        } finally {
            if (canvas != null) {
                try { surface.unlockCanvasAndPost(canvas); }
                catch (Throwable ignored) {}
            }
            if (logo != null) logo.recycle();
        }
    }

    private static Paint textPaint(int width, int height, boolean title) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(title ? Color.WHITE : Color.LTGRAY);
        paint.setTextAlign(Paint.Align.CENTER);
        float base = Math.min(width, height);
        paint.setTextSize(Math.max(title ? 36.0f : 26.0f,
                base * (title ? 0.052f : 0.032f)));
        paint.setFakeBoldText(title);
        return paint;
    }

    private static Bitmap loadLogo() {
        String classPath = System.getenv("CLASSPATH");
        if (!RecoverySplashFlag.classPathUsable(classPath)) return null;
        try (ZipFile zip = new ZipFile(classPath)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!entry.isDirectory()
                        && (name.equals("res/drawable-nodpi/" + LOGO_NAME)
                        || name.endsWith("/" + LOGO_NAME))) {
                    return BitmapFactory.decodeStream(zip.getInputStream(entry));
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static Object call(Class<?> type, Object target, String name,
            Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = type.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static void optionalCall(Class<?> type, Object target, String name,
            Class<?>[] parameterTypes, Object... args) {
        try {
            call(type, target, name, parameterTypes, args);
        } catch (Throwable ignored) {}
    }

    private static long timeout(String[] args) {
        long parsed = DEFAULT_TIMEOUT_MS;
        if (args != null && args.length > 0) {
            try { parsed = Long.parseLong(args[0]); }
            catch (NumberFormatException ignored) {}
        }
        return Math.max(MIN_TIMEOUT_MS, Math.min(MAX_TIMEOUT_MS, parsed));
    }

    private static String safeReason(Throwable error) {
        if (error == null) return "UNKNOWN";
        String name = error.getClass().getSimpleName();
        return name == null || name.isEmpty() ? "THROWABLE" : name;
    }

    private static void trace(String action, String detail) {
        String bootId = SecureChannel.currentBootId();
        String line = "elapsed_ms=" + SystemClock.elapsedRealtime()
                + ";action=" + action
                + (detail == null || detail.isEmpty() ? "" : ";" + detail)
                + ";pid=" + android.os.Process.myPid()
                + ";boot_id=" + (bootId == null || bootId.isEmpty() ? "?" : bootId)
                + ";source=splash\n";
        RootLogFiles.rotateIfLarger(BootTrace.PATH, 256L * 1024L);
        RootLogFiles.append(BootTrace.PATH, line);
    }

    private static final class DisplaySpec {
        final int width;
        final int height;
        final String source;

        DisplaySpec(int width, int height, String source) {
            this.width = width;
            this.height = height;
            this.source = source;
        }
    }

    private static final class Layer {
        final Surface surface;
        final Object session;
        final Object control;
        final Class<?> surfaceControlClass;
        final Class<?> transactionClass;
        final int width;
        final int height;
        final String sizeSource;
        final boolean logoDrawn;
        private boolean closed;

        Layer(Surface surface, Object session, Object control,
                Class<?> surfaceControlClass, Class<?> transactionClass,
                int width, int height, String sizeSource, boolean logoDrawn) {
            this.surface = surface;
            this.session = session;
            this.control = control;
            this.surfaceControlClass = surfaceControlClass;
            this.transactionClass = transactionClass;
            this.width = width;
            this.height = height;
            this.sizeSource = sizeSource;
            this.logoDrawn = logoDrawn;
        }

        synchronized boolean close() {
            if (closed) return false;
            closed = true;
            try {
                Constructor<?> constructor = transactionClass.getDeclaredConstructor();
                constructor.setAccessible(true);
                Object transaction = constructor.newInstance();
                optionalCall(transactionClass, transaction, "remove",
                        new Class<?>[]{surfaceControlClass}, control);
                optionalCall(transactionClass, transaction, "apply", new Class<?>[0]);
            } catch (Throwable ignored) {}
            try { surface.release(); } catch (Throwable ignored) {}
            optionalCall(surfaceControlClass, control, "release", new Class<?>[0]);
            optionalCall(session.getClass(), session, "kill", new Class<?>[0]);
            return true;
        }
    }
}
