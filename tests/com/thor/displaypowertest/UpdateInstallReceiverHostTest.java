package com.thor.displaypowertest;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.widget.Toast;

/** Exercises the actual production receiver's callback switch with host Android fakes. */
public final class UpdateInstallReceiverHostTest {
    private static final Context CONTEXT = new Context();
    private static final UpdateInstallReceiver RECEIVER = new UpdateInstallReceiver();
    private static int assertions;

    public static void main(String[] args) {
        reset();
        RECEIVER.onReceive(CONTEXT, null);
        RECEIVER.onReceive(CONTEXT, new Intent().setAction("unrelated.action"));
        check(noEffects(), "null and unrelated broadcasts are ignored");

        reset();
        Intent confirmation = new Intent().setAction("android.intent.action.CONFIRM");
        RECEIVER.onReceive(CONTEXT, callback(PackageInstaller.STATUS_PENDING_USER_ACTION, 7)
                .putExtra(Intent.EXTRA_INTENT, confirmation));
        check(AppUpdater.confirmations == 1 && AppUpdater.lastConfirmation == confirmation,
                "actual pending callback dispatches confirmation");
        check(AppUpdater.lastSessionId == 7 && AppUpdater.abandonCalls == 0
                && AppUpdater.clearCalls == 0 && Toast.shown == 0,
                "pending confirmation retains session without cleaning files");
        check((confirmation.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0,
                "pending confirmation gets NEW_TASK to survive receiver context");

        reset();
        RECEIVER.onReceive(CONTEXT, callback(PackageInstaller.STATUS_PENDING_USER_ACTION, 9));
        check(AppUpdater.confirmations == 0 && AppUpdater.abandonCalls == 1
                && AppUpdater.clearCalls == 1 && AppUpdater.lastSessionId == 9,
                "missing Android confirmation fails closed, abandons session and cache");
        check(Toast.shown == 1 && Toast.lastText.contains("did not ask"),
                "missing confirmation reports the failure");

        reset();
        RECEIVER.onReceive(CONTEXT, callback(PackageInstaller.STATUS_SUCCESS, 13));
        check(AppUpdater.clearCalls == 1 && AppUpdater.abandonCalls == 0
                && AppUpdater.confirmations == 0 && Toast.shown == 0,
                "success clears cache without erroneously abandoning session");

        reset();
        RECEIVER.onReceive(CONTEXT, callback(PackageInstaller.STATUS_FAILURE_ABORTED, 17));
        check(AppUpdater.clearCalls == 1 && AppUpdater.abandonCalls == 1
                && AppUpdater.lastSessionId == 17 && Toast.lastText.contains("cancelled"),
                "denied Android confirmation follows cancelled-session cleanup path");

        reset();
        RECEIVER.onReceive(CONTEXT, callback(PackageInstaller.STATUS_FAILURE, 19)
                .putExtra(PackageInstaller.EXTRA_STATUS_MESSAGE, "test denied"));
        check(AppUpdater.clearCalls == 1 && AppUpdater.abandonCalls == 1
                && Toast.lastText.contains("test denied"),
                "other platform failure reports and cleans the right session");

        reset();
        RECEIVER.onReceive(CONTEXT, new Intent().setAction(UpdateInstallReceiver.ACTION_STATUS));
        check(AppUpdater.clearCalls == 1 && AppUpdater.abandonCalls == 1
                && AppUpdater.lastSessionId == -1,
                "missing callback extras default to failure without inventing a session ID");

        // Characterization only: stale/duplicate ordering is NOT yet rejected by
        // production receiver. This test intentionally makes no stronger claim.
        reset();
        RECEIVER.onReceive(CONTEXT, callback(PackageInstaller.STATUS_FAILURE_ABORTED, 21));
        RECEIVER.onReceive(CONTEXT, callback(PackageInstaller.STATUS_FAILURE_ABORTED, 21));
        check(AppUpdater.abandonCalls == 2 && AppUpdater.clearCalls == 2,
                "duplicate terminal callbacks currently cause duplicate idempotent cleanup");

        System.out.println("UpdateInstallReceiverHostTest passed (" + assertions
                + " assertions; actual receiver with stubbed Android services)");
    }

    private static Intent callback(int status, int sessionId) {
        return new Intent().setAction(UpdateInstallReceiver.ACTION_STATUS)
                .putExtra(PackageInstaller.EXTRA_STATUS, status)
                .putExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId);
    }
    private static void reset() { AppUpdater.reset(); Toast.reset(); }
    private static boolean noEffects() {
        return AppUpdater.confirmations == 0 && AppUpdater.clearCalls == 0
                && AppUpdater.abandonCalls == 0 && Toast.shown == 0;
    }
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
