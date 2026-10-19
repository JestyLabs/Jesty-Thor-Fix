package android.app;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
public class PendingIntent {
    public static final int FLAG_UPDATE_CURRENT=134217728, FLAG_MUTABLE=33554432;
    public static PendingIntent getBroadcast(Context context,int id,Intent intent,int flags) { return new PendingIntent(); }
    public IntentSender getIntentSender() { return new IntentSender(); }
}
