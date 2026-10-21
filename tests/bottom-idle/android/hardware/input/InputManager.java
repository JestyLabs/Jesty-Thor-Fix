package android.hardware.input;

import android.view.InputMonitor;

/** Host fixture only: hidden gesture monitor API with a scripted availability flag. */
public final class InputManager {
    private static final InputManager INSTANCE = new InputManager();
    public static volatile boolean available = true;
    public static int monitors;
    public static int lastDisplayId = -1;

    private InputManager() {}

    private static InputManager getInstance() { return INSTANCE; }

    private InputMonitor monitorGestureInput(String name, int displayId) {
        if (!available) throw new IllegalStateException("monitor unavailable");
        monitors++;
        lastDisplayId = displayId;
        return new InputMonitor();
    }
}
