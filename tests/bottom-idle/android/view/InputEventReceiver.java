package android.view;

import android.os.Looper;

/** Host fixture only: the test delivers events by calling onInputEvent directly. */
public abstract class InputEventReceiver {
    public static volatile InputEventReceiver latest;
    public volatile boolean disposed;
    public int finished;

    public InputEventReceiver(InputChannel channel, Looper looper) {
        if (channel == null || looper == null) throw new IllegalArgumentException("channel/looper");
        latest = this;
    }

    public void onInputEvent(InputEvent event) { finishInputEvent(event, false); }

    public final void finishInputEvent(InputEvent event, boolean handled) {
        if (handled) throw new AssertionError("passive monitor must not consume input");
        finished++;
    }

    public void dispose() { disposed = true; }
}
