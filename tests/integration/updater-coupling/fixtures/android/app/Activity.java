package android.app;
import android.content.Context;
import android.content.Intent;
import java.io.File;
public class Activity extends Context {
    public int starts;
    public Intent last;
    public boolean rejectStart;
    public Activity(File cache) { super(cache); }
    public boolean isFinishing() { return false; }
    public boolean isDestroyed() { return false; }
    public void runOnUiThread(Runnable action) { action.run(); }
    public void startActivity(Intent intent) {
        if (rejectStart) throw new IllegalStateException("host confirmation launch rejected");
        starts++; last = intent;
    }
}
