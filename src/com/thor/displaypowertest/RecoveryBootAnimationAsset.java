package com.thor.displaypowertest;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.system.Os;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Prototype-only asset builder for the Thor firmware's native bootanimation
 * custom-path hook. It never starts/stops services or changes properties.
 */
public final class RecoveryBootAnimationAsset {
    public static final String MARKER =
            "/data/local/tmp/thor-recovery-native-bootanim-prototype";
    public static final String OUTPUT =
            "/dev/jesty-thor-recovery-bootanimation.zip";
    public static final String CUSTOM_PROPERTY = "persist.sys.customanim.boot";
    public static final String DISPLAYS_PROPERTY = "persist.service.bootanim.displays";

    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;
    private static final int BACKGROUND = Color.rgb(14, 11, 24);

    private RecoveryBootAnimationAsset() {}

    public static boolean prototypeArmed() {
        try {
            if (!Files.isRegularFile(Paths.get(MARKER), LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(Paths.get(MARKER))) {
                return false;
            }
            String value = new String(Files.readAllBytes(Paths.get(MARKER)),
                    StandardCharsets.US_ASCII).trim();
            return "1".equals(value);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Builds a one-frame, indefinitely repeating, STORED bootanimation ZIP.
     * The native renderer owns timing/removal via service.bootanim.exit.
     */
    public static boolean prepare() {
        Bitmap brand = null;
        Bitmap frame = null;
        try {
            brand = loadBrandBitmap();
            if (brand == null || brand.getWidth() <= 0 || brand.getHeight() <= 0) {
                return false;
            }

            frame = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(frame);
            canvas.drawColor(BACKGROUND);

            float maxWidth = WIDTH * 0.56f;
            float maxHeight = HEIGHT * 0.24f;
            float scale = Math.min(maxWidth / brand.getWidth(),
                    maxHeight / brand.getHeight());
            float drawWidth = Math.max(1f, brand.getWidth() * scale);
            float drawHeight = Math.max(1f, brand.getHeight() * scale);
            float left = (WIDTH - drawWidth) / 2f;
            float top = (HEIGHT - drawHeight) / 2f;

            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            canvas.drawBitmap(brand, null,
                    new RectF(left, top, left + drawWidth, top + drawHeight), paint);

            ByteArrayOutputStream pngBytes = new ByteArrayOutputStream(256 * 1024);
            if (!frame.compress(Bitmap.CompressFormat.PNG, 100, pngBytes)) {
                return false;
            }

            byte[] desc = ("1920 1080 30\n"
                    + "p 0 0 part0\n").getBytes(StandardCharsets.US_ASCII);
            byte[] png = pngBytes.toByteArray();

            File output = new File(OUTPUT);
            if (output.exists() && !output.delete()) return false;

            try (FileOutputStream file = new FileOutputStream(output, false);
                    ZipOutputStream zip = new ZipOutputStream(file)) {
                putStored(zip, "desc.txt", desc);
                putStored(zip, "part0/frame000.png", png);
                zip.finish();
                file.getFD().sync();
            }

            Os.chmod(OUTPUT, 0644);
            return output.isFile() && output.length() > 0L;
        } catch (Throwable ignored) {
            try { new File(OUTPUT).delete(); } catch (Throwable ignoredAgain) {}
            return false;
        } finally {
            if (frame != null) frame.recycle();
            if (brand != null) brand.recycle();
        }
    }

    public static void cleanup() {
        try { new File(OUTPUT).delete(); } catch (Throwable ignored) {}
    }

    private static void putStored(ZipOutputStream zip, String name, byte[] data)
            throws Exception {
        CRC32 crc = new CRC32();
        crc.update(data);
        ZipEntry entry = new ZipEntry(name);
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(data.length);
        entry.setCompressedSize(data.length);
        entry.setCrc(crc.getValue());
        zip.putNextEntry(entry);
        zip.write(data);
        zip.closeEntry();
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
}
