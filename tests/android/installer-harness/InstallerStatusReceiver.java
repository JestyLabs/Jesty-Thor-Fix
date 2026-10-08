package org.jestylabs.thorfix.installerharness;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

/** The real PackageInstaller callback; the intent sender is explicit and private. */
public final class InstallerStatusReceiver extends BroadcastReceiver {
    private static volatile Intent confirmationIntent;

    static Intent takeConfirmation() {
        Intent result = confirmationIntent;
        confirmationIntent = null;
        return result;
    }

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !HarnessActivity.ACTION_STATUS.equals(intent.getAction())) return;

        int expected = intent.getIntExtra("expected_session", -1);
        int session = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1);
        if (expected <= 0 || session != expected) {
            context.getSharedPreferences(HarnessActivity.PREFS, Context.MODE_PRIVATE).edit()
                    .putString("callback_result", "SESSION_MISMATCH").commit();
            return;
        }
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE);
        String result;
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            confirmationIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            result = confirmationIntent == null ? "PENDING_WITHOUT_INTENT" : "PENDING_USER_ACTION";
        } else if (status == PackageInstaller.STATUS_FAILURE_ABORTED) {
            result = "USER_ABORTED";
        } else if (status == PackageInstaller.STATUS_SUCCESS) {
            result = "INSTALLED";
        } else {
            result = "FAILURE_STATUS_" + status;
        }
        context.getSharedPreferences(HarnessActivity.PREFS, Context.MODE_PRIVATE).edit()
                .putString("callback_result", result).commit();
    }
}
