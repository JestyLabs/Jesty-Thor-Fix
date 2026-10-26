package android.view;

/** Host fixture only: the action constants match the Android framework values. */
public final class MotionEvent extends InputEvent {
    public static final int ACTION_DOWN = 0;
    public static final int ACTION_UP = 1;
    public static final int ACTION_MOVE = 2;
    public static final int ACTION_CANCEL = 3;
    public static final int ACTION_POINTER_DOWN = 5;

    private final int action;

    public MotionEvent(int displayId, int action) {
        super(displayId);
        this.action = action;
    }

    public int getActionMasked() { return action; }
}
