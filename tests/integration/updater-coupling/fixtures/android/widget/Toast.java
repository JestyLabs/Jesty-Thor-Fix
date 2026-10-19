package android.widget;
import android.content.Context;
public final class Toast {
    public static final int LENGTH_SHORT=0,LENGTH_LONG=1;
    public static String lastText;
    public static int shown;
    private final String text;
    private Toast(String value) { text=value; }
    public static Toast makeText(Context context,CharSequence text,int duration) {
        return new Toast(text.toString());
    }
    public void show() { lastText=text; shown++; }
    public static void reset() { lastText=null; shown=0; }
}
