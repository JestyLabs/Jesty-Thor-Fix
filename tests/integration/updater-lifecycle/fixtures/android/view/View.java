package android.view;
public class View {
    public interface OnClickListener { void onClick(View v); }
    public static volatile boolean failListener;
    public void setEnabled(boolean enabled) { }
    public void setOnClickListener(OnClickListener listener) {
        if (failListener) throw new IllegalStateException("HOST_BUTTON_FAILURE");
    }
}
