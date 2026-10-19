package com.thor.displaypowertest;

/** Synthetic hardware boundary shared by host and Android coordinator tests. */
public final class TestDisplayState {
    public static String mode = "1", top = "1", bottom = "0", property = "0";
    public static int propertyWrites, hardwareCalls;
    public static boolean enabled = true, held, guard;
    public static boolean noToken, powerFailure, guardDefers;
    public static void baseline() {
        DisplayActionCoordinator.cancelWakeRepair();
        DisplayActionCoordinator.onWatcherSample("?");
        enabled = true; held = false; guard = false;
        noToken = false; powerFailure = false; guardDefers = false;
        mode = "1"; top = "1"; bottom = "0"; property = "0";
        propertyWrites = 0; hardwareCalls = 0;
        WakeRepairScheduler.schedules = 0; WakeRepairScheduler.notes = 0;
        DisplayActionCoordinator.onWatcherSample(mode);
    }
    public static void wakeTop() {
        mode = "1"; top = "1"; bottom = "1"; property = "1";
        DisplayActionCoordinator.onWatcherSample(mode);
    }
    public static void sleep() {
        top = "0"; bottom = "0"; property = "0";
        DisplayActionCoordinator.onWatcherSample(mode);
    }
    public static void both() {
        mode = "0"; top = "1"; bottom = "1";
        DisplayActionCoordinator.onWatcherSample(mode);
    }
}
