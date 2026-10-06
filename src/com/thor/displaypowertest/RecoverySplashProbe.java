package com.thor.displaypowertest;

import android.os.IBinder;
import android.os.Parcel;

import java.lang.reflect.Method;

/**
 * TEST-BRANCH ONLY no-reboot probe for the recovery splash renderer.
 *
 * Run from adb shell with this APK on CLASSPATH. The probe submits one fixed
 * root command through the same vendor PServer bridge already used by the app.
 * It does not restart composer/framework and accepts no caller-controlled shell
 * fragments.
 */
public final class RecoverySplashProbe {
    private RecoverySplashProbe() {}

    public static void main(String[] args) {
        boolean submitted = false;
        Parcel data = null;
        Parcel reply = null;
        try {
            if (args != null && args.length != 0) {
                System.out.println("submitted=0;reason=ARGS_NOT_ALLOWED");
                return;
            }

            Class<?> manager = Class.forName("android.os.ServiceManager");
            Method getService = manager.getDeclaredMethod("getService", String.class);
            getService.setAccessible(true);
            IBinder binder = (IBinder) getService.invoke(null, "PServerBinder");
            if (binder == null) {
                System.out.println("submitted=0;reason=NO_PSERVER");
                return;
            }

            // Everything is constant or resolved inside the privileged shell.
            // Fake baselines 1/2 make the fully recovered current PIDs satisfy
            // the exact-successor check without needing a framework restart.
            String command = String.join("\n",
                    "A=$(pm path com.thor.displaypowertest 2>/dev/null);A=${A#*:}",
                    "case \"$A\" in /data/app/*/base.apk) ;; *) exit 31;; esac",
                    "C=$(pidof vendor.qti.hardware.display.composer-service)",
                    "S=$(pidof surfaceflinger)",
                    "case \"$C:$S\" in *[!0-9:]*|:*|*:) exit 32;; esac",
                    "[ \"$C\" != 1 ] && [ \"$S\" != 2 ] || exit 33",
                    "CLASSPATH=$A app_process / com.thor.displaypowertest.RecoverySplash"
                            + " 1 \"$C\" 2 \"$S\" >/dev/null 2>&1 &");

            data = Parcel.obtain();
            reply = Parcel.obtain();
            data.writeStringArray(new String[]{command, "0"});
            submitted = binder.transact(0, data, reply, 0);
            System.out.println("submitted=" + (submitted ? "1" : "0"));
        } catch (Throwable error) {
            System.out.println("submitted=0;reason="
                    + error.getClass().getSimpleName().replaceAll("[^A-Za-z0-9_.-]", "_"));
        } finally {
            if (data != null) data.recycle();
            if (reply != null) reply.recycle();
        }
    }
}
