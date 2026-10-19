package android.widget;
import android.content.Context;
public final class Toast {
    public static final int LENGTH_SHORT=0,LENGTH_LONG=1;
    public static String lastText;
    public static int shown;
    public static boolean failShow;
    public static Runnable showHook;
    private final String text;
    private Toast(String value) { text=value; }
    public static Toast makeText(Context context,CharSequence text,int duration) {
        return new Toast(text.toString());
    }
    public void show() { if(showHook!=null) showHook.run(); if(failShow) throw new IllegalStateException("HOST_TOAST_FAILURE"); lastText=text; shown++; }
    public static void reset() { lastText=null; shown=0; failShow=false; showHook=null; }
}
