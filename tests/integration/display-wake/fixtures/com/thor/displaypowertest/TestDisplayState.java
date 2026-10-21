package com.thor.displaypowertest;

/** Synthetic hardware boundary shared by host and Android coordinator tests. */
public final class TestDisplayState {
    public static String mode = "1", top = "1", bottom = "0", property = "0";
    public static int propertyWrites, hardwareCalls;
    public static boolean enabled = true, held, guard;
    public static boolean noToken, powerFailure, guardDefers;
    /** Synthetic platform-stall boundary, default-off (see android.view.SurfaceControl). */
    public static boolean vendorGate;
    public static String platformPropertyDuringCall;
    /** Synthetic: vendor mode switched while the compositor call was blocked (live recurrence B). */
    public static String modeDuringCall;
    public static long callSleepMs;
    /** Synthetic: elapsed-clock time consumed by a blocked compositor call. */
    public static long callAdvanceMs;
    /** Synthetic power state seen through IPowerManager.isInteractive(). */
    public static boolean interactive = true, powerServiceMissing;
    public static int powerQueries;
    public static int gateRefusals;
    public static java.util.concurrent.CountDownLatch callEntered, callRelease;
    public static void baseline() {
        DisplayActionCoordinator.cancelWakeRepair();
        DisplayActionCoordinator.onWatcherSample("?");
        enabled = true; held = false; guard = false;
        noToken = false; powerFailure = false; guardDefers = false;
        vendorGate = false; platformPropertyDuringCall = null; modeDuringCall = null; callSleepMs = 0L; callAdvanceMs = 0L; gateRefusals = 0;
        callEntered = null; callRelease = null;
        interactive = true; powerServiceMissing = false; powerQueries = 0;
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
