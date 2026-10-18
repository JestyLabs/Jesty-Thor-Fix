package com.thor.displaypowertest;

import android.os.SystemProperties;
import android.view.SurfaceControl;
import java.util.Arrays;

/** Executes unchanged DisplayHardware with inert reflection endpoints. */
public final class DisplayHardwareFailureHostTest {
    private static int checks;
    private static int cases;
    private static void equal(Object expected, Object actual, String why) {
        checks++;
        if (!expected.equals(actual)) throw new AssertionError(why + ": " + actual);
    }
    private static void reset(String prior) {
        SystemProperties.value = prior;
        SystemProperties.readFails = false;
        SystemProperties.failWrite = 0;
        SystemProperties.attempts.clear();
        SystemProperties.writes.clear();
        SurfaceControl.missingToken = false;
        SurfaceControl.powerFails = false;
        SurfaceControl.tokens = 0;
        SurfaceControl.requests = 0;
        SurfaceControl.propertyAtPower = null;
        DaemonState.action = "initial";
    }
    private static void done(String id) {
        cases++;
        System.out.println("PASS " + id + "; real DisplayHardware, inert Android reflection");
    }
    private static void rollback() {
        reset("0");
        SurfaceControl.powerFails = true;
        equal(false, DisplayHardware.apply(true, "FAIL"), "HW-rollback false");
        equal(Arrays.asList("1", "0"), SystemProperties.writes, "HW-rollback writes");
        equal("0", SystemProperties.value, "HW-rollback restored");
        equal("1", SurfaceControl.propertyAtPower, "HW-rollback ordering");
        equal(1, SurfaceControl.requests, "HW-rollback one request");
        equal("FAIL:ERROR", DaemonState.action, "HW-rollback no success");
        done("HW-rollback");
    }
    public static void main(String[] args) {
        if (args.length > 0) { rollback(); return; }
        for (boolean on : new boolean[] {true, false}) {
            reset(on ? "0" : "1");
            equal(true, DisplayHardware.apply(on, "OK"), "HW-success result");
            equal(on ? 2 : 0, SurfaceControl.lastMode, "HW-success power mode");
            equal(on ? "1" : "0", SurfaceControl.propertyAtPower, "HW-success property ordering");
            equal(1, SystemProperties.writes.size(), "HW-success one write");
            equal(on ? "OK:ON" : "OK:OFF", DaemonState.action, "HW-success action");
            done(on ? "HW-success-on" : "HW-success-off");
        }
        reset("0");
        SurfaceControl.missingToken = true;
        equal(false, DisplayHardware.apply(true, "NULL"), "HW-token result");
        equal(0, SystemProperties.attempts.size(), "HW-token no property attempt");
        equal(0, SurfaceControl.requests, "HW-token no power attempt");
        equal("NULL:TOKEN_NULL", DaemonState.action, "HW-token action");
        done("HW-token");
        reset("0");
        SystemProperties.failWrite = 1;
        equal(false, DisplayHardware.apply(true, "WRITE"), "HW-write result");
        equal(0, SurfaceControl.requests, "HW-write no vendor request");
        equal("0", SystemProperties.value, "HW-write unchanged");
        equal(1, SystemProperties.attempts.size(), "HW-write no false rollback");
        equal("WRITE:ERROR", DaemonState.action, "HW-write action");
        done("HW-property-write-failure");
        rollback();
        reset("0");
        SurfaceControl.powerFails = true;
        SystemProperties.failWrite = 2;
        equal(false, DisplayHardware.apply(true, "ROLLBACK"), "HW-rollback-failed result");
        equal(Arrays.asList("1", "0"), SystemProperties.attempts, "HW-rollback-failed attempted");
        equal("1", SystemProperties.value, "HW-rollback-failed unresolved logical state");
        equal("ROLLBACK:ERROR", DaemonState.action, "HW-rollback-failed no success");
        done("HW-rollback-failed");
        reset("?");
        SurfaceControl.powerFails = true;
        equal(false, DisplayHardware.apply(true, "UNKNOWN"), "HW-unknown result");
        equal(Arrays.asList("1"), SystemProperties.attempts, "HW-unknown no invented rollback");
        equal("UNKNOWN:ERROR", DaemonState.action, "HW-unknown action");
        done("HW-unknown-prior");
        reset("0");
        SystemProperties.readFails = true;
        SurfaceControl.powerFails = true;
        equal("?", DisplayHardware.getProperty(), "HW-read failure sentinel");
        equal(false, DisplayHardware.apply(true, "READ"), "HW-read-failed result");
        equal(Arrays.asList("1"), SystemProperties.attempts, "HW-read-failed no invented rollback");
        equal("READ:ERROR", DaemonState.action, "HW-read-failed action");
        done("HW-property-read-failure");
        System.out.println("DisplayHardwareFailureHostTest passed: " + cases + " cases, " + checks
                + " checks; Android/vendor/Thor NOT RUN");
    }
}

final class DaemonState {
    static String action;
    static void setLastAction(String value) { action = value; }
}
