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

            // Keep the vendor-bridge command below the same conservative
            // 255-character ceiling used by DaemonLaunchScript. The previous
            // probe exceeded that budget, so binder transact could succeed
            // while the vendor bridge never executed the complete shell.
            //
            // Fake baselines 1/2 make the fully recovered current PIDs satisfy
            // the exact-successor check without needing a framework restart.
            String command = "A=$(pm path com.thor.displaypowertest);A=${A#*:};"
                    + "C=$(pidof vendor.qti.hardware.display.composer-service);"
                    + "S=$(pidof surfaceflinger);"
                    + "CLASSPATH=$A app_process / com.thor.displaypowertest.RecoverySplash"
                    + " 1 $C 2 $S >/dev/null 2>&1 &";
            if (command.length() > DaemonLaunchScript.MAX_COMMAND_CHARS) {
                System.out.println("submitted=0;reason=COMMAND_TOO_LONG;chars="
                        + command.length());
                return;
            }

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
