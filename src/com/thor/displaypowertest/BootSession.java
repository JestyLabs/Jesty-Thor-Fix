package com.thor.displaypowertest;

/**
 * Boot-coordinator state shared by BootCoordinator and CpuFixController. It
 * replaces the daemon's former static fields; the values and their meaning are
 * unchanged.
 */
public final class BootSession {
    private volatile boolean active;
    private volatile long startedAt;
    private final boolean cpuFixDesired;
    private final boolean lidGuardDesired;

    public BootSession(boolean active, long startedAt, boolean cpuFixDesired,
            boolean lidGuardDesired) {
        this.active = active;
        this.startedAt = startedAt;
        this.cpuFixDesired = cpuFixDesired;
        this.lidGuardDesired = lidGuardDesired;
    }

    /** True while the boot coordinator owns display actions (boot hold). */
    public boolean active() { return active; }
    public void finish() { active = false; }

    /** Start of the current readiness phase on the boot clock. */
    public long startedAt() { return startedAt; }
    public void restartDeadline(long now) { startedAt = now; }

    public boolean cpuFixDesired() { return cpuFixDesired; }
    /** Saved Wake Guard preference; the watcher is enabled only after READY. */
    public boolean lidGuardDesired() { return lidGuardDesired; }
}
