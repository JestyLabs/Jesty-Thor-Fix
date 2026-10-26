package android.view;

/** Host fixture only: carries the logical display that produced the event. */
public abstract class InputEvent {
    private final int displayId;

    protected InputEvent(int displayId) { this.displayId = displayId; }

    public int getDisplayId() { return displayId; }
}
