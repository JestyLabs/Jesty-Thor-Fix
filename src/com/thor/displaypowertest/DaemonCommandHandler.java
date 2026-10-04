package com.thor.displaypowertest;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The daemon's closed one-byte command set. No command takes an argument;
 * everything a command may do is decided here.
 */
public final class DaemonCommandHandler {
    private final CpuFixController cpuFix;
    private final BootTrace trace;
    private final int pid;
    private final AtomicBoolean firstIdentityTrace = new AtomicBoolean();

    public DaemonCommandHandler(CpuFixController cpuFix, BootTrace trace, int pid) {
        this.cpuFix = cpuFix;
        this.trace = trace;
        this.pid = pid;
    }

    /** After a compositor restart, mark the first identity query in the trace. */
    public void traceFirstIdentityQuery() {
        firstIdentityTrace.set(true);
    }

    public String handle(char command) {
        if (BootSafety.isHeld() && command != 'I' && command != 'Q' && command != 'V') {
            return "ok=0;error=BOOT_HOLD;boot_phase="
                    + BootSafety.phase().replace(' ', '_');
        }
        switch (command) {
            case 'I':
                // After a compositor restart the first identity query normally
                // comes from AutoService on Android's second BOOT_COMPLETED.
                if (firstIdentityTrace.compareAndSet(true, false)) {
                    trace.mark("FIRST_IDENTITY_QUERY", null);
                }
                // healthyResponse in PServer requires this to end with ";watcher=".
                return "ok=1;protocol=" + SecureChannel.PROTOCOL
                    + ";version=" + DaemonIdentity.VERSION + ";pid=" + pid
                    + ";boot_phase=" + BootSafety.phase().replace(' ', '_')
                    + ";phase_ms=" + BootSafety.phaseAgeMs()
                    + ";fix=" + (DaemonState.isEnabled() ? "1" : "0")
                    + ";watcher=" + WatcherSupervisor.health();
            case 'E':
                return DisplayActionCoordinator.requestFix(true);
            case 'N':
                return DisplayActionCoordinator.requestFix(false);
            case 'Q': return DaemonState.snapshot()
                    + ";cpu_fix_desired=" + (cpuFix.desired() ? "1" : "0")
                    + ";cpu_fix_phase=" + cpuFix.phase()
                    + ";handoff=" + cpuFix.handoffState();
            case 'V': return Telemetry.verifyDrm();
            case 'R': return cpuFix.apply(true);
            case 'L': return cpuFix.apply(false);
            case 'G': return LidGuard.setEnabled(true) ? "ok=1;lid_guard=1"
                    : "ok=0;error=HALL_UNAVAILABLE";
            case 'H': LidGuard.setEnabled(false); return "ok=1;lid_guard=0";
            default: return "ok=0;error=UNKNOWN_COMMAND";
        }
    }
}
