package android.content;
/** Host-only Android placeholder; never packaged into the app. */
public abstract class BroadcastReceiver {
    public abstract void onReceive(Context context, Intent intent);
}
