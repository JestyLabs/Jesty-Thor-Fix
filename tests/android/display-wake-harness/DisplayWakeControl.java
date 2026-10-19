package com.thor.displaypowertest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class DisplayWakeControl extends BroadcastReceiver {
    public void onReceive(Context context, Intent intent) {
        if (DisplayWakeActivity.instance == null) throw new IllegalStateException("Start fixture activity first");
        DisplayWakeActivity.instance.arm();
    }
}
