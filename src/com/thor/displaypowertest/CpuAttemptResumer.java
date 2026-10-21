package com.thor.displaypowertest;

import android.util.Log;
import java.util.function.Supplier;

/** Bounded retry worker; the controller retains all state, locks and effects. */
final class CpuAttemptResumer implements Runnable {
    private final Supplier<String> attempt;
    private final Runnable releaseWakeLock;

    CpuAttemptResumer(Supplier<String> attempt, Runnable releaseWakeLock) {
        this.attempt = attempt;
        this.releaseWakeLock = releaseWakeLock;
    }

    @Override public void run() {
        for (int poll = 0; poll < 130; poll++) {
            String result = attempt.get();
            if (result.startsWith("ok=1")) {
                // A newly scheduled restart retains ownership of the named
                // transition wake lock; the controller rechecks that state.
                releaseWakeLock.run();
                return;
            }
            if (!retryableAttemptResult(result)) {
                releaseWakeLock.run();
                Log.e("ThorDisplayDaemon", "CPU attempt resume stopped: " + result);
                return;
            }
            try {
                Thread.sleep(200L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        Log.e("ThorDisplayDaemon", "CPU attempt resume exceeded bounded wait");
    }

    static boolean retryableAttemptResult(String result) {
        return result != null && (result.contains("WATCHER_NOT_READY")
                || result.contains("COMPOSER_NOT_READY")
                || result.contains("COMPOSER_PID_UNKNOWN")
                || result.contains("CPU_FIX_STATE_UNKNOWN")
                || result.contains("BOOT_ID_UNKNOWN")
                || result.contains("COMPOSER_RESTART_PENDING")
                || result.contains("COMPOSER_RESTART_BUSY"));
    }
}
