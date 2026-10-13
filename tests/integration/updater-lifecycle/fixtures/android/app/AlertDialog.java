package android.app;
public class AlertDialog {
    public static volatile boolean failShow, failDismiss;
    public static volatile AlertDialog last;
    private boolean showing;
    private final android.widget.Button button;
    public AlertDialog(android.content.Context context) { button = new android.widget.Button(context); }
    public boolean isShowing() { return showing; }
    public void dismiss() { if(failDismiss) throw new IllegalStateException("HOST_DISMISS_FAILURE"); showing = false; }
    public android.widget.Button getButton(int which) { return button; }
    public static class Builder {
        private final android.content.Context context;
        public Builder(android.content.Context context, int theme) { this.context = context; }
        public Builder setTitle(CharSequence title) { return this; }
        public Builder setMessage(CharSequence message) { return this; }
        public Builder setView(android.view.View view) { return this; }
        public Builder setCancelable(boolean cancelable) { return this; }
        public Builder setNegativeButton(CharSequence label, android.content.DialogInterface.OnClickListener listener) { return this; }
        public Builder setPositiveButton(CharSequence label, android.content.DialogInterface.OnClickListener listener) { return this; }
        public AlertDialog show() {
            if (failShow) throw new IllegalStateException("HOST_DIALOG_FAILURE");
            AlertDialog result = new AlertDialog(context); result.showing = true; last = result; return result;
        }
    }
}
