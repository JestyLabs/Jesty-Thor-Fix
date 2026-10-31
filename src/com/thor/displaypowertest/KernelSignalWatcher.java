package com.thor.displaypowertest;

import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Reads new kernel log records in the root daemon and latches the lower panel when one
 * marks a poisoned lower DSI link (KernelSignalModel). /dev/kmsg gives this reader its
 * own cursor and never consumes records for other readers (unlike /proc/kmsg). The
 * thread blocks in read(): no polling, no wakelock.
 */
final class KernelSignalWatcher implements Runnable {
    private static final String TAG = "ThorDisplayDaemon";
    private static final String KMSG = "/dev/kmsg";
    private static final int MAX_CONSECUTIVE_ERRORS = 3;
    private boolean noticePosted;

    private KernelSignalWatcher() {}

    /** Called once by the daemon after the exclusive lock. */
    static void start() {
        Thread thread = new Thread(new KernelSignalWatcher(), "thor-kmsg");
        thread.setDaemon(true);
        thread.start();
    }

    @Override public void run() {
        FileInputStream in;
        try {
            in = new FileInputStream(KMSG);
            // Only records written from now on: older ones belong to earlier events.
            Os.lseek(in.getFD(), 0L, OsConstants.SEEK_END);
        } catch (IOException | ErrnoException | RuntimeException error) {
            DisplayActionCoordinator.setKernelSignalsAvailable(false);
            Log.d(TAG, "KERNEL_SIGNALS unavailable=" + error.getClass().getSimpleName());
            return;
        }
        DisplayActionCoordinator.setKernelSignalsAvailable(true);
        Log.d(TAG, "KERNEL_SIGNALS ready");
        byte[] buffer = new byte[8192];
        int errors = 0;
        try {
            while (true) {
                int length;
                try {
                    length = in.read(buffer);
                } catch (IOException overwritten) {
                    // EPIPE: records were overwritten before this reader got them; the
                    // next read continues with the oldest record still available.
                    if (++errors >= MAX_CONSECUTIVE_ERRORS) throw overwritten;
                    continue;
                }
                if (length < 0) break;
                errors = 0;
                String record = new String(buffer, 0, length, StandardCharsets.UTF_8);
                String reason = KernelSignalModel.classify(record);
                if (reason == null) continue;
                Log.d(TAG, "KERNEL_SIGNAL reason=" + reason + ";excerpt=" + KernelSignalModel.excerpt(record));
                DisplayActionCoordinator.latchLowerPoisoned(reason);
                postNotice();
            }
        } catch (IOException | RuntimeException error) {
            Log.e(TAG, "kernel signal reader stopped", error);
        } finally {
            DisplayActionCoordinator.setKernelSignalsAvailable(false);
            try { in.close(); } catch (IOException ignored) {}
        }
    }

    /** One best-effort system notification per daemon process; the dashboard also shows it. */
    private void postNotice() {
        if (noticePosted) return;
        noticePosted = true;
        Process process = null;
        try {
            process = new ProcessBuilder("/system/bin/cmd", "notification", "post", "-S", "bigtext",
                    "-t", "Lower screen fault", "jesty-lower-fault",
                    "The lower screen cannot start again until you restart the Thor. "
                            + "Restart now to avoid a black screen.")
                    .redirectErrorStream(true).start();
            boolean ok = ProcessWait.exited(process, 3000L) && process.exitValue() == 0;
            Log.d(TAG, "LOWER_FAULT_NOTICE posted=" + (ok ? "1" : "0"));
        } catch (Throwable error) {
            Log.e(TAG, "lower fault notice failed", error);
        } finally {
            if (process != null) process.destroy();
        }
    }
}
