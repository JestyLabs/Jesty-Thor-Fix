package com.thor.displaypowertest;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.util.Log;
import android.widget.Toast;

/** Receives PackageInstaller results for in-app updates (not exported). */
public final class UpdateInstallReceiver extends BroadcastReceiver {
    static final String ACTION_STATUS = "com.thor.displaypowertest.UPDATE_INSTALL_STATUS";
    private static final String TAG = "ThorDisplayUpdate";

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_STATUS.equals(intent.getAction())) return;
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE);
        int sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1);
        switch (status) {
            case PackageInstaller.STATUS_PENDING_USER_ACTION: {
                Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm == null) {
                    fail(context, sessionId, "Update failed: Android did not ask for confirmation");
                    break;
                }
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                AppUpdater.deliverConfirmation(context, confirm, sessionId);
                break;
            }
            case PackageInstaller.STATUS_SUCCESS:
                // Normally not delivered: Android stops this process to replace the APK.
                AppUpdater.clearDownloads(context);
                break;
            case PackageInstaller.STATUS_FAILURE_ABORTED:
                fail(context, sessionId, "Update cancelled");
                break;
            default: {
                String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                Log.w(TAG, "update install failed status=" + status + " message=" + message);
                fail(context, sessionId, "Update failed" + (message == null ? "" : ": " + message));
                break;
            }
        }
    }

    private static void fail(Context context, int sessionId, String message) {
        AppUpdater.abandonSession(context, sessionId);
        AppUpdater.clearDownloads(context);
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }
}
