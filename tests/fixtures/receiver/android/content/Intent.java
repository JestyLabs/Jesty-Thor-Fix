package android.content;

import java.util.HashMap;
import java.util.Map;

/** Minimal typed Intent used only by the host receiver callback test. */
public final class Intent {
    public static final String EXTRA_INTENT = "android.intent.extra.INTENT";
    private final Map<String, Object> extras = new HashMap<>();
    private String action;
    public Intent setAction(String value) { action = value; return this; }
    public String getAction() { return action; }
    public Intent putExtra(String name, int value) { extras.put(name, value); return this; }
    public Intent putExtra(String name, String value) { extras.put(name, value); return this; }
    public Intent putExtra(String name, Intent value) { extras.put(name, value); return this; }
    public int getIntExtra(String name, int fallback) {
        Object value = extras.get(name);
        return value instanceof Integer ? ((Integer) value).intValue() : fallback;
    }
    public String getStringExtra(String name) {
        Object value = extras.get(name);
        return value instanceof String ? (String) value : null;
    }
    @SuppressWarnings("unchecked")
    public <T> T getParcelableExtra(String name) { return (T) extras.get(name); }
    public static final int FLAG_ACTIVITY_NEW_TASK = 0x10000000;
    private int flags;
    public Intent addFlags(int value) { flags |= value; return this; }
    public int getFlags() { return flags; }
}
