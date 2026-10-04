package com.thor.displaypowertest;

import android.os.SystemClock;
import android.util.Log;

/**
 * Staged boot gate: waits for Android, the compositor, a known mode and three
 * consistent CRTC samples, then applies the CPU Fix once or reconciles the
 * display. Formerly static code in D; the gate rules live in BootGateModel.
 */
public final class BootCoordinator implements CpuFixController.HandoffRecovery {
    private final BootSession session;
    private final SystemProbe probe;
    private final BootTrace trace;
    private CpuFixController cpuFix;

    public BootCoordinator(BootSession session, SystemProbe probe, BootTrace trace) {
        this.session = session;
        this.probe = probe;
        this.trace = trace;
    }

    /** Called once by DaemonRuntime; the two objects reference each other. */
    void attach(CpuFixController cpuFix) {
        if (this.cpuFix != null) throw new IllegalStateException("CPU fix already attached");
        this.cpuFix = cpuFix;
    }

    public void run(boolean afterComposerRestart) {
        boolean handedOff = false;
        try {
            handedOff = reconcileBootPhase(afterComposerRestart);
        } finally {
            // Only a daemon that scheduled a compositor restart hands the timed
            // lock to its successor. Any other ending releases it here, so a
            // daemon that won the launch race cannot leave it to the timeout.
            if (!handedOff && cpuFix.releaseWakeLockUnlessRestartScheduled()) {
                trace.mark("WAKE_UNLOCK_SENT", "after_composer="
                        + (afterComposerRestart ? "1" : "0"));
            }
        }
    }

    /**
     * Only for a handover in which the framework provably did not restart: the
     * watcher and display callback still belong to the running system_server.
     */
    @Override public void recoverInPlace(String reason) {
        if (!"RUNNING".equals(WatcherSupervisor.health())) {
            cpuFix.markHandoffFailed("reason=" + reason + ";watcher_not_running=1");
            return;
        }
        cpuFix.setHandoffState("RECOVERING");
        DaemonState.setLastAction("HANDOFF_RECOVERING");
        trace.mark("HANDOFF_RECOVERY_BEGIN", "reason=" + reason);
        // A fresh deadline: the original boot deadline may already be spent.
        session.restartDeadline(SystemClock.elapsedRealtime());
        run(true);
        boolean recovered = !BootSafety.isHeld();
        cpuFix.setHandoffState(recovered ? "RECOVERED" : "RECOVERY_FAILED");
        trace.mark(recovered ? "HANDOFF_RECOVERED" : "HANDOFF_RECOVERY_FAILED",
                "phase=" + BootSafety.phase().replace(' ', '_'));
    }

    /** Returns true only when a compositor restart now owns the boot transition. */
    private boolean reconcileBootPhase(boolean afterComposerRestart) {
        BootGateModel gate = new BootGateModel(session.startedAt(), afterComposerRestart);
        BootSafety.phase(afterComposerRestart ? "WAITING AFTER COMPOSER" : "WAITING FOR ANDROID");
        trace.boot(afterComposerRestart ? "WAIT_AFTER_COMPOSER" : "WAIT_FOR_ANDROID");
        Boolean lastBooted = null;
        Boolean lastComposer = null;
        Boolean lastModeKnown = null;
        Boolean lastCrtcValid = null;
        while (true) {
            long now = SystemClock.elapsedRealtime();
            boolean booted = probe.bootCompleted();
            boolean composer = probe.composerRunning();
            String mode = DaemonState.getMode();
            String[] crtc = Telemetry.crtcActivePair();
            String top = crtc[0];
            String bottom = crtc[1];
            lastBooted = gateEdge("GATE_BOOT_COMPLETED", lastBooted, booted);
            lastComposer = gateEdge("GATE_COMPOSER_RUNNING", lastComposer, composer);
            lastModeKnown = gateEdge("GATE_MODE_KNOWN", lastModeKnown,
                    BootSafety.knownMode(mode));
            lastCrtcValid = gateEdge("GATE_CRTC_VALID", lastCrtcValid,
                    SystemProbe.binary(top) && SystemProbe.binary(bottom));
            int previousSamples = gate.stableSamples();
            String previousCandidate = gate.candidate();
            BootGateModel.Result result = gate.observe(now, booted, composer, mode, top, bottom);
            if (result == BootGateModel.Result.TIMEOUT) {
                BootSafety.timeout();
                DaemonState.setLastAction("BOOT_SAFETY_TIMEOUT");
                Log.e("ThorDisplayDaemon", "BOOT SAFETY TIMEOUT; no display action");
                trace.boot("BOOT_SAFETY_TIMEOUT");
                return false;
            }
            int samples = gate.stableSamples();
            if (previousSamples > 0 && samples != previousSamples + 1) {
                trace.mark("GATE_RESET", "previous_samples=" + previousSamples
                        + ";previous=" + safeCandidate(previousCandidate)
                        + ";candidate=" + safeCandidate(gate.candidate()));
            }
            // samples == 1 always means a new candidate, even right after another one.
            if (samples == 1 || (samples <= 3 && samples == previousSamples + 1)) {
                trace.mark("GATE_STABLE_SAMPLE", "n=" + samples
                        + ";candidate=" + safeCandidate(gate.candidate()));
            }
            if (samples == 3 && previousSamples == 2) {
                trace.mark("GATE_GRACE_BEGIN", "grace_ms=" + gate.graceMs());
            }
            if (result == BootGateModel.Result.READY) {
                trace.mark("GATE_GRACE_END", "grace_ms=" + gate.graceMs()
                        + ";samples=" + samples);
                break;
            }
            long delay = gate.nextSampleDelayMs(now, SystemClock.elapsedRealtime());
            try { Thread.sleep(delay); } catch (InterruptedException ignored) { return false; }
        }
        BootGateModel.CpuAction cpuAction = BootGateModel.cpuAction(session.cpuFixDesired(),
                Telemetry.systemLoadFixObservation(), afterComposerRestart);
        if (cpuAction == BootGateModel.CpuAction.FAIL_SAFE) {
            // A read failure or invalid CPU property must never trigger a restart.
            // It does not make a separately verified display state unsafe.
            DaemonState.setLastAction("BOOT_CPU_FIX_NOT_APPLIED");
            trace.boot("BOOT_CPU_FIX_NOT_APPLIED");
            Log.w("ThorDisplayDaemon", "CPU fix not confirmed; continuing safe display reconcile");
        }
        if (cpuAction == BootGateModel.CpuAction.RESTART_ONCE) {
            BootSafety.phase("APPLYING CPU FIX");
            trace.boot("APPLY_CPU_FIX");
            String result = cpuFix.apply(session.cpuFixDesired());
            if (result.contains("composer_restart=scheduled_once")) return true;
            BootSafety.timeout();
            DaemonState.setLastAction("BOOT_CPU_FIX_FAILED");
            Log.e("ThorDisplayDaemon", "boot CPU reconcile failed: " + result);
            trace.boot("BOOT_CPU_FIX_FAILED");
            return false;
        }
        BootSafety.phase("RECONCILING DISPLAY");
        trace.boot("RECONCILE_DISPLAY");
        String mode = DaemonState.getMode();
        if (!BootSafety.knownMode(mode)) {
            BootSafety.timeout();
            return false;
        }
        boolean bottomShouldBeOn = !DaemonState.isEnabled() || !"1".equals(mode);
        if (!DisplayActionCoordinator.reconcileBoot(mode, bottomShouldBeOn)) {
            BootSafety.timeout();
            trace.boot("BOOT_DISPLAY_FAILED");
            return false;
        }
        trace.boot(bottomShouldBeOn ? "BOTTOM_ON_CONFIRMED" : "BOTTOM_OFF_CONFIRMED");
        BootSafety.ready();
        session.finish();
        if (session.lidGuardDesired() && !LidGuard.setEnabled(true)) {
            Log.w("ThorDisplayDaemon", "Hall switch unavailable; wake guard disabled");
        }
        DaemonState.setLastAction("BOOT_READY");
        Log.d("ThorDisplayDaemon", "BOOT READY mode=" + mode
                + " bottom=" + Telemetry.bottomCrtcActive());
        trace.boot("BOOT_READY");
        return false;
    }

    private Boolean gateEdge(String action, Boolean previous, boolean current) {
        if (previous == null || previous != current) {
            trace.mark(action, "value=" + (current ? "1" : "0"));
        }
        return current;
    }

    private static String safeCandidate(String candidate) {
        return candidate != null && candidate.matches("[0-2]:[01]:[01]") ? candidate : "-";
    }
}
