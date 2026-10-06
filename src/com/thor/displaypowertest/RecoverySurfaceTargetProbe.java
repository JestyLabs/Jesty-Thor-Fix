package com.thor.displaypowertest;

import android.os.IBinder;
import android.os.Parcel;

import java.lang.reflect.Method;

/**
 * TEST-BRANCH ONLY launcher for the no-reboot recovery-surface targeting probe.
 *
 * The launcher accepts no caller-controlled arguments. It submits one fixed
 * root command through the vendor PServer bridge and never restarts composer,
 * SurfaceFlinger or framework services.
 */
public final class RecoverySurfaceTargetProbe {
    private RecoverySurfaceTargetProbe() {}

    public static void main(String[] args) {
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

            String command = "A=$(pm path com.thor.displaypowertest);A=${A#*:};"
                    + "CLASSPATH=$A app_process / "
                    + "com.thor.displaypowertest.RecoverySurfaceTargetProbeWorker"
                    + " >/dev/null 2>&1 &";
            if (command.length() > DaemonLaunchScript.MAX_COMMAND_CHARS) {
                System.out.println("submitted=0;reason=COMMAND_TOO_LONG;chars="
                        + command.length());
                return;
            }

            data = Parcel.obtain();
            reply = Parcel.obtain();
            data.writeStringArray(new String[]{command, "0"});
            boolean submitted = binder.transact(0, data, reply, 0);
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
