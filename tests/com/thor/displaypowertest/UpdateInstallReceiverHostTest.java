package com.thor.displaypowertest;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.widget.Toast;

/** Production receiver + Android fake, asserting current-session cleanup only. */
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
        AppUpdater.trackSession(7);
        Intent confirmation = new Intent().setAction("android.intent.action.CONFIRM");
        RECEIVER.onReceive(CONTEXT, callback(PackageInstaller.STATUS_PENDING_USER_ACTION, 7)
                .putExtra(Intent.EXTRA_INTENT, confirmation));
        check(AppUpdater.confirmations == 1 && AppUpdater.lastConfirmation == confirmation,
                "valid pending callback dispatches confirmation");
        check(AppUpdater.lastSessionId == 7 && AppUpdater.abandonCalls == 0
                && AppUpdater.clearCalls == 0 && Toast.shown == 0,
                "pending session is not cleaned");
        check((confirmation.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0,
                "confirmation includes NEW_TASK flag");

        reset();
        AppUpdater.trackSession(9);
        RECEIVER.onReceive(CONTEXT, callback(PackageInstaller.STATUS_PENDING_USER_ACTION, 9));
        check(AppUpdater.confirmations == 0 && AppUpdater.abandonCalls == 1
                && AppUpdater.clearCalls == 1 && AppUpdater.lastSessionId == 9,
                "missing valid Android confirmation abandons only current session");
        check(Toast.shown == 1 && Toast.lastText.contains("did not ask"),
                "missing confirmation reports failure");

        reset();
        AppUpdater.trackSession(13);
        RECEIVER.onReceive(CONTEXT, callback(PackageInstaller.STATUS_SUCCESS, 13));
        check(AppUpdater.clearCalls == 1 && AppUpdater.abandonCalls == 0
                && AppUpdater.confirmations == 0 && Toast.shown == 0,
                "own successful session clears cache once");

        reset();
        AppUpdater.trackSession(17);
        RECEIVER.onReceive(CONTEXT, callback(PackageInstaller.STATUS_FAILURE_ABORTED, 17));
        check(AppUpdater.clearCalls == 1 && AppUpdater.abandonCalls == 1
                && AppUpdater.lastSessionId == 17 && Toast.lastText.contains("cancelled"),
                "valid aborted status is cleaned");

        reset();
        AppUpdater.trackSession(19);
        RECEIVER.onReceive(CONTEXT, callback(PackageInstaller.STATUS_FAILURE, 19)
                .putExtra(PackageInstaller.EXTRA_STATUS_MESSAGE, "test denied"));
        check(AppUpdater.clearCalls == 1 && AppUpdater.abandonCalls == 1
                && Toast.lastText.contains("test denied"),
                "valid failed status reports reason");

        reset();
        RECEIVER.onReceive(CONTEXT, new Intent().setAction(UpdateInstallReceiver.ACTION_STATUS));
        check(noEffects(), "unknown/missing session ID cannot remove arbitrary downloads");

        reset();
        AppUpdater.trackSession(21);
        RECEIVER.onReceive(CONTEXT, callback(PackageInstaller.STATUS_FAILURE_ABORTED, 21));
        RECEIVER.onReceive(CONTEXT, callback(PackageInstaller.STATUS_FAILURE_ABORTED, 21));
        check(AppUpdater.abandonCalls == 1 && AppUpdater.clearCalls == 1,
                "duplicate terminal status cleans once, not twice");

        reset();
        AppUpdater.trackSession(31);
        RECEIVER.onReceive(CONTEXT, callback(PackageInstaller.STATUS_PENDING_USER_ACTION, 31)
                .putExtra(Intent.EXTRA_INTENT, new Intent()));
        RECEIVER.onReceive(CONTEXT, callback(PackageInstaller.STATUS_FAILURE_ABORTED, 31));
        check(AppUpdater.lastConfirmation == null && AppUpdater.clearCalls == 1,
                "aborting a pending confirmation invalidates the matching prompt");

        reset();
        AppUpdater.trackSession(11);
        RECEIVER.onReceive(CONTEXT, callback(PackageInstaller.STATUS_FAILURE_ABORTED, 9));
        check(AppUpdater.clearCalls == 0 && AppUpdater.abandonCalls == 0
                && AppUpdater.isActiveInstallSession(11),
                "late session-9 abort cannot remove session 11's download");
        RECEIVER.onReceive(CONTEXT, callback(PackageInstaller.STATUS_PENDING_USER_ACTION, 11)
                .putExtra(Intent.EXTRA_INTENT, new Intent()));
        check(AppUpdater.confirmations == 1 && AppUpdater.isActiveInstallSession(11),
                "matching newer pending callback remains valid");
        RECEIVER.onReceive(CONTEXT, callback(PackageInstaller.STATUS_FAILURE_ABORTED, 11));
        check(AppUpdater.clearCalls == 1 && AppUpdater.abandonCalls == 1,
                "only matching newer terminal status performs cleanup");

        System.out.println("UpdateInstallReceiverHostTest passed (" + assertions
                + " assertions; actual receiver and stubbed Android services)");
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
