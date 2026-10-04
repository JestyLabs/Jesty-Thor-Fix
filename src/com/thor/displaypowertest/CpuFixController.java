package com.thor.displaypowertest;

import android.os.SystemClock;
import android.util.Log;

/**
 * AYN Dashboard CPU Fix: the vendor system-load-check property, the one-shot
 * compositor restart, its timed wake lock and the helper that relaunches the
 * daemon after Android's framework restarts. Formerly static code in D.
 *
 * Every state change that the boot coordinator also observes is guarded by
 * {@link #lock}, the former D.class monitor.
 */
public final class CpuFixController {
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

    private String applyLocked(boolean enabled) {
        final String desiredValue = enabled ? "1" : "0";
        final String actual = Telemetry.systemLoadFixObservation();
        boolean unsetEnable = enabled && PropertyState.UNSET.equals(actual);
        if (!unsetEnable && !"0".equals(actual) && !"1".equals(actual)) {
            return "ok=0;error=CPU_FIX_STATE_UNKNOWN";
        }
        if (!probe.composerRunning()) {
            return "ok=0;error=COMPOSER_NOT_READY";
        }
        if (!"RUNNING".equals(WatcherSupervisor.health())) {
            return "ok=0;error=WATCHER_NOT_READY";
        }
        final String beforeComposerPid = probe.composerPid();
        if ("?".equals(beforeComposerPid)) return "ok=0;error=COMPOSER_PID_UNKNOWN";
        if (desiredValue.equals(actual) && !restartFailed) {
            desired = enabled;
            return "ok=1;system_load_fix=" + desiredValue + ";composer_restart=not_needed";
        }
        if (restartScheduled || helperAlive) {
            return "ok=0;error=COMPOSER_RESTART_BUSY";
        }
        if (!wakeLock.acquire()) {
            return "ok=0;error=WAKE_LOCK_FAILED";
        }
        final boolean bootTrace = session.active();
        String propertyResult = setSystemLoadCheckValue(desiredValue);
        if (!propertyResult.startsWith("ok=1")) {
            wakeLock.release();
            return propertyResult;
        }
        if (bootTrace) trace.mark("CPU_PROP_WRITTEN", "value=" + desiredValue);
        if (!desiredValue.equals(Telemetry.systemLoadFixObservation())) {
            restoreSystemLoadCheckState(actual);
            wakeLock.release();
            return "ok=0;error=SETPROP_UNCONFIRMED";
        }
        if (bootTrace) trace.mark("CPU_PROP_VERIFIED", "value=" + desiredValue);
        desired = enabled;
        restartFailed = false;
        restartScheduled = true;
        new Thread(() -> runRestart(bootTrace, beforeComposerPid, actual),
                "composer-restart-once").start();
        return "ok=1;system_load_fix=" + desiredValue + ";composer_restart=scheduled_once";
    }

    private void runRestart(boolean bootTrace, String beforeComposerPid, String previousCpu) {
        Process helper = null;
        try {
            Thread.sleep(300L);
            helper = scheduleTransitionCleanupAndDaemonRestart(beforeComposerPid);
            handoffState = "SCHEDULED";
            observeHelper(helper, bootTrace, beforeComposerPid, previousCpu);
            if (bootTrace) trace.mark("HELPER_SCHEDULED", "old_composer=" + beforeComposerPid);
            if (bootTrace) trace.mark("CTL_RESTART_SENT", null);
            int exit = new ProcessBuilder("setprop", "ctl.restart",
                    SystemProbe.COMPOSER_SERVICE).start().waitFor();
            if (bootTrace) trace.mark("CTL_RESTART_ACK", "exit=" + exit);
            if (exit != 0) throw new IllegalStateException("composer restart exit=" + exit);
            Thread.sleep(3000L);
        } catch (Throwable error) {
            restartFailed = true;
            if (beforeComposerPid.equals(probe.composerPid())) {
                restoreSystemLoadCheckState(previousCpu);
            }
            Log.e("ThorDisplayDaemon", "composer restart failed", error);
            wakeLock.release();
        } finally {
            synchronized (lock) { restartScheduled = false; }
        }
        // No helper started, so ctl.restart was never sent either: the
        // framework did not restart and this daemon's watcher is current.
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
            final String beforeComposerPid, final String previousCpu) {
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
            onHelperExit(exit, bootHandoff, beforeComposerPid, previousCpu);
        }, "thor-helper-observer");
        observer.setDaemon(true);
        observer.start();
    }

    private void onHelperExit(int exit, boolean bootHandoff, String beforeComposerPid,
            String previousCpu) {
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
            if (beforeComposerPid.equals(probe.composerPid())) {
                restoreSystemLoadCheckState(previousCpu);
            }
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

    private Process scheduleTransitionCleanupAndDaemonRestart(String beforeComposerPid)
            throws Exception {
        String enabled = DaemonState.isEnabled() ? "1" : "0";
        String desiredCpu = desired ? "1" : "0";
        // During BOOT HOLD the Hall watcher has not been enabled yet. Preserve
        // its saved preference rather than sampling the inactive watcher.
        String desiredLidGuard = (session.active() ? session.lidGuardDesired()
                : LidGuard.isEnabled()) ? "1" : "0";
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
                "cs(){ read U X </proc/uptime; S=${U%.*}; F=${U#*.}; F=${F#0};"
                        + " echo $((S*100+${F:-0})); }",
                // A trace line is skipped, never redirected, if the path is unsafe.
                "T(){ safe_log \"$TR\" || return 0;"
                        + " echo \"elapsed_ms=$(cs)0;action=$1;${2:+$2;}pid=$$;"
                        + "boot_id=${BID:-?};source=helper\" >>\"$TR\"; }",
                "OLD='" + beforeComposerPid + "'",
                "DP=" + daemonPid,
                "SF0=$(pidof surfaceflinger); Z0=$(pidof zygote64); SS0=$(pidof system_server)",
                "T HELPER_START \"old_composer=$OLD;old_sf=${SF0:--};old_zygote=${Z0:--};"
                        + "old_system_server=${SS0:--};daemon_pid=$DP\"",
                "B=$(cs); N=0; NC=''; NS=''; NZ=''; NSS=''; PKS=''; STS=''",
                "while [ $(($(cs)-B)) -lt 800 ] && [ $N -lt 60 ]; do",
                "  P=$(pidof vendor.qti.hardware.display.composer-service)",
                "  if [ -z \"$NC\" ] && [ -n \"$P\" ] && [ \"$P\" != \"$OLD\" ]; then"
                        + " NC=$P; T HELPER_COMPOSER_NEW_PID \"composer_pid=$P\"; fi",
                "  P=$(pidof surfaceflinger)",
                "  if [ -z \"$NS\" ] && [ -n \"$P\" ] && [ \"$P\" != \"$SF0\" ]; then"
                        + " NS=$P; T HELPER_SF_NEW_PID \"sf_pid=$P\"; fi",
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
                        + " post &");
        ProcessBuilder helper = new ProcessBuilder("sh", "-c", command);
        // Launch timing belongs to this daemon only; never pass it to the successor.
        helper.environment().keySet().removeIf(name -> name.startsWith("JESTY_"));
        return helper.start();
    }
}
