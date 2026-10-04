package com.thor.displaypowertest;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Pure release, download and install-readiness rules for the in-app updater. */
public final class UpdateVersion {
    public static final String ASSET_PREFIX = "Jesty-Thor-Fix-";
    public static final long CHECK_INTERVAL_MS = 60L * 60L * 1000L;
    /** Telemetry polls every second with a 2-second request timeout. */
    public static final long MAX_SAMPLE_AGE_MS = 3500L;
    private static final int MAX_SUMMARY_CHARS = 600;

    /** What the dashboard last learned from the daemon, if anything. */
    public enum DeviceState { UNKNOWN, UNAVAILABLE, RESPONDING }

    /** One GitHub release reduced to the fields the selection rules need. */
    public static final class Candidate {
        public final String tag;
        public final boolean draft;
        public final boolean prerelease;
        public final boolean hasVerifiableApk;

        public Candidate(String tag, boolean draft, boolean prerelease, boolean hasVerifiableApk) {
            this.tag = tag;
            this.draft = draft;
            this.prerelease = prerelease;
            this.hasVerifiableApk = hasVerifiableApk;
        }
    }

    private UpdateVersion() {}

    /** Numeric parts of "v1.5.16" or "1.5.16-rc1", or null when the tag is not a version. */
    public static int[] parse(String version) {
        if (version == null) return null;
        String core = stripV(version.trim());
        int suffix = indexOfAny(core, '-', '+');
        if (suffix >= 0) core = core.substring(0, suffix);
        if (core.isEmpty()) return null;
        String[] parts = core.split("\\.", -1);
        int[] numbers = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty() || parts[i].length() > 6) return null;
            for (int c = 0; c < parts[i].length(); c++) {
                char digit = parts[i].charAt(c);
                if (digit < '0' || digit > '9') return null;
            }
            numbers[i] = Integer.parseInt(parts[i]);
        }
        return numbers;
    }

    public static String stripV(String tag) {
        if (tag == null) return "";
        return tag.startsWith("v") || tag.startsWith("V") ? tag.substring(1) : tag;
    }

    public static boolean hasSuffix(String version) {
        return version != null && indexOfAny(version, '-', '+') >= 0;
    }

    /** Comparator semantics; unparseable versions sort lowest, a suffix sorts before its base. */
    public static int compare(String left, String right) {
        int[] a = parse(left);
        int[] b = parse(right);
        if (a == null || b == null) return (a == null ? 0 : 1) - (b == null ? 0 : 1);
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) return x < y ? -1 : 1;
        }
        boolean suffixA = hasSuffix(left);
        boolean suffixB = hasSuffix(right);
        if (suffixA == suffixB) return 0;
        return suffixA ? -1 : 1;
    }

    public static boolean isNewer(String candidate, String installed) {
        return parse(candidate) != null && compare(candidate, installed) > 0;
    }

    /**
     * Picks the newest installable release above the installed version. Drafts are
     * never used; GitHub pre-releases only when the user opted into test builds.
     * Returns the index in {@code releases} or -1.
     */
    public static int best(List<Candidate> releases, String installed, boolean includePrereleases) {
        int chosen = -1;
        for (int i = 0; i < releases.size(); i++) {
            Candidate release = releases.get(i);
            if (release == null || release.draft || !release.hasVerifiableApk) continue;
            if (release.prerelease && !includePrereleases) continue;
            if (!isNewer(release.tag, installed)) continue;
            if (chosen < 0 || compare(release.tag, releases.get(chosen).tag) > 0) chosen = i;
        }
        return chosen;
    }

    /** The only asset name accepted for a tag, e.g. Jesty-Thor-Fix-1.5.17.apk for v1.5.17. */
    public static String expectedAssetName(String tag) {
        return ASSET_PREFIX + stripV(tag) + ".apk";
    }

    public static boolean isExpectedAsset(String name, String tag) {
        return name != null && parse(tag) != null && name.equals(expectedAssetName(tag));
    }

    /** Converts GitHub's "sha256:<hex>" asset digest to lowercase hex, or null. */
    public static String sha256FromDigest(String digest) {
        if (digest == null || !digest.regionMatches(true, 0, "sha256:", 0, 7)) return null;
        String hex = digest.substring(7).trim().toLowerCase(Locale.US);
        if (hex.length() != 64) return null;
        for (int i = 0; i < hex.length(); i++) {
            char c = hex.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) return null;
        }
        return hex;
    }

    /** Only this repository's own asset for this exact tag is downloaded. */
    public static boolean isTrustedDownloadUrl(String url, String repository, String tag,
            String assetName) {
        if (url == null || tag == null || tag.isEmpty() || assetName == null) return false;
        String expected = "https://github.com/" + repository + "/releases/download/"
                + tag + "/" + assetName;
        return url.equals(expected) && assetName.indexOf('/') < 0 && !assetName.contains("..");
    }

    /** True when the last check is old enough, or the wall clock moved backwards. */
    public static boolean shouldCheck(long nowMs, long lastCheckMs, long intervalMs) {
        return lastCheckMs <= 0L || nowMs < lastCheckMs || nowMs - lastCheckMs >= intervalMs;
    }

    /**
     * Returns why an install must wait, or null. The package replacement must not
     * race the daemon's boot coordinator or an in-flight display/CPU command, and
     * needs a recent dashboard sample. A boot hold seen earlier blocks even if the
     * daemon stops answering; an unavailable daemon otherwise does not block,
     * because the update may be the repair.
     */
    public static String installBlocker(DeviceState state, long sampleAgeMs, boolean bootHeld,
            boolean commandInFlight) {
        if (commandInFlight) return "Wait for the current change to finish, then try again.";
        if (bootHeld) {
            return "The boot transition is still running. Update after BOOT READY,"
                    + " when the display controls are available.";
        }
        if (state == null || state == DeviceState.UNKNOWN
                || sampleAgeMs < 0L || sampleAgeMs > MAX_SAMPLE_AGE_MS) {
            return "Keep Jesty Thor Fix open while it reads the Thor's state, then try again.";
        }
        return null;
    }

    /** Explains how the running background service is replaced after the update. */
    public static String handoverNote(DeviceState state, boolean bothActive) {
        if (state == DeviceState.RESPONDING && bothActive) {
            return "After Android installs it, open Jesty Thor Fix again. The new background"
                    + " service takes over roughly 30 seconds later.";
        }
        if (state == DeviceState.RESPONDING) {
            return "Both screens are not on (BOTH). The current background service keeps"
                    + " running and is replaced only when you open the app in BOTH, or at"
                    + " the next reboot. Switching to BOTH first is recommended.";
        }
        if (state == DeviceState.UNAVAILABLE) {
            return "The background service is not responding. After the update, open the"
                    + " app again; if it stays unavailable, reboot the Thor.";
        }
        return "After Android installs it, open Jesty Thor Fix again. The new background"
                + " service replaces the current one when both screens are on (BOTH),"
                + " or at the next reboot.";
    }

    /** Keeps the dialog short: first section of the notes, no Markdown markup, capped. */
    public static String summarize(String notes) {
        if (notes == null) return "";
        StringBuilder text = new StringBuilder();
        for (String line : notes.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                if (text.length() > 0) break;
                continue;
            }
            if (trimmed.isEmpty() && text.length() == 0) continue;
            if (trimmed.startsWith("<") || trimmed.startsWith("![")) continue;
            text.append(trimmed.replace("`", "").replace("**", "")).append('\n');
        }
        String summary = text.toString().trim();
        return summary.length() > MAX_SUMMARY_CHARS
                ? summary.substring(0, MAX_SUMMARY_CHARS).trim() + "..." : summary;
    }

    /**
     * Installed multi-signer: the archive must have exactly the same signer set.
     * Installed single signer: {@code installedSigners} is its current certificate
     * and {@code archiveSigners} the archive's rotation lineage, which must contain it.
     */
    public static boolean signersCompatible(List<byte[]> installedSigners,
            boolean installedMultiple, List<byte[]> archiveSigners, boolean archiveMultiple) {
        if (installedSigners == null || archiveSigners == null
                || installedSigners.isEmpty() || archiveSigners.isEmpty()) return false;
        if (installedMultiple || archiveMultiple) {
            if (installedMultiple != archiveMultiple) return false;
            return containsAll(installedSigners, archiveSigners)
                    && containsAll(archiveSigners, installedSigners);
        }
        return installedSigners.size() == 1 && contains(archiveSigners, installedSigners.get(0));
    }

    private static boolean containsAll(List<byte[]> haystack, List<byte[]> needles) {
        for (byte[] needle : needles) {
            if (!contains(haystack, needle)) return false;
        }
        return true;
    }

    private static boolean contains(List<byte[]> haystack, byte[] needle) {
        if (needle == null) return false;
        for (byte[] item : haystack) {
            if (Arrays.equals(item, needle)) return true;
        }
        return false;
    }

    private static int indexOfAny(String value, char first, char second) {
        int a = value.indexOf(first);
        int b = value.indexOf(second);
        if (a < 0) return b;
        if (b < 0) return a;
        return Math.min(a, b);
    }
}
