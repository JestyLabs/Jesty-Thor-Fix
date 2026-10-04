package com.thor.displaypowertest;

/**
 * Pure choice of the lid input device. The named Thor device wins. Only when
 * no device has that name may a device be chosen by capability, and only if
 * exactly one input device reports SW_LID; anything ambiguous is unavailable,
 * so Wake Guard stays off rather than reading the wrong switch.
 */
public final class HallNodeModel {
    private HallNodeModel() {}

    /** Index into the parallel arrays, or -1 when no single safe choice exists. */
    public static int choose(String[] names, String[] switchCapabilities) {
        if (names == null || switchCapabilities == null
                || names.length != switchCapabilities.length) return -1;
        int named = -1;
        int namedCount = 0;
        int lid = -1;
        int lidCount = 0;
        for (int i = 0; i < names.length; i++) {
            if (ThorHardwareProfile.HALL_DEVICE_NAME.equals(names[i])) {
                named = i;
                namedCount++;
            }
            if (hasSwLid(switchCapabilities[i])) {
                lid = i;
                lidCount++;
            }
        }
        if (namedCount > 0) return namedCount == 1 ? named : -1;
        return lidCount == 1 ? lid : -1;
    }

    /**
     * sysfs capabilities/sw is a space-separated hex bitmap, most significant
     * word first. SW_LID is bit 0 of the last word. Malformed text is false.
     */
    public static boolean hasSwLid(String bitmap) {
        if (bitmap == null) return false;
        String trimmed = bitmap.trim();
        if (trimmed.isEmpty() || !trimmed.matches("[0-9a-fA-F]{1,16}( [0-9a-fA-F]{1,16})*")) {
            return false;
        }
        String last = trimmed.substring(trimmed.lastIndexOf(' ') + 1);
        char lowest = last.charAt(last.length() - 1);
        return (Character.digit(lowest, 16) & 1) == 1;
    }
}
