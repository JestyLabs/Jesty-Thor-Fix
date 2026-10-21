package android.view;

/** Host fixture only: records disposal of the gesture monitor. */
public final class InputMonitor {
    public static int disposed;
    private final InputChannel channel = new InputChannel();

    public InputChannel getInputChannel() { return channel; }

    public void dispose() { disposed++; }
}
