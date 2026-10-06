package com.thor.displaypowertest;

import android.os.SystemClock;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * AYN Dashboard CPU Fix: the vendor system-load-check property, the one-shot
 * compositor restart, its timed wake lock and the helper that relaunches the
 * daemon after Android's framework restarts. Formerly static code in D.
 *
 * Every state change that the boot coordinator also observes is guarded by
 * {@link #lock}, the former D.class monitor.
 */
public final class CpuFixController {
    private static final String BOOT_ANIMATION_DISABLE_PROPERTY = "debug.sf.nobootanimation";
    /** Runs the post-restart gate in this daemon; see BootCoordinator. */
    public interface HandoffRecovery {
        void recoverInPlace(String reason);
    }

    private final Object lock = new Object();
    private final SystemProbe probe;
    private final BootTrace trace;
    private final TransitionWakeLock wakeLock;
    private final BootSession session;
    private final HandoffRecovery recovery;

    private volatile boolean restartScheduled;
    private volatile boolean restartFailed;
    private volatile boolean desired;
    /** NONE, SCHEDULED, RECOVERING, RECOVERED, RECOVERY_FAILED, ABORTED or FAILED. */
    private volatile String handoffState = "NONE";
    /** True from helper start until its exit is observed; one helper at a time. */
    private volatile boolean helperAlive;

    public CpuFixController(SystemProbe probe, BootTrace trace, TransitionWakeLock wakeLock,
            BootSession session, HandoffRecovery recovery) {
        this.probe = probe;
        this.trace = trace;
        this.wakeLock = wakeLock;
        this.session = session;
        this.recovery = recovery;
        this.desired = session.cpuFixDesired();
    }

    public boolean desired() { return desired; }
    public String handoffState() { return handoffState; }
    public void setHandoffState(String state) { handoffState = state; }

    /**
     * True only when a boot-scoped APPLIED marker proves that the current
     * composer is the successor that consumed the desired vendor property.
     * BootCoordinator uses this to recover post-restart display-gate semantics
     * even if a later daemon relaunch loses the explicit "post" argv token.
     */
    public boolean currentComposerHasAppliedAttempt() {
        synchronized (lock) {
            CpuBootAttemptStore.ReadResult stored = CpuBootAttemptStore.read();
            if (stored.state != CpuBootAttemptStore.State.VALID
                    || stored.attempt.phase != CpuBootAttemptModel.Phase.APPLIED) {
                return false;
            }
            CpuBootAttemptModel.Attempt attempt = stored.attempt;
            String bootId = SecureChannel.currentBootId();
            String property = Telemetry.systemLoadFixObservation();
            String composerPid = probe.composerPid();
            String desiredValue = desired ? "1" : "0";
            return CpuBootAttemptModel.decide(attempt, bootId, desiredValue,
                    property, composerPid, SystemClock.elapsedRealtime())
                    == CpuBootAttemptModel.Action.PROCEED;
        }
    }

    /**
     * Releases the transition wake lock unless a compositor restart now owns
     * it. Returns true when the release was sent.
     */
    public boolean releaseWakeLockUnlessRestartScheduled() {
        synchronized (lock) {
            if (restartScheduled) return false;
            wakeLock.release();
            return true;
        }
    }

    public String phase() {
        if (restartFailed) return "ERROR";
        if (restartScheduled || helperAlive || BootSafety.isHeld()) return "PENDING";

        CpuBootAttemptStore.ReadResult stored = CpuBootAttemptStore.read();
        if (stored.state == CpuBootAttemptStore.State.CORRUPT) return "ERROR";
        if (stored.state == CpuBootAttemptStore.State.VALID) {
            CpuBootAttemptModel.Attempt attempt = stored.attempt;
            String bootId = SecureChannel.currentBootId();
            if (CpuBootAttemptModel.validBootId(bootId)
                    && bootId.equalsIgnoreCase(attempt.bootId)) {
                switch (attempt.phase) {
                    case PREPARED:
                    case PROPERTY_VERIFIED:
                    case RESTART_REQUESTED:
                        return "PENDING";
                    case FAILED:
                        return "ERROR";
                    case APPLIED:
                        String actual = Telemetry.systemLoadFixObservation();
                        String composerPid = probe.composerPid();
                        if (attempt.desired.equals(actual)
                                && CpuBootAttemptModel.pid(composerPid)
                                && !attempt.baselineComposerPid.equals(composerPid)) {
                            return (desired ? "1" : "0").equals(actual)
                                    ? "CONFIRMED" : "MISMATCH";
                        }
                        return "ERROR";
                    default:
                        return "ERROR";
                }
            }
        }

        String actual = Telemetry.systemLoadFixState();
        if (!"0".equals(actual) && !"1".equals(actual)) return "UNKNOWN";
        if (!probe.composerRunning()) return "PENDING";
        if (!"RUNNING".equals(WatcherSupervisor.health())) return "PENDING";
        return (desired ? "1" : "0").equals(actual) ? "CONFIRMED" : "MISMATCH";
    }

    /** IPC 'R' and 'L', and the boot coordinator's single CPU action. */
    public String apply(boolean enabled) {
        synchronized (lock) {
            return applyLocked(enabled);
        }
    }

    /**
     * A normal (non-boot-hold) daemon can be the successor of a daemon that
     * died in the property/restart window. Resume only when a durable marker
     * exists; an ordinary daemon start with no marker remains side-effect free.
     */
    public void resumePersistedAttempt() {
        CpuBootAttemptStore.ReadResult initial = CpuBootAttemptStore.read();
        if (initial.state == CpuBootAttemptStore.State.ABSENT) return;
        Thread resume = new Thread(() -> {
            for (int poll = 0; poll < 130; poll++) {
                String result = apply(desired);
                if (result.startsWith("ok=1")) {
                    // A predecessor may have died while holding the named
                    // transition wake lock. A newly scheduled restart still
                    // owns it; any other terminal success may release it.
                    releaseWakeLockUnlessRestartScheduled();
                    return;
                }
                if (!retryableAttemptResult(result)) {
                    // Terminal recovery failure also releases a predecessor's
                    // named lock when no restart is currently scheduled.
                    releaseWakeLockUnlessRestartScheduled();
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
        }, "cpu-fix-attempt-resume");
        resume.setDaemon(true);
        resume.start();
    }

    private String applyLocked(boolean enabled) {
        final String desiredValue = enabled ? "1" : "0";
        if (restartScheduled || helperAlive) {
            return "ok=0;error=COMPOSER_RESTART_BUSY";
        }
        if (!probe.composerRunning()) {
            return "ok=0;error=COMPOSER_NOT_READY";
        }
        if (!"RUNNING".equals(WatcherSupervisor.health())) {
            return "ok=0;error=WATCHER_NOT_READY";
        }

        final String bootId = SecureChannel.currentBootId();
        if (!CpuBootAttemptModel.validBootId(bootId)) {
            return "ok=0;error=BOOT_ID_UNKNOWN";
        }

        boolean wakeHeld = false;
        for (int step = 0; step < 10; step++) {
            final String actual = Telemetry.systemLoadFixObservation();
            boolean unsetEnable = enabled && PropertyState.UNSET.equals(actual);
            if (!unsetEnable && !"0".equals(actual) && !"1".equals(actual)) {
                if (wakeHeld) wakeLock.release();
                return "ok=0;error=CPU_FIX_STATE_UNKNOWN";
            }

            final String composerPid = probe.composerPid();
            if (!CpuBootAttemptModel.pid(composerPid)) {
                if (wakeHeld) wakeLock.release();
                return "ok=0;error=COMPOSER_PID_UNKNOWN";
            }

            CpuBootAttemptStore.ReadResult stored = CpuBootAttemptStore.read();
            if (stored.state == CpuBootAttemptStore.State.CORRUPT) {
                if (wakeHeld) wakeLock.release();
                restartFailed = true;
                return "ok=0;error=CPU_ATTEMPT_MARKER_CORRUPT";
            }
            CpuBootAttemptModel.Attempt attempt =
                    stored.state == CpuBootAttemptStore.State.VALID ? stored.attempt : null;
            if (restartFailed && attempt == null) {
                if (wakeHeld) wakeLock.release();
                return "ok=0;error=COMPOSER_RESTART_FAILED";
            }

            CpuBootAttemptModel.Action action = CpuBootAttemptModel.decide(
                    attempt, bootId, desiredValue, actual, composerPid,
                    SystemClock.elapsedRealtime());
            switch (action) {
                case DELETE_STALE:
                    try {
                        CpuBootAttemptStore.deleteTrusted();
                        trace.mark("CPU_ATTEMPT_STALE_DELETED", null);
                    } catch (Throwable error) {
                        if (wakeHeld) wakeLock.release();
                        restartFailed = true;
                        Log.e("ThorDisplayDaemon", "cannot delete stale CPU attempt", error);
                        return "ok=0;error=CPU_ATTEMPT_STALE_DELETE_FAILED";
                    }
                    continue;

                case START_NEW:
                    CpuBootAttemptModel.Attempt started = CpuBootAttemptModel.start(
                            bootId, desiredValue, actual, composerPid,
                            SystemClock.elapsedRealtime());
                    if (!persistAttempt(started, "CPU_ATTEMPT_PREPARED", null)) {
                        if (wakeHeld) wakeLock.release();
                        return "ok=0;error=CPU_ATTEMPT_STORE_WRITE_FAILED";
                    }
                    continue;

                case WRITE_PROPERTY:
                    if (!wakeHeld) {
                        if (!wakeLock.acquire()) {
                            failPersistedAttempt(attempt.baselineComposerPid,
                                    "wake_lock_failed");
                            return "ok=0;error=WAKE_LOCK_FAILED";
                        }
                        wakeHeld = true;
                    }
                    String propertyResult = setSystemLoadCheckValue(desiredValue);
                    if (!propertyResult.startsWith("ok=1")) {
                        failPersistedAttempt(attempt.baselineComposerPid,
                                "setprop_failed");
                        wakeLock.release();
                        return propertyResult;
                    }
                    if (session.active()) {
                        trace.mark("CPU_PROP_WRITTEN", "value=" + desiredValue);
                    }
                    if (!desiredValue.equals(Telemetry.systemLoadFixObservation())) {
                        if (attempt.baselineComposerPid.equals(probe.composerPid())) {
                            restoreSystemLoadCheckState(attempt.previous);
                        }
                        failPersistedAttempt(attempt.baselineComposerPid,
                                "setprop_unconfirmed");
                        wakeLock.release();
                        return "ok=0;error=SETPROP_UNCONFIRMED";
                    }
                    CpuBootAttemptModel.Attempt verified = attempt.withPhase(
                            CpuBootAttemptModel.Phase.PROPERTY_VERIFIED,
                            SystemClock.elapsedRealtime());
                    if (!persistAttempt(verified, "CPU_ATTEMPT_PROPERTY_VERIFIED",
                            "recovered=0")) {
                        if (attempt.baselineComposerPid.equals(probe.composerPid())) {
                            restoreSystemLoadCheckState(attempt.previous);
                        }
                        wakeLock.release();
                        return "ok=0;error=CPU_ATTEMPT_STORE_WRITE_FAILED";
                    }
                    if (session.active()) {
                        trace.mark("CPU_PROP_VERIFIED", "value=" + desiredValue);
                    }
                    continue;

                case CONFIRM_PROPERTY:
                    CpuBootAttemptModel.Attempt recovered = attempt.withPhase(
                            CpuBootAttemptModel.Phase.PROPERTY_VERIFIED,
                            SystemClock.elapsedRealtime());
                    if (!persistAttempt(recovered, "CPU_ATTEMPT_PROPERTY_VERIFIED",
                            "recovered=1")) {
                        if (wakeHeld) wakeLock.release();
                        return "ok=0;error=CPU_ATTEMPT_STORE_WRITE_FAILED";
                    }
                    continue;

                case REQUEST_RESTART:
                    if (!wakeHeld) {
                        if (!wakeLock.acquire()) {
                            failPersistedAttempt(attempt.baselineComposerPid,
                                    "wake_lock_failed_before_restart");
                            if (attempt.baselineComposerPid.equals(probe.composerPid())) {
                                restoreSystemLoadCheckState(attempt.previous);
                            }
                            return "ok=0;error=WAKE_LOCK_FAILED";
                        }
                        wakeHeld = true;
                    }
                    CpuBootAttemptModel.Attempt requested = attempt.withPhase(
                            CpuBootAttemptModel.Phase.RESTART_REQUESTED,
                            SystemClock.elapsedRealtime());
                    // Durability boundary: this write MUST complete before any
                    // helper/thread can reach ctl.restart.
                    if (!persistAttempt(requested, "CPU_ATTEMPT_RESTART_REQUESTED",
                            null)) {
                        if (attempt.baselineComposerPid.equals(probe.composerPid())) {
                            restoreSystemLoadCheckState(attempt.previous);
                        }
                        wakeLock.release();
                        return "ok=0;error=CPU_ATTEMPT_STORE_WRITE_FAILED";
                    }
                    desired = enabled;
                    restartFailed = false;
                    restartScheduled = true;
                    final boolean bootTrace = session.active();
                    Thread restartThread = new Thread(
                            () -> runRestart(bootTrace, attempt.baselineComposerPid,
                                    attempt.previous, desiredValue),
                            "composer-restart-once");
                    try {
                        restartThread.start();
                    } catch (Throwable error) {
                        restartScheduled = false;
                        restartFailed = true;
                        failPersistedAttempt(attempt.baselineComposerPid,
                                "restart_thread_start_failed");
                        if (attempt.baselineComposerPid.equals(probe.composerPid())) {
                            restoreSystemLoadCheckState(attempt.previous);
                        }
                        wakeLock.release();
                        wakeHeld = false;
                        Log.e("ThorDisplayDaemon", "cannot start composer restart thread", error);
                        return "ok=0;error=COMPOSER_RESTART_THREAD_FAILED";
                    }
                    wakeHeld = false;
                    return "ok=1;system_load_fix=" + desiredValue
                            + ";composer_restart=scheduled_once";

                case WAIT_FOR_RESTART:
                    desired = enabled;
                    if (wakeHeld) wakeLock.release();
                    return "ok=0;error=COMPOSER_RESTART_PENDING";

                case MARK_APPLIED:
                    CpuBootAttemptModel.Attempt applied = attempt.withPhase(
                            CpuBootAttemptModel.Phase.APPLIED,
                            SystemClock.elapsedRealtime());
                    if (!persistAttempt(applied, "CPU_ATTEMPT_APPLIED",
                            "composer_pid=" + composerPid)) {
                        if (wakeHeld) wakeLock.release();
                        return "ok=0;error=CPU_ATTEMPT_STORE_WRITE_FAILED";
                    }
                    desired = enabled;
                    restartFailed = false;
                    if (wakeHeld) wakeLock.release();
                    return "ok=1;system_load_fix=" + desiredValue
                            + ";composer_restart=applied";

                case PROCEED:
                    desired = enabled;
                    if (restartFailed) {
                        if (wakeHeld) wakeLock.release();
                        return "ok=0;error=COMPOSER_RESTART_FAILED";
                    }
                    if (wakeHeld) wakeLock.release();
                    return "ok=1;system_load_fix=" + desiredValue
                            + ";composer_restart=not_needed";

                case FAIL_SAFE:
                default:
                    if (attempt != null && bootId.equalsIgnoreCase(attempt.bootId)
                            && attempt.phase != CpuBootAttemptModel.Phase.FAILED) {
                        // If RESTART_REQUESTED timed out while the original
                        // composer is still alive, the restart provably did
                        // not take effect. Restore the global property to the
                        // state that compositor actually cached before sealing
                        // the attempt as FAILED. This preserves the no-retry
                        // invariant without leaving getprop/effective state
                        // deliberately split for the rest of the boot.
                        if (attempt.phase == CpuBootAttemptModel.Phase.RESTART_REQUESTED
                                && attempt.baselineComposerPid.equals(composerPid)) {
                            restoreSystemLoadCheckState(attempt.previous);
                        }
                        failPersistedAttempt(attempt.baselineComposerPid,
                                "model_fail_safe");
                    }
                    if (attempt != null) restartFailed = true;
                    if (wakeHeld) wakeLock.release();
                    return "ok=0;error=CPU_RESTART_ATTEMPT_FAILED";
            }
        }
        if (wakeHeld) wakeLock.release();
        restartFailed = true;
        return "ok=0;error=CPU_RESTART_STATE_LOOP";
    }

    private void runRestart(boolean bootTrace, String beforeComposerPid, String previousCpu,
            String desiredValue) {
        Process helper = null;
        String previousBootAnimation = null;
        String previousNativeBootAnimation = null;
        try {
            Thread.sleep(300L);
            previousNativeBootAnimation = armNativeRecoveryBootAnimation(bootTrace);
            if (previousNativeBootAnimation == null) {
                // Existing validated fallback: suppress the replacement
                // SurfaceFlinger boot animation when the native prototype is
                // absent or cannot be armed safely.
                previousBootAnimation = armBootAnimationSuppression(bootTrace);
            }
            helper = scheduleTransitionCleanupAndDaemonRestart(
                    beforeComposerPid, previousBootAnimation,
                    previousNativeBootAnimation);
            handoffState = "SCHEDULED";
            observeHelper(helper, bootTrace, beforeComposerPid, previousCpu, desiredValue);
            if (bootTrace) trace.mark("HELPER_SCHEDULED", "old_composer=" + beforeComposerPid);
            if (bootTrace) trace.mark("CTL_RESTART_SENT", null);
            int exit = new ProcessBuilder("setprop", "ctl.restart",
                    SystemProbe.COMPOSER_SERVICE).start().waitFor();
            if (bootTrace) trace.mark("CTL_RESTART_ACK", "exit=" + exit);
            if (exit != 0) throw new IllegalStateException("composer restart exit=" + exit);
            Thread.sleep(3000L);
        } catch (Throwable error) {
            if (helper == null) {
                if (previousNativeBootAnimation != null) {
                    restoreNativeRecoveryBootAnimation(
                            previousNativeBootAnimation, bootTrace);
                }
                if (previousBootAnimation != null) {
                    restoreBootAnimationSuppression(previousBootAnimation, bootTrace);
                }
            }
            boolean applied = markAppliedIfComposerReplaced(beforeComposerPid, desiredValue);
            restartFailed = !applied;
            if (!applied) {
                failPersistedAttempt(beforeComposerPid, "restart_failed");
                if (beforeComposerPid.equals(probe.composerPid())) {
                    restoreSystemLoadCheckState(previousCpu);
                }
            }
            Log.e("ThorDisplayDaemon", "composer restart failed", error);
            wakeLock.release();
        } finally {
            synchronized (lock) { restartScheduled = false; }
        }
        if (helper == null && bootTrace && BootSafety.isHeld()) {
            recovery.recoverInPlace("HELPER_START_FAILED");
        }
    }

    /**
     * The helper normally stops this daemon before it exits, so its exit is only
     * observed when the handover did not happen. Without this observer a boot
     * daemon stayed in APPLYING CPU FIX with display actions held until reboot.
     */
    private void observeHelper(final Process helper, final boolean bootHandoff,
            final String beforeComposerPid, final String previousCpu,
            final String desiredValue) {
        helperAlive = true;
        Thread observer = new Thread(() -> {
            int exit;
            try {
                // Untimed by design: the helper's own loops are bounded and this
                // thread only waits; it never holds a lock while waiting.
                exit = helper.waitFor();
            } catch (InterruptedException ignored) {
                return;
            } finally {
                helperAlive = false;
            }
            onHelperExit(exit, bootHandoff, beforeComposerPid, previousCpu, desiredValue);
        }, "thor-helper-observer");
        observer.setDaemon(true);
        observer.start();
    }

    private void onHelperExit(int exit, boolean bootHandoff, String beforeComposerPid,
            String previousCpu, String desiredValue) {
        HandoffRecoveryModel.Action action = HandoffRecoveryModel.afterHelperExit(
                exit, bootHandoff, BootSafety.isHeld(),
                "RUNNING".equals(WatcherSupervisor.health()));
        trace.mark("HELPER_EXIT_OBSERVED", "exit=" + exit
                + ";reason=" + HandoffRecoveryModel.reason(exit)
                + ";action=" + action.name());
        if (action == HandoffRecoveryModel.Action.NONE) return;
        if (HandoffRecoveryModel.composerNotRestarted(exit)) {
            // Same rule as a failed ctl.restart: report ERROR and restore
            // the previous property only while the old compositor still runs.
            restartFailed = true;
            failPersistedAttempt(beforeComposerPid, "helper_composer_not_restarted");
            if (beforeComposerPid.equals(probe.composerPid())) {
                restoreSystemLoadCheckState(previousCpu);
            }
        } else if (markAppliedIfComposerReplaced(beforeComposerPid, desiredValue)) {
            restartFailed = false;
        }
        if (action == HandoffRecoveryModel.Action.HOLD_FOR_REPLACEMENT) {
            // Hold first, before any wait, so no display action uses a
            // mode or callback from a framework that may have restarted.
            markHandoffFailed("exit=" + exit);
        }
        waitForRestartBookkeeping();
        if (action != HandoffRecoveryModel.Action.RECOVER_IN_PLACE) {
            if (action == HandoffRecoveryModel.Action.RELEASE_WAKE_LOCK) {
                handoffState = "ABORTED";
                DaemonState.setLastAction("HANDOFF_ABORTED");
            }
            releaseWakeLockUnlessRestartScheduled();
            trace.mark("WAKE_UNLOCK_SENT", "after_helper_exit=1");
            return;
        }
        recovery.recoverInPlace(HandoffRecoveryModel.reason(exit));
    }

    /** Holds display actions until AutoService replaces this daemon. */
    public void markHandoffFailed(String detail) {
        BootSafety.handoffFailed();
        handoffState = "FAILED";
        DaemonState.setLastAction("HANDOFF_FAILED");
        trace.mark("HANDOFF_FAILED", detail);
    }

    /** The restart thread clears its busy flag about 3.3 s after starting the helper. */
    private void waitForRestartBookkeeping() {
        for (int poll = 0; poll < 100 && restartScheduled; poll++) {
            try { Thread.sleep(100L); } catch (InterruptedException ignored) { return; }
        }
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

    private boolean persistAttempt(CpuBootAttemptModel.Attempt attempt,
            String action, String detail) {
        try {
            CpuBootAttemptStore.write(attempt);
            String suffix = "phase=" + attempt.phase.name()
                    + ";baseline_pid=" + attempt.baselineComposerPid;
            if (detail != null && !detail.isEmpty()) suffix += ";" + detail;
            trace.mark(action, suffix);
            return true;
        } catch (Throwable error) {
            Log.e("ThorDisplayDaemon", "cannot persist CPU boot attempt "
                    + attempt.phase.name(), error);
            return false;
        }
    }

    private void failPersistedAttempt(String baselineComposerPid, String detail) {
        try {
            CpuBootAttemptStore.ReadResult stored = CpuBootAttemptStore.read();
            if (stored.state != CpuBootAttemptStore.State.VALID) return;
            CpuBootAttemptModel.Attempt attempt = stored.attempt;
            String bootId = SecureChannel.currentBootId();
            if (!CpuBootAttemptModel.validBootId(bootId)
                    || !bootId.equalsIgnoreCase(attempt.bootId)
                    || !baselineComposerPid.equals(attempt.baselineComposerPid)
                    || attempt.phase == CpuBootAttemptModel.Phase.APPLIED
                    || attempt.phase == CpuBootAttemptModel.Phase.FAILED) {
                return;
            }
            persistAttempt(attempt.withPhase(CpuBootAttemptModel.Phase.FAILED,
                    SystemClock.elapsedRealtime()), "CPU_ATTEMPT_FAILED", detail);
        } catch (Throwable error) {
            Log.e("ThorDisplayDaemon", "cannot fail CPU boot attempt", error);
        }
    }

    private boolean markAppliedIfComposerReplaced(String baselineComposerPid,
            String desiredValue) {
        try {
            CpuBootAttemptStore.ReadResult stored = CpuBootAttemptStore.read();
            if (stored.state != CpuBootAttemptStore.State.VALID) return false;
            CpuBootAttemptModel.Attempt attempt = stored.attempt;
            if (!baselineComposerPid.equals(attempt.baselineComposerPid)) return false;
            String bootId = SecureChannel.currentBootId();
            String property = Telemetry.systemLoadFixObservation();
            String composerPid = probe.composerPid();
            CpuBootAttemptModel.Action action = CpuBootAttemptModel.decide(
                    attempt, bootId, desiredValue, property, composerPid,
                    SystemClock.elapsedRealtime());
            if (action == CpuBootAttemptModel.Action.PROCEED
                    && attempt.phase == CpuBootAttemptModel.Phase.APPLIED) {
                return true;
            }
            if (action != CpuBootAttemptModel.Action.MARK_APPLIED) return false;
            CpuBootAttemptModel.Attempt applied = attempt.withPhase(
                    CpuBootAttemptModel.Phase.APPLIED, SystemClock.elapsedRealtime());
            return persistAttempt(applied, "CPU_ATTEMPT_APPLIED",
                    "composer_pid=" + composerPid);
        } catch (Throwable error) {
            Log.e("ThorDisplayDaemon", "cannot confirm CPU boot attempt", error);
            return false;
        }
    }

    /**
     * Arms Android's bootanimation opt-out only for a boot-scoped compositor
     * restart. Returns the exact previous value when armed; null means fall
     * back to the visible second animation.
     */
    private String armNativeRecoveryBootAnimation(boolean bootTrace) {
        if (!bootTrace || !RecoveryBootAnimationAsset.prototypeArmed()) return null;

        String displays = readProperty(RecoveryBootAnimationAsset.DISPLAYS_PROPERTY);
        if (displays == null || !displays.isEmpty()) {
            trace.mark("NATIVE_BOOTANIM_FAIL_OPEN",
                    "reason=MULTI_DISPLAY_PROPERTY_NOT_EMPTY");
            return null;
        }

        String disabled = readProperty(BOOT_ANIMATION_DISABLE_PROPERTY);
        if (disabled == null || "1".equals(disabled)) {
            trace.mark("NATIVE_BOOTANIM_FAIL_OPEN", "reason=BOOTANIM_DISABLED");
            return null;
        }

        String previous = readProperty(RecoveryBootAnimationAsset.CUSTOM_PROPERTY);
        if (previous == null
                || !previous.matches("[A-Za-z0-9_./:@+\\-]{0,90}")) {
            trace.mark("NATIVE_BOOTANIM_FAIL_OPEN",
                    "reason=PREVIOUS_CUSTOM_PATH_UNSAFE");
            return null;
        }

        if (!RecoveryBootAnimationAsset.prepare()) {
            trace.mark("NATIVE_BOOTANIM_FAIL_OPEN", "reason=ASSET_PREPARE_FAILED");
            return null;
        }

        if (!writeProperty(RecoveryBootAnimationAsset.CUSTOM_PROPERTY,
                    RecoveryBootAnimationAsset.OUTPUT)
                || !RecoveryBootAnimationAsset.OUTPUT.equals(
                        readProperty(RecoveryBootAnimationAsset.CUSTOM_PROPERTY))) {
            writeProperty(RecoveryBootAnimationAsset.CUSTOM_PROPERTY, previous);
            RecoveryBootAnimationAsset.cleanup();
            trace.mark("NATIVE_BOOTANIM_FAIL_OPEN",
                    "reason=SET_OR_READBACK_FAILED");
            return null;
        }

        trace.mark("NATIVE_BOOTANIM_ARMED",
                "previous=" + (previous.isEmpty() ? "UNSET" : previous)
                        + ";path=" + RecoveryBootAnimationAsset.OUTPUT
                        + ";display_route=INTERNAL_DEFAULT");
        return previous;
    }

    private void restoreNativeRecoveryBootAnimation(String previous, boolean bootTrace) {
        if (previous == null
                || !previous.matches("[A-Za-z0-9_./:@+\\-]{0,90}")) {
            RecoveryBootAnimationAsset.cleanup();
            return;
        }
        boolean propertyOk = writeProperty(
                RecoveryBootAnimationAsset.CUSTOM_PROPERTY, previous);
        RecoveryBootAnimationAsset.cleanup();
        if (bootTrace) {
            trace.mark(propertyOk ? "NATIVE_BOOTANIM_RESTORED"
                            : "NATIVE_BOOTANIM_RESTORE_FAILED",
                    "value=" + (previous.isEmpty() ? "UNSET" : previous));
        }
    }

    private String armBootAnimationSuppression(boolean bootTrace) {
        if (!bootTrace) return null;
        String previous = readProperty(BOOT_ANIMATION_DISABLE_PROPERTY);
        if (previous == null || !previous.matches("[0-9]{0,3}")) {
            trace.mark("BOOTANIM_SUPPRESS_SKIPPED", "reason=PREVIOUS_VALUE_UNSAFE");
            return null;
        }
        if (!writeProperty(BOOT_ANIMATION_DISABLE_PROPERTY, "1")
                || !"1".equals(readProperty(BOOT_ANIMATION_DISABLE_PROPERTY))) {
            restoreBootAnimationSuppression(previous, true);
            trace.mark("BOOTANIM_SUPPRESS_SKIPPED", "reason=SET_OR_READBACK_FAILED");
            return null;
        }
        trace.mark("BOOTANIM_SUPPRESS_ARMED",
                "previous=" + (previous.isEmpty() ? "UNSET" : previous));
        return previous;
    }

    private void restoreBootAnimationSuppression(String previous, boolean bootTrace) {
        if (previous == null || !previous.matches("[0-9]{0,3}")) return;
        boolean ok = writeProperty(BOOT_ANIMATION_DISABLE_PROPERTY, previous);
        if (bootTrace) {
            trace.mark(ok ? "BOOTANIM_SUPPRESS_RESTORED"
                            : "BOOTANIM_SUPPRESS_RESTORE_FAILED",
                    "value=" + (previous.isEmpty() ? "UNSET" : previous));
        }
    }

    private String readProperty(String name) {
        Process process = null;
        try {
            process = new ProcessBuilder("getprop", name).start();
            if (!ProcessWait.exited(process, 2000L) || process.exitValue() != 0) return null;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                return line == null ? "" : line.trim();
            }
        } catch (Throwable ignored) {
            return null;
        } finally {
            if (process != null) process.destroy();
        }
    }

    private boolean writeProperty(String name, String value) {
        Process process = null;
        try {
            process = new ProcessBuilder("setprop", name, value).start();
            return ProcessWait.exited(process, 2000L) && process.exitValue() == 0;
        } catch (Throwable ignored) {
            return false;
        } finally {
            if (process != null) process.destroy();
        }
    }

    private String setSystemLoadCheckValue(String value) {
        Process process = null;
        try {
            process = new ProcessBuilder("setprop",
                    "vendor.display.disable_system_load_check", value).start();
            if (!ProcessWait.exited(process, 2000L)) return "ok=0;error=SETPROP_TIMEOUT";
            int exit = process.exitValue();
            if (exit != 0) return "ok=0;error=SETPROP_EXIT_" + exit;
            return "ok=1;system_load_fix=" + value;
        } catch (Throwable error) {
            return "ok=0;error=SETPROP_FAILED";
        } finally { if (process != null) process.destroy(); }
    }

    private void restoreSystemLoadCheckState(String previous) {
        // An empty value restores the unconfigured state after a failed
        // pre-restart write. Never invent a prior binary value for UNSET.
        String value = PropertyState.UNSET.equals(previous) ? "" : previous;
        String result = setSystemLoadCheckValue(value);
        if (!result.startsWith("ok=1")) {
            Log.e("ThorDisplayDaemon", "could not restore CPU property: " + result);
        }
    }

    private Process scheduleTransitionCleanupAndDaemonRestart(String beforeComposerPid,
            String previousBootAnimation, String previousNativeBootAnimation)
            throws Exception {
        String enabled = DaemonState.isEnabled() ? "1" : "0";
        String desiredCpu = desired ? "1" : "0";
        // During BOOT HOLD the Hall watcher has not been enabled yet. Preserve
        // its saved preference rather than sampling the inactive watcher.
        String desiredLidGuard = (session.active() ? session.lidGuardDesired()
                : LidGuard.isEnabled()) ? "1" : "0";
        boolean suppressBootAnimation = previousBootAnimation != null;
        String bootAnimationPrevious = suppressBootAnimation ? previousBootAnimation : "";
        boolean nativeBootAnimation = previousNativeBootAnimation != null;
        String nativeBootAnimationPrevious =
                nativeBootAnimation ? previousNativeBootAnimation : "";
        // A compositor restart starts a second readiness phase. Do not reuse
        // the initial boot's 60-second deadline for its five-second grace.
        long phaseStartedAt = SystemClock.elapsedRealtime();
        int daemonPid = android.os.Process.myPid();
        // The vendor composer restarts SurfaceFlinger and zygote. Keep an
        // eight-second minimum, then relaunch as soon as the Android services
        // and package manager recover. The post-restart daemon releases the
        // timed wake lock only after reconciliation or safe timeout.
        // Timing marks use /proc/uptime (boot clock, 10 ms resolution) in
        // centiseconds: mksh arithmetic is 32-bit, so milliseconds are only
        // formatted, never computed.
        String command = String.join("\n",
                RootLogFiles.SHELL_GUARD,
                "L=" + RootLogFiles.DAEMON_LOG,
                "if safe_log \"$L\"; then exec </dev/null >>\"$L\" 2>&1;"
                        + " else exec </dev/null >/dev/null 2>&1; fi",
                "TR=" + BootTrace.PATH,
                "BID=$(cat /proc/sys/kernel/random/boot_id 2>/dev/null)",
                // Prototype only; never created by the app and never affects
                // CPU/display recovery when absent.
                "RSPF=/data/local/tmp/thor-recovery-splash-prototype",
                "RSP=0; [ -f \"$RSPF\" ] && [ ! -L \"$RSPF\" ]"
                        + " && [ \"$(cat \"$RSPF\" 2>/dev/null)\" = 1 ] && RSP=1",
                // Preserve the already-running daemon APK path through the
                // framework gap instead of depending on package manager.
                "SCP=$CLASSPATH",
                "case \"$SCP\" in /data/app/*/base.apk) ;; *) SCP='';; esac",
                "BAS=" + (suppressBootAnimation ? "1" : "0"),
                "BAPREV='" + bootAnimationPrevious + "'",
                "NBA=" + (nativeBootAnimation ? "1" : "0"),
                "NBPREV='" + nativeBootAnimationPrevious + "'",
                "NBPATH='" + RecoveryBootAnimationAsset.OUTPUT + "'",
                "[ \"$NBA\" = 1 ] && RSP=0",
                "cs(){ read U X </proc/uptime; S=${U%.*}; F=${U#*.}; F=${F#0};"
                        + " echo $((S*100+${F:-0})); }",
                // A trace line is skipped, never redirected, if the path is unsafe.
                "T(){ safe_log \"$TR\" || return 0;"
                        + " echo \"elapsed_ms=$(cs)0;action=$1;${2:+$2;}pid=$$;"
                        + "boot_id=${BID:-?};source=helper\" >>\"$TR\"; }",
                // Splash-only successor resolver. The existing provenance
                // path is untouched; ambiguous observations simply do not arm
                // rendering.
                "succ(){ B=$1; shift; case \"$B\" in ''|0|*[!0-9]*) return;; esac;"
                        + " C=''; for X in \"$@\"; do"
                        + " case \"$X\" in ''|0|*[!0-9]*) return;; esac;"
                        + " [ \"$X\" = \"$B\" ] && continue;"
                        + " [ -n \"$C\" ] && [ \"$X\" = \"$C\" ] && continue;"
                        + " [ -n \"$C\" ] && return; C=$X; done;"
                        + " [ -n \"$C\" ] && echo \"$C\"; }",
                "start_splash(){ [ \"$RSP\" = 1 ] || return 0; RSP=0;"
                        + " [ -n \"$SCP\" ] || { T SPLASH_FAIL_OPEN"
                        + " reason=CLASSPATH_UNAVAILABLE; return 0; };"
                        + " CLASSPATH=\"$SCP\" app_process /"
                        + " com.thor.displaypowertest.RecoverySplash"
                        + " \"$OLD\" \"$SC\" \"$SF0\" \"$SS\""
                        + " >/dev/null 2>&1 & SP=$!;"
                        + " T SPLASH_RENDERER_STARTED"
                        + " \"composer_pid=$SC;sf_pid=$SS;splash_pid=$SP\"; }",
                "restore_ba(){ [ \"$BAS\" = 1 ] || return 0;"
                        + " setprop debug.sf.nobootanimation \"$BAPREV\" >/dev/null 2>&1;"
                        + " V=$BAPREV; [ -n \"$V\" ] || V=UNSET;"
                        + " T BOOTANIM_SUPPRESS_RESTORED \"value=$V\"; BAS=0; }",
                "restore_native(){ [ \"$NBA\" = 1 ] || return 0;"
                        + " setprop " + RecoveryBootAnimationAsset.CUSTOM_PROPERTY
                        + " \"$NBPREV\" >/dev/null 2>&1;"
                        + " V=$(getprop " + RecoveryBootAnimationAsset.CUSTOM_PROPERTY + ");"
                        + " if [ \"$V\" = \"$NBPREV\" ]; then"
                        + " X=$NBPREV; [ -n \"$X\" ] || X=UNSET;"
                        + " T NATIVE_BOOTANIM_RESTORED \"value=$X\";"
                        + " else T NATIVE_BOOTANIM_RESTORE_FAILED reason=READBACK; fi;"
                        + " rm -f \"$NBPATH\" >/dev/null 2>&1; NBA=0; }",
                "cleanup_bootanim(){ restore_native; restore_ba; }",
                "trap cleanup_bootanim EXIT",
                "OLD='" + beforeComposerPid + "'",
                "DP=" + daemonPid,
                "SF0=$(pidof surfaceflinger); Z0=$(pidof zygote64); SS0=$(pidof system_server)",
                "T HELPER_START \"old_composer=$OLD;old_sf=${SF0:--};old_zygote=${Z0:--};"
                        + "old_system_server=${SS0:--};daemon_pid=$DP\"",
                "[ \"$RSP\" = 1 ] && T SPLASH_ARMED prototype=1",
                "[ \"$RSP\" = 1 ] && T SPLASH_WAIT_SF \"old_sf=${SF0:--}\"",
                "[ \"$NBA\" = 1 ] && T NATIVE_BOOTANIM_WAIT_SF"
                        + " \"path=$NBPATH;display_route=INTERNAL_DEFAULT\"",
                "B=$(cs); N=0; NC=''; NS=''; NZ=''; NSS=''; PKS=''; STS=''; SC=''; SS='';"
                        + " NBSEEN=''; NBDONE=''",
                "while [ $(($(cs)-B)) -lt 800 ] && [ $N -lt 60 ]; do",
                "  P=$(pidof vendor.qti.hardware.display.composer-service)",
                "  if [ -z \"$NC\" ] && [ -n \"$P\" ] && [ \"$P\" != \"$OLD\" ]; then"
                        + " NC=$P; T HELPER_COMPOSER_NEW_PID \"composer_pid=$P\"; fi",
                "  if [ -z \"$SC\" ]; then Q=$(succ \"$OLD\" $P);"
                        + " [ -n \"$Q\" ] && SC=$Q; fi",
                "  P=$(pidof surfaceflinger)",
                "  if [ -z \"$NS\" ] && [ -n \"$P\" ] && [ \"$P\" != \"$SF0\" ]; then"
                        + " NS=$P; T HELPER_SF_NEW_PID \"sf_pid=$P\"; fi",
                "  if [ -z \"$SS\" ]; then Q=$(succ \"$SF0\" $P);"
                        + " [ -n \"$Q\" ] && SS=$Q; fi",
                "  [ -n \"$SC\" ] && [ -n \"$SS\" ] && start_splash",
                "  if [ \"$NBA\" = 1 ]; then BP=$(pidof bootanimation);"
                        + " if [ -z \"$NBSEEN\" ] && [ -n \"$BP\" ]; then"
                        + " NBSEEN=1; T NATIVE_BOOTANIM_STARTED \"pid=$BP\";"
                        + " elif [ \"$NBSEEN\" = 1 ] && [ -z \"$NBDONE\" ]"
                        + " && [ -z \"$BP\" ]; then NBDONE=1;"
                        + " T NATIVE_BOOTANIM_EXITED; fi; fi",
                "  P=$(pidof zygote64)",
                "  if [ -z \"$NZ\" ] && [ -n \"$P\" ] && [ \"$P\" != \"$Z0\" ]; then"
                        + " NZ=$P; T HELPER_ZYGOTE_NEW_PID \"zygote_pid=$P\"; fi",
                // Observability only: service readiness of the new system_server
                // shows how much of the eight-second floor is actually needed.
                "  P=$(pidof system_server)",
                "  if [ -z \"$NSS\" ] && [ -n \"$P\" ] && [ \"$P\" != \"$SS0\" ]; then"
                        + " NSS=$P; T HELPER_SYSTEM_SERVER_NEW_PID \"system_server_pid=$P\"; fi",
                "  if [ -n \"$NSS\" ] && [ -z \"$PKS\" ]"
                        + " && service check package 2>/dev/null | grep -q ': found$'; then"
                        + " PKS=1; T HELPER_PACKAGE_SERVICE_FOUND; fi",
                "  if [ -n \"$NSS\" ] && [ -z \"$STS\" ]"
                        + " && service check settings 2>/dev/null | grep -q ': found$'; then"
                        + " STS=1; T HELPER_SETTINGS_SERVICE_FOUND; fi",
                "  N=$((N+1)); sleep 0.25",
                "done",
                // The eight-second floor holds even if fractional sleep is unsupported.
                "R=$((800-($(cs)-B))); [ $R -gt 0 ] && sleep $(((R+99)/100))",
                "T HELPER_FLOOR_DONE \"polls=$N\"",
                "A=''",
                "for I in $(seq 1 10); do",
                "  [ \"$(getprop init.svc.vendor.qti.hardware.display.composer)\" = running ]"
                        + " && [ \"$(getprop init.svc.surfaceflinger)\" = running ]"
                        + " && [ \"$(getprop init.svc.zygote)\" = running ]"
                        + " && [ \"$(getprop sys.boot_completed)\" = 1 ]"
                        + " && A=$(pm path com.thor.displaypowertest 2>/dev/null)"
                        + " && [ -n \"$A\" ] && break",
                "  A=''; sleep 1",
                "done",
                "[ -n \"$A\" ] && PM=1 || PM=0",
                "T HELPER_SERVICES_CHECKED \"attempts=$I;pm=$PM\"",
                "NEW=$(pidof vendor.qti.hardware.display.composer-service)",
                "if [ -z \"$NEW\" ] || [ \"$NEW\" = \"$OLD\" ]; then"
                        + " T HELPER_ABORT reason=COMPOSER_NOT_RESTARTED;"
                        + " exit " + HandoffRecoveryModel.EXIT_COMPOSER_NOT_RESTARTED + "; fi",
                "if [ -z \"$A\" ]; then for I in $(seq 1 30); do"
                        + " A=$(pm path com.thor.displaypowertest 2>/dev/null);"
                        + " [ -n \"$A\" ] && break; sleep 1; done; fi",
                "A=${A#*:}",
                "if [ -z \"$A\" ]; then T HELPER_ABORT reason=PACKAGE_PATH;"
                        + " exit " + HandoffRecoveryModel.EXIT_PACKAGE_PATH + "; fi",
                "T HELPER_PM_READY \"composer_pid=$NEW\"",
                "state(){ [ ! -d /proc/$DP ] && { echo GONE; return; };"
                        + " S=$(sed -n 's/^State:[[:space:]]*\\([A-Z]\\).*/\\1/p'"
                        + " /proc/$DP/status 2>/dev/null);"
                        + " if [ -z \"$S\" ]; then"
                        + " if [ -d /proc/$DP ]; then echo UNKNOWN; else echo GONE; fi;"
                        + " elif [ \"$S\" = Z ]; then echo GONE;"
                        + " else echo LIVE; fi; }",
                // Only signal the PID if it is still this root daemon, then wait
                // for its exit so the successor's instance lock cannot lose.
                // A failed identity read is re-classified: a daemon that has
                // just exited is gone, anything still present is a mismatch.
                "ident(){ [ \"$(stat -c %u /proc/$DP 2>/dev/null)\" = 0 ]"
                        + " && tr '\\000' ' ' </proc/$DP/cmdline 2>/dev/null"
                        + " | grep -Eq '^app_process / D [01] (hold|run) [01] [01]( |$)'"
                        + " && { echo OK; return; };"
                        + " [ \"$(state)\" = GONE ] && echo GONE || echo BAD; }",
                "PSTATE=$(state)",
                "if [ \"$PSTATE\" = UNKNOWN ]; then"
                        + " T HELPER_ABORT reason=DAEMON_IDENTITY_MISMATCH;"
                        + " exit " + HandoffRecoveryModel.EXIT_DAEMON_IDENTITY + "; fi",
                "[ \"$PSTATE\" = LIVE ] && PSTATE=$(ident)",
                "if [ \"$PSTATE\" = BAD ]; then"
                        + " T HELPER_ABORT reason=DAEMON_IDENTITY_MISMATCH;"
                        + " exit " + HandoffRecoveryModel.EXIT_DAEMON_IDENTITY + "; fi",
                "if [ \"$PSTATE\" = OK ]; then",
                // A daemon that exits between the checks and the signal is
                // not a failure; only a live process that rejects it is.
                "  if kill $DP 2>/dev/null; then T HELPER_KILL_SENT \"daemon_pid=$DP\";"
                        + " elif [ \"$(state)\" = GONE ]; then"
                        + " T HELPER_KILL_RACE_GONE \"daemon_pid=$DP\";"
                        + " else T HELPER_ABORT reason=KILL_FAILED;"
                        + " exit " + HandoffRecoveryModel.EXIT_KILL_FAILED + "; fi",
                "  I=0; while [ \"$(state)\" != GONE ] && [ $I -lt 50 ];"
                        + " do sleep 0.1; I=$((I+1)); done",
                "  [ \"$(state)\" != GONE ] && sleep 1",
                "  if [ \"$(state)\" != GONE ]; then"
                        + " T HELPER_ABORT reason=OLD_DAEMON_ALIVE;"
                        + " exit " + HandoffRecoveryModel.EXIT_OLD_DAEMON_ALIVE + "; fi",
                "  T HELPER_OLD_EXITED \"polls=$I\"",
                "else",
                "  T HELPER_OLD_GONE \"daemon_pid=$DP\"",
                "fi",
                "T HELPER_EXEC_NEW \"composer_pid=$NEW\"",
                "CLASSPATH=$A app_process / D " + enabled
                        + " hold " + desiredCpu
                        + " " + desiredLidGuard + " " + phaseStartedAt
                        + " post &",
                "cleanup_bootanim",
                "trap - EXIT");
        ProcessBuilder helper = new ProcessBuilder("sh", "-c", command);
        // Launch timing belongs to this daemon only; never pass it to the successor.
        helper.environment().keySet().removeIf(name -> name.startsWith("JESTY_"));
        helper.environment().remove("JT");
        return helper.start();
    }
}
