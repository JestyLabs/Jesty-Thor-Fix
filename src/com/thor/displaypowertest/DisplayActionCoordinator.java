package com.thor.displaypowertest;

import android.os.SystemClock;
import android.util.Log;

/** The only author of lower-panel transitions and guard-requested sleep. */
public final class DisplayActionCoordinator {
    private static final String TAG = "ThorDisplayDaemon";
    private static volatile String lastMode = "?";
    private static volatile String status = "STARTING";
    private static final DisplayGenerationModel generations = new DisplayGenerationModel();
    private static long lastHardwareReadAt = -1L;
    private static long lastAttemptAt;
    private static String lastAttemptKey = "";
    private static String lastObservedCrtc = "";
    /**
     * A display power call that stays blocked longer than this was observed only while
     * the vendor composer power-reset loop held its locks (7.2-70 s); every completed
     * transition in the same traces took <= 1.71 s. Classification only, not a delay.
     */
    private static final long STALLED_CALL_MS = 5000L;
    private static volatile boolean lowerOnBackoff;
    /** Rate limit for the interactive query, which runs only while the back-off is set. */
    private static final long INTERACTIVE_QUERY_MS = 1000L;
    private static long lastInteractiveQueryAt = Long.MIN_VALUE / 2; // watcher thread only
    private static boolean sawNonInteractive;
    private static boolean bothDegraded;
    /**
     * Boot-scoped companion of the back-off. On 2026-10-20 every lower-panel start after
     * the first failure failed until reboot, and automatic BOTH ON re-entered the vendor
     * reset loop after the app's own OFF. A stalled call therefore also latches suppression
     * of automatic lower ON for the life of this daemon process (normally until reboot).
     * Mode changes, TOP wake and sleep/wake clear only the back-off; confirmed manual
     * reconciliation is the only in-process clear. The lower OFF path is never blocked.
     */
    private static volatile boolean lowerFailureLatched;
    /**
     * S1 sleep-order race (2026-10-28, 2/2 BOTH lid sleeps; 7 of 20 BOTH sleeps on 1.7.5):
     * going to sleep the platform may power the lower panel off first, so the watcher sees
     * mode=0;top=1;bottom=0 for ~0.2-0.4 s before the top follows. Restoring the lower panel
     * then left it powered for the whole sleep. In stable BOTH (no recent mode edge) a lower
     * ON therefore waits for a second observation and a fresh interactive query; a user mode
     * switch keeps its immediate ON. Watcher thread only.
     */
    private static final long BOTH_ON_CONFIRM_MS = 300L;
    private static final long MODE_EDGE_GRACE_MS = 1600L;
    private static final long CANDIDATE_QUERY_MS = 100L;
    private static long bothOnCandidateAt = -1L;
    private static String candidateInteractive = "?";
    private static boolean candidateQueried;
    private static boolean candidateConfirmed;
    private static boolean candidateSkipLogged;
    private static long lastModeEdgeAt = Long.MIN_VALUE / 2;

    private DisplayActionCoordinator() {}
    public static String status() { return status; }
    public static long generation() { return generations.current(); }

    /**
     * Called by the Settings watcher. Idle samples are slow; relevant display
     * events and mode edges wake a short 20 ms burst. DRM reads remain
     * independently event/safety driven.
     */
    public static void onWatcherSample(String sampledMode) {
        String mode = BootSafety.knownMode(sampledMode) ? sampledMode : "?";
        boolean stableTopWake = "1".equals(mode) && mode.equals(lastMode)
                && !BootSafety.isHeld() && DaemonState.isEnabled()
                && "1".equals(DisplayHardware.getProperty());
        // Avoid an inverted lock order with LidGuard's synchronized methods.
        boolean deferredByLid = stableTopWake && LidGuard.isEnabled()
                && LidGuard.onWakeEvent();
        // Binder query outside the coordinator lock, only while the back-off is set or a
        // stable-BOTH lower ON is waiting for confirmation.
        String interactive = "?";
        boolean bothCandidate = bothOnCandidateAt >= 0L;
        if (lowerOnBackoff || bothCandidate) {
            long queryAt = SystemClock.elapsedRealtime();
            long interval = bothCandidate ? CANDIDATE_QUERY_MS : INTERACTIVE_QUERY_MS;
            if (queryAt - lastInteractiveQueryAt >= interval) {
                lastInteractiveQueryAt = queryAt;
                interactive = readInteractive();
                if (bothCandidate) {
                    candidateInteractive = interactive;
                    candidateQueried = true;
                }
            }
        }
        synchronized (DisplayHardware.class) {
            // A real sleep/wake (interactive false -> true) ends the back-off; CRTC 0/0
            // is not used because the vendor reset loop passes through it every cycle.
            if ("0".equals(interactive)) sawNonInteractive = true;
            else if ("1".equals(interactive) && sawNonInteractive) clearLowerOnBackoffLocked("POWER_CYCLE");
            boolean changed = !mode.equals(lastMode);
            if (changed) {
                generations.invalidate();
                cancelRepairLocked();
                // Observation only: mode edges are otherwise visible only as status.
                Log.d(TAG, "MODE_OBSERVED mode=" + mode + ";previous=" + lastMode);
                // Leaving BOTH ends the back-off; entering BOTH must not (the stall can
                // span the mode switch, as in the 2026-10-20 recurrence).
                if ("1".equals(mode) || "2".equals(mode)) clearLowerOnBackoffLocked("MODE_" + mode);
                lastMode = mode;
                lastModeEdgeAt = SystemClock.elapsedRealtime();
                clearBothOnCandidate();
                status = "MODE_CHANGED";
                WatcherCadence.onModeChanged();
            }
            DaemonState.setMode(mode);
            if (!BootSafety.knownMode(mode) || BootSafety.isHeld()) {
                status = BootSafety.isHeld() ? "BOOT_HOLD" : "MODE_UNKNOWN";
                return;
            }
            if (!DaemonState.isEnabled()) {
                DisplayHardware.notifyWatcherSample(false, "?");
                cancelRepairLocked();
                status = "STOCK";
                return;
            }
            DisplayHardware.notifyWatcherSample(true, "?");
            long now = SystemClock.elapsedRealtime();
            boolean due = WakeRepairScheduler.isDue();
            long readGeneration = WatcherCadence.beginDrmRead(lastHardwareReadAt,
                    changed, due, stableTopWake);
            if (readGeneration < 0L) return;
            String[] crtc = Telemetry.crtcActivePair();
            lastHardwareReadAt = now;
            WatcherCadence.completeDrmRead(readGeneration);
            String top = crtc[0];
            String bottom = crtc[1];
            DisplayHardware.notifyWatcherSample(true, bottom);
            // Observation only: edge-log the paired DRM snapshot the watcher already
            // took, so a later blackout can be placed against CRTC state.
            String observedCrtc = top + bottom;
            if (!observedCrtc.equals(lastObservedCrtc)) {
                lastObservedCrtc = observedCrtc;
                Log.d(TAG, "CRTC_OBSERVED top=" + top + ";bottom=" + bottom + ";mode=" + mode
                        + ";property=" + DisplayHardware.getProperty());
            }
            // A candidate exists only while BOTH still shows an active top and an inactive lower.
            if (!("0".equals(mode) && "1".equals(top) && "0".equals(bottom))) clearBothOnCandidate();
            if ("0".equals(top) && "0".equals(bottom)) {
                if (WakeRepairScheduler.isPending()) generations.invalidate();
                cancelRepairLocked();
                status = "DISPLAY_ASLEEP_OR_TRANSITION";
                return;
            }
            if (stableTopWake && "1".equals(top) && "1".equals(bottom)
                    && !WakeRepairScheduler.isPending()) {
                if (deferredByLid) return;
                clearLowerOnBackoffLocked("TOP_WAKE");
                scheduleRepairLocked();
                return;
            }
            if (due) {
                if (!generations.isRepairCurrent() || !"1".equals(mode)
                        || !"1".equals(top)) {
                    cancelRepairLocked();
                } else {
                    DaemonState.onRepairBegin();
                    if ("0".equals(bottom) || attempt(false, "WAKE_REPAIR", mode)) {
                        WakeRepairScheduler.markDone();
                        generations.completeRepair();
                        DaemonState.onRepairEnd("OFF_OK");
                        Log.d(TAG, "WAKE_REPAIR_RESULT result=OFF_OK;watcher_health="
                                + WatcherSupervisor.health());
                    } else {
                        DaemonState.onRepairEnd("RETRY_PENDING");
                    }
                    return;
                }
            }
            // Owner-accepted BOTH degrade: after a measured compositor stall the lower
            // panel is turned OFF, the only action observed to end the vendor reset loop.
            // The boot latch keeps this after the back-off itself has been cleared.
            if ((lowerOnBackoff || lowerFailureLatched) && "0".equals(mode) && "1".equals(bottom)) {
                // A new active-lower observation invalidates the prior isolation result.
                // Preserve a current error/readback failure instead of historical success.
                status = "BOTH_ON_BACKOFF";
                bothDegraded = attempt(false, "BOTH_DEGRADE", mode);
                if (bothDegraded) status = "BOTH_DEGRADED";
                return;
            }
            DisplayDecisionModel.Action action = DisplayDecisionModel.reconcile(
                    mode, top, bottom, true, false, WakeRepairScheduler.isPending());
            if (action == DisplayDecisionModel.Action.OFF) attempt(false, "WATCH_TOP", mode);
            else if (action == DisplayDecisionModel.Action.ON && (lowerOnBackoff || lowerFailureLatched)) {
                String held = bothDegraded ? "BOTH_DEGRADED" : "BOTH_ON_BACKOFF";
                if (!held.equals(status)) Log.d(TAG, "WATCH_BOTH_SUPPRESSED backoff=" + (lowerOnBackoff ? "1" : "0")
                        + ";latched=" + (lowerFailureLatched ? "1" : "0") + ";degraded=" + (bothDegraded ? "1" : "0"));
                status = held;
            }
            else if (action == DisplayDecisionModel.Action.ON
                    && now - lastModeEdgeAt < MODE_EDGE_GRACE_MS) {
                // A user TOP -> BOTH switch: the device is interactive, keep the immediate ON.
                clearBothOnCandidate();
                attempt(true, "WATCH_BOTH", mode);
            }
            else if (action == DisplayDecisionModel.Action.ON) confirmBothOnLocked(mode, now);
            else if (!WakeRepairScheduler.isPending()) status =
                    DisplayDecisionModel.effective(mode, top, bottom, true, false);
        }
    }

    /** Manual fix toggle; one observation line per request (2026-10-28: the toggle left no trace). */
    public static String requestFix(boolean requested) {
        String reply = requestFixLocked(requested);
        Log.d(TAG, "USER_RECONCILE fix=" + (requested ? "1" : "0") + ";result="
                + (reply.startsWith("ok=1;") ? "CONFIRMED" : reply.replace("ok=0;error=", "")));
        return reply;
    }
    private static String requestFixLocked(boolean requested) {
        synchronized (DisplayHardware.class) {
            if (BootSafety.isHeld()) return "ok=0;error=BOOT_HOLD";
            if (!"RUNNING".equals(WatcherSupervisor.health()))
                return "ok=0;error=WATCHER_NOT_READY";
            String mode = readCurrentMode();
            if (!BootSafety.knownMode(mode) || !mode.equals(DaemonState.getMode()))
                return "ok=0;error=DISPLAY_MODE_UNKNOWN";
            // A single DRM snapshot avoids two debugfs reads and contradictory
            // TOP/BOTTOM observations during a mode transition.
            String[] crtc = Telemetry.crtcActivePair();
            String top = crtc[0];
            String bottom = crtc[1];
            if (!DisplayDecisionModel.readyForReconcile(mode, top, bottom))
                return "ok=0;error=DISPLAY_TRANSITION";
            boolean targetOn = !requested || !"1".equals(mode);
            if (!applyConfirmed(targetOn, "USER_RECONCILE", mode))
                return "ok=0;error=DISPLAY_UNCONFIRMED";
            generations.invalidate();
            cancelRepairLocked();
            clearLowerOnBackoffLocked("USER_RECONCILE");
            clearLowerFailureLatchLocked("USER_RECONCILE");
            DaemonState.setEnabled(requested);
            status = "CONFIRMED";
            return "ok=1;fix=" + (requested ? "1" : "0") + ";display=confirmed";
        }
    }

    /** Called only after the boot gate has observed a stable mode and CRTCs. */
    public static boolean reconcileBoot(String expectedMode, boolean targetOn) {
        synchronized (DisplayHardware.class) {
            if (!BootSafety.knownMode(expectedMode)
                    || !expectedMode.equals(readCurrentMode())) return false;
            String[] crtc = Telemetry.crtcActivePair();
            if (!DisplayDecisionModel.readyForReconcile(expectedMode,
                    crtc[0], crtc[1])) return false;
            if (!applyConfirmed(targetOn, "BOOT_RECONCILE", expectedMode)) return false;
            generations.invalidate();
            cancelRepairLocked();
            status = "CONFIRMED";
            return true;
        }
    }

    public static void cancelWakeRepair() {
        synchronized (DisplayHardware.class) { generations.invalidate(); cancelRepairLocked(); }
    }
    public static void scheduleWakeRepair() {
        synchronized (DisplayHardware.class) {
            if (BootSafety.isHeld() || !DaemonState.isEnabled()
                    || !"1".equals(DaemonState.getMode())) return;
            scheduleRepairLocked();
        }
    }
    public static void noteDisplayOn() {
        synchronized (DisplayHardware.class) {
            if (generations.isRepairCurrent() && WakeRepairScheduler.isPending())
                WakeRepairScheduler.noteDisplayOn();
        }
    }

    /** Pending work is internal; it must not change the firmware sleep/wake flag. */
    public static boolean isWakeRepairPending() {
        synchronized (DisplayHardware.class) {
            return generations.isRepairCurrent() && WakeRepairScheduler.isPending();
        }
    }

    /** LidGuard decides policy; this method serializes and rechecks the action. */
    public static boolean requestGuardSleep(long authorizedLidEpoch) {
        synchronized (DisplayHardware.class) {
            // Recheck the *same* closed-lid observation that authorized this
            // attempt, immediately before canceling repair / issuing input.
            if (BootSafety.isHeld() || !LidGuard.isEnabled()
                    || !LidGuard.isSleepDecisionCurrent(authorizedLidEpoch)
                    || !"0".equals(LidGuard.externalDisplayCached())) return false;
            generations.invalidate();
            cancelRepairLocked();
            Process process = null;
            try {
                process = new ProcessBuilder("input", "keyevent", "223").start();
                boolean slept = ProcessWait.exited(process, 2000L)
                        && process.exitValue() == 0;
                status = slept ? "SLEEP_REQUESTED" : "SLEEP_FAILED";
                return slept;
            } catch (Throwable error) {
                status = "SLEEP_FAILED";
                Log.e(TAG, "guard sleep command failed", error);
                return false;
            } finally {
                if (process != null) process.destroy();
            }
        }
    }

    /** True while automatic lower-panel ON in BOTH is suppressed after a stalled call. */
    public static boolean isLowerOnBackoff() {
        synchronized (DisplayHardware.class) { return lowerOnBackoff; }
    }
    /** True for the rest of this daemon process after a stalled call, unless manually reconciled. */
    public static boolean isLowerFailureLatched() {
        synchronized (DisplayHardware.class) { return lowerFailureLatched; }
    }
    private static void noteHardwareCallLocked(String reason, long blockedMs) {
        if (blockedMs <= STALLED_CALL_MS) return;
        if (!lowerFailureLatched) {
            lowerFailureLatched = true;
            Log.d(TAG, "LOWER_FAILURE_LATCH set reason=" + reason + ";call_ms=" + blockedMs);
        }
        if (lowerOnBackoff) return;
        lowerOnBackoff = true;
        sawNonInteractive = false;
        Log.d(TAG, "LOWER_ON_BACKOFF set reason=" + reason + ";call_ms=" + blockedMs);
    }
    private static void clearLowerOnBackoffLocked(String cause) {
        if (!lowerOnBackoff) return;
        lowerOnBackoff = false;
        sawNonInteractive = false;
        bothDegraded = false;
        Log.d(TAG, "LOWER_ON_BACKOFF cleared cause=" + cause);
    }
    private static void clearLowerFailureLatchLocked(String cause) {
        if (!lowerFailureLatched) return;
        lowerFailureLatched = false;
        Log.d(TAG, "LOWER_FAILURE_LATCH cleared cause=" + cause);
    }
    /**
     * Stable-BOTH lower ON: first observation only records a candidate; the ON is issued when
     * the pattern persisted BOTH_ON_CONFIRM_MS and the fresh query did not report a
     * non-interactive (going to sleep) device. An unavailable query keeps the old contract.
     */
    private static void confirmBothOnLocked(String mode, long now) {
        if (bothOnCandidateAt < 0L) {
            clearBothOnCandidate();
            bothOnCandidateAt = now;
            status = "DISPLAY_ASLEEP_OR_TRANSITION";
            return;
        }
        if ("0".equals(candidateInteractive)) {
            if (!candidateSkipLogged) {
                candidateSkipLogged = true;
                Log.d(TAG, "WATCH_BOTH_SKIPPED reason=NON_INTERACTIVE;candidate_ms=" + (now - bothOnCandidateAt));
            }
            status = "DISPLAY_ASLEEP_OR_TRANSITION";
            return;
        }
        if (!candidateConfirmed) {
            if (!candidateQueried || now - bothOnCandidateAt < BOTH_ON_CONFIRM_MS) {
                status = "DISPLAY_ASLEEP_OR_TRANSITION";
                return;
            }
            candidateConfirmed = true;
            Log.d(TAG, "WATCH_BOTH_CONFIRMED interactive=" + candidateInteractive
                    + ";candidate_ms=" + (now - bothOnCandidateAt));
        }
        // Once confirmed, retries keep the existing attempt() interval until the pattern ends.
        attempt(true, "WATCH_BOTH", mode);
    }
    private static void clearBothOnCandidate() {
        bothOnCandidateAt = -1L;
        candidateInteractive = "?";
        candidateQueried = false;
        candidateConfirmed = false;
        candidateSkipLogged = false;
    }
    /** "1"/"0" from IPowerManager.isInteractive(); "?" when unavailable (never clears). */
    static String readInteractive() {
        try {
            Object binder = Class.forName("android.os.ServiceManager")
                    .getMethod("getService", String.class).invoke(null, "power");
            if (binder == null) return "?";
            Object power = Class.forName("android.os.IPowerManager$Stub")
                    .getMethod("asInterface", Class.forName("android.os.IBinder")).invoke(null, binder);
            if (power == null) return "?";
            Object value = Class.forName("android.os.IPowerManager")
                    .getMethod("isInteractive").invoke(power);
            return Boolean.TRUE.equals(value) ? "1" : Boolean.FALSE.equals(value) ? "0" : "?";
        } catch (Throwable error) {
            return "?";
        }
    }
    private static void scheduleRepairLocked() {
        generations.scheduleRepair();
        WakeRepairScheduler.scheduleFromWake();
        WatcherCadence.onWakeRepairScheduled();
    }
    private static void cancelRepairLocked() {
        generations.completeRepair();
        WakeRepairScheduler.cancel();
    }
    private static boolean attempt(boolean on, String reason, String mode) {
        String key = reason + ':' + mode + ':' + (on ? '1' : '0');
        long now = SystemClock.elapsedRealtime();
        if (key.equals(lastAttemptKey) && now - lastAttemptAt < 1500L) return false;
        lastAttemptKey = key;
        lastAttemptAt = now;
        return applyConfirmed(on, reason, mode);
    }
    private static boolean applyConfirmed(boolean on, String reason, String expectedMode) {
        if (!expectedMode.equals(readCurrentMode())) {
            status = "MODE_CHANGED";
            return false;
        }
        String expected = on ? "1" : "0";
        String[] beforePair = Telemetry.crtcActivePair();
        String top = beforePair[0];
        String before = beforePair[1];
        if (!DisplayDecisionModel.readyForReconcile(expectedMode, top, before)
                || (on && "0".equals(top) && "0".equals(before))) {
            status = "DISPLAY_ASLEEP_OR_TRANSITION";
            return false;
        }
        if (!("0".equals(before) || "1".equals(before))) {
            status = "HARDWARE_UNKNOWN";
            return false;
        }
        long hardwareStartedAt = SystemClock.elapsedRealtime();
        if (!expected.equals(before) && !DisplayHardware.apply(on, reason)) {
            // A stalled call that ends in an error is the same compositor evidence.
            noteHardwareCallLocked(reason, SystemClock.elapsedRealtime() - hardwareStartedAt);
            status = "APPLY_FAILED";
            return false;
        }
        if (!expected.equals(before))
            noteHardwareCallLocked(reason, SystemClock.elapsedRealtime() - hardwareStartedAt);
        long deadline = SystemClock.elapsedRealtime() + 1200L;
        while (!expected.equals(Telemetry.bottomCrtcActive())
                && SystemClock.elapsedRealtime() < deadline) {
            try { Thread.sleep(80L); } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                status = "INTERRUPTED";
                return false;
            }
        }
        String[] finalPair = Telemetry.crtcActivePair();
        String finalBottom = finalPair[1];
        boolean confirmed = expectedMode.equals(readCurrentMode())
                && expected.equals(finalBottom)
                && DisplayDecisionModel.topCompatible(expectedMode, finalPair[0]);
        if (!confirmed) {
            // Observation only: an issued action whose readback did not confirm.
            Log.d(TAG, "APPLY_UNCONFIRMED reason=" + reason + ";target=" + expected
                    + ";top=" + finalPair[0] + ";bottom=" + finalBottom);
            DisplayHardware.alignPropertyWithCrtc(finalBottom);
        }
        status = confirmed ? "CONFIRMED" : "UNCONFIRMED";
        return confirmed;
    }
    private static String readCurrentMode() {
        return DisplayModeReader.current();
    }
}
