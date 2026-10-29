package com.thor.displaypowertest;

/**
 * Pure classifier for kernel log records that mark a lower DSI link which will not
 * start again before reboot. Every signature was seen only in the poisoned boots of
 * 2026-10-19, 2026-10-20 and the 2026-10-29 reproduction, and in none of the retained
 * healthy kernel rings (including at least 12 lower-panel starts).
 */
public final class KernelSignalModel {
    /** Panel-RAM bypass write with the lower panel off (the trigger itself). */
    public static final String PRECURSOR = "KMSG_PRECURSOR";
    /** First lower start after the trigger: its command packets time out. */
    public static final String INIT_FAIL = "KMSG_INIT_FAIL";
    /** Later lower starts: commit timeouts that drive the compositor reset loop. */
    public static final String COMMIT_110 = "KMSG_COMMIT_110";

    private KernelSignalModel() {}

    /** A /dev/kmsg record ("prio,seq,usec,flags;text" plus continuation lines) or a dmesg line. */
    public static String classify(String record) {
        String text = message(record);
        if (text == null) return null;
        if (text.matches(".*\\bDSI_PLL_\\d+: lock failed.*")
                || text.contains("failed to send DSI_CMD_SET_VID_BYPASS_RAM")) return PRECURSOR;
        if (text.contains("dsi-ctrl-0: Command transfer failed")) return INIT_FAIL;
        if (text.contains("crtc:" + ThorHardwareProfile.BOTTOM_CRTC_ID + ",")
                && text.contains("wait for commit done failed ret:-110")) return COMMIT_110;
        return null;
    }

    /** The record's first text line without the kmsg header. */
    static String message(String record) {
        if (record == null) return null;
        String line = record;
        int newline = line.indexOf('\n');
        if (newline >= 0) line = line.substring(0, newline);
        int semicolon = line.indexOf(';');
        if (semicolon > 0 && line.substring(0, semicolon).matches("\\d+,\\d+,\\d+,[^;]*")) {
            line = line.substring(semicolon + 1);
        }
        return line;
    }

    /** A short, log-safe excerpt of the matching text. */
    public static String excerpt(String record) {
        String text = message(record);
        if (text == null) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length() && out.length() < 120; i++) {
            char c = text.charAt(i);
            out.append(Character.isLetterOrDigit(c) || " _:.,-".indexOf(c) >= 0 ? c : '?');
        }
        return out.toString().trim().replace(' ', '_');
    }
}
