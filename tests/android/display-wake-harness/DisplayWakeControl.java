package com.thor.displaypowertest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class DisplayWakeControl extends BroadcastReceiver {
    public void onReceive(Context context, Intent intent) {
        if (DisplayWakeActivity.instance == null) throw new IllegalStateException("Start fixture activity first");
        if (intent.getBooleanExtra("backoff", false))
            DisplayWakeActivity.instance.armBackoff(intent.getBooleanExtra("fail_call", true));
        else DisplayWakeActivity.instance.arm();
    }
}
