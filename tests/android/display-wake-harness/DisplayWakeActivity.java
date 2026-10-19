package com.thor.displaypowertest;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.widget.TextView;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Real Android power broadcasts driving the production coordinator, with synthetic panels. */
public final class DisplayWakeActivity extends Activity {
    static DisplayWakeActivity instance;
    private final Handler handler = new Handler();
    private JSONArray events = new JSONArray();
    private PowerManager power;
    private int epoch;
    private boolean armed, offSeen, onSeen, finished;
    private String pendingProperty = "?";
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (armed && Intent.ACTION_SCREEN_OFF.equals(action)) {
                offSeen = true;
                TestDisplayState.sleep();
            } else if (armed && offSeen && Intent.ACTION_SCREEN_ON.equals(action)) {
                onSeen = true;
                TestDisplayState.wakeTop();
                pendingProperty = TestDisplayState.property;
                DisplayActionCoordinator.noteDisplayOn();
                final int wakeEpoch = epoch;
                handler.postDelayed(() -> {
                    if (wakeEpoch != epoch) return;
                    TestDisplayState.both();
                    record("BOTH_CANCEL");
                }, 200L);
                handler.postDelayed(() -> {
                    if (wakeEpoch != epoch) return;
                    DisplayActionCoordinator.onWatcherSample(TestDisplayState.mode);
                    DisplayActionCoordinator.noteDisplayOn();
                    finished = true;
                    record("SETTLED");
                }, 1200L);
            }
            record(action);
        }
    };
    public void onCreate(Bundle saved) {
        super.onCreate(saved);
        instance = this;
        power = (PowerManager) getSystemService(POWER_SERVICE);
        TextView label = new TextView(this);
        label.setText("Disposable display wake fixture; synthetic panels");
        setContentView(label);
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        registerReceiver(receiver, filter);
        arm();
    }
    void arm() {
        epoch++;
        TestDisplayState.baseline();
        events = new JSONArray();
        offSeen = false; onSeen = false; finished = false; armed = true;
        pendingProperty = "?";
        record("ARMED");
    }
    private void record(String action) {
        try {
            JSONObject event = new JSONObject();
            event.put("event", action);
            event.put("interactive", power.isInteractive());
            event.put("batterySaver", power.isPowerSaveMode());
            event.put("elapsed", android.os.SystemClock.elapsedRealtime());
            events.put(event);
            JSONObject state = new JSONObject();
            state.put("events", events);
            state.put("offSeen", offSeen); state.put("onSeen", onSeen);
            state.put("finished", finished); state.put("pendingProperty", pendingProperty);
            state.put("property", TestDisplayState.property);
            state.put("propertyWrites", TestDisplayState.propertyWrites);
            state.put("hardwareCalls", TestDisplayState.hardwareCalls);
            state.put("pending", WakeRepairScheduler.isPending());
            try (FileOutputStream output = new FileOutputStream(new File(getFilesDir(), "result.json"))) {
                output.write(state.toString(2).getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception error) { throw new IllegalStateException(error); }
    }
    protected void onDestroy() {
        unregisterReceiver(receiver);
        instance = null;
        super.onDestroy();
    }
}
