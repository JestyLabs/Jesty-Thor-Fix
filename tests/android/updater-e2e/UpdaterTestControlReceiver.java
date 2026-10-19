package com.thor.displaypowertest;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Disposable test APK only: replay callbacks without changing Activity visibility. */
public final class UpdaterTestControlReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        try {
            UpdaterTestActivity.replayPending(context, intent.getIntExtra("session", -1));
        } catch (Exception failure) {
            android.util.Log.e("UpdaterE2E", "TEST_ERROR", failure);
        }
    }
}
