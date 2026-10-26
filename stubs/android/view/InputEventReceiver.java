package android.view;

import android.os.Looper;

/** Compile-time only; Android 13 owns the real receiver implementation. */
public abstract class InputEventReceiver {
    public InputEventReceiver(InputChannel channel, Looper looper) {}
    public void onInputEvent(InputEvent event) {}
    public final void finishInputEvent(InputEvent event, boolean handled) {}
    public void dispose() {}
}
