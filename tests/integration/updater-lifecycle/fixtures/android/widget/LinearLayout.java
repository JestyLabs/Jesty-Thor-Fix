package android.widget;
public class LinearLayout extends android.view.ViewGroup {
    public static volatile boolean failConstructor;
    public LinearLayout(android.content.Context context) {
        if (failConstructor) throw new IllegalStateException("HOST_LAYOUT_FAILURE");
    }
    public void setOrientation(int orientation) { }
    public void setPadding(int left, int top, int right, int bottom) { }
}
