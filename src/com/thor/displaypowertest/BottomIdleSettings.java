package com.thor.displaypowertest;

/** Closed, bounded choices shared by the slider, Quick Settings and IPC. */
public final class BottomIdleSettings {
    private static final int[] MINUTES = {1, 2, 3, 5, 10, 15, 20, 30, 45, 60};
    private static final String COMMANDS = "abcdefghij";
    public static final int DEFAULT_INDEX = 3;
    public static final char DISABLE_COMMAND = 'z';

    private BottomIdleSettings() {}

    public static int count() { return MINUTES.length; }
    public static int minutesAt(int index) {
        if (index < 0 || index >= MINUTES.length) throw new IllegalArgumentException("index");
        return MINUTES[index];
    }
    public static int indexOf(int minutes) {
        for (int i = 0; i < MINUTES.length; i++) if (MINUTES[i] == minutes) return i;
        return DEFAULT_INDEX;
    }
    public static boolean validMinutes(int minutes) {
        return minutesAt(indexOf(minutes)) == minutes;
    }
    public static char commandFor(int minutes) {
        if (!validMinutes(minutes)) throw new IllegalArgumentException("minutes");
        return COMMANDS.charAt(indexOf(minutes));
    }
    /** Zero means disabled. Negative means an unknown command. */
    public static int minutesFor(char command) {
        if (command == DISABLE_COMMAND) return 0;
        int index = COMMANDS.indexOf(command);
        return index < 0 ? -1 : MINUTES[index];
    }
}
