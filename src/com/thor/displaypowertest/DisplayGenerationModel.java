package com.thor.displaypowertest;

/** Invalidates delayed display work after a newer mode, fix or sleep action. */
public final class DisplayGenerationModel {
    private long generation;
    private long repairGeneration = -1L;

    public synchronized long current() { return generation; }

    public synchronized void invalidate() {
        generation++;
        repairGeneration = -1L;
    }

    public synchronized long scheduleRepair() {
        generation++;
        repairGeneration = generation;
        return generation;
    }

    public synchronized boolean isRepairCurrent() {
        return repairGeneration == generation;
    }

    public synchronized void completeRepair() { repairGeneration = -1L; }
}
