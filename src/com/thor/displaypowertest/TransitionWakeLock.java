package com.thor.displaypowertest;

import android.util.Log;

/** Named kernel wake lock with a 150-second limit across a compositor restart. */
public final class TransitionWakeLock {
    private static final String NAME = "jesty_dashboard_cpu_transition";

    public boolean acquire() {
        try {
            // The post-restart phase can need 60 seconds after helper recovery.
            // The post-restart daemon releases this early after reconciliation.
            Process process = new ProcessBuilder("sh", "-c", "echo '"
                    + NAME + " 150000000000' > /sys/power/wake_lock").start();
            return process.waitFor() == 0;
        } catch (Throwable error) {
            Log.e("ThorDisplayDaemon", "transition wake lock failed", error);
            return false;
        }
    }

    public void release() {
        try {
            new ProcessBuilder("sh", "-c", "echo '" + NAME
                    + "' > /sys/power/wake_unlock").start().waitFor();
        } catch (Throwable error) {
            Log.e("ThorDisplayDaemon", "transition wake unlock failed", error);
        }
    }
}
