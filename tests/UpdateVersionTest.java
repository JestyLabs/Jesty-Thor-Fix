import com.thor.displaypowertest.UpdateVersion;
import com.thor.displaypowertest.UpdateVersion.Candidate;
import com.thor.displaypowertest.UpdateVersion.DeviceState;

import java.util.Arrays;
import java.util.List;

public final class UpdateVersionTest {
    private static final String REPO = "JestyLabs/Jesty-Thor-Fix";

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        check(UpdateVersion.isNewer("v1.5.17", "1.5.16"), "patch bump");
        check(UpdateVersion.isNewer("v1.6.0", "1.5.16"), "minor bump");
        check(UpdateVersion.isNewer("v1.10.0", "1.9.9"), "numeric, not lexical");
        check(UpdateVersion.isNewer("v2", "1.9.9"), "short version");
        check(!UpdateVersion.isNewer("v1.5.16", "1.5.16"), "same version");
        check(!UpdateVersion.isNewer("v1.5.16.0", "1.5.16"), "trailing zero");
        check(!UpdateVersion.isNewer("v1.3.0", "1.5.16"), "older version");
        check(!UpdateVersion.isNewer("v1.5.16-rc1", "1.5.16"), "suffix before stable");
        check(UpdateVersion.isNewer("v1.5.16", "1.5.16-rc1"), "stable after suffix");
        check(!UpdateVersion.isNewer("latest", "1.5.16"), "non-version tag");
        check(!UpdateVersion.isNewer("v1..5", "1.5.16"), "empty part");
        check(!UpdateVersion.isNewer("v1.\u0665.0", "1.4.0"), "non-ASCII digit");
        check(!UpdateVersion.isNewer(null, "1.5.16"), "null tag");

        List<Candidate> releases = Arrays.asList(
                new Candidate("v1.5.18", true, false, true),
                new Candidate("v1.5.17", false, true, true),
                new Candidate("v1.5.16", false, false, true),
                new Candidate("v1.5.19", false, false, false),
                new Candidate("v1.3.0", false, false, true));
        check(UpdateVersion.best(releases, "1.5.15", false) == 2, "stable channel ignores test builds");
        check(UpdateVersion.best(releases, "1.5.15", true) == 1, "test channel takes newer pre-release");
        check(UpdateVersion.best(releases, "1.5.17", true) == -1, "draft and unverifiable never chosen");
        check(UpdateVersion.best(releases, "1.5.16", false) == -1, "installed stable is current");

        check("Jesty-Thor-Fix-1.5.17.apk".equals(UpdateVersion.expectedAssetName("v1.5.17")),
                "asset name");
        check(UpdateVersion.isExpectedAsset("Jesty-Thor-Fix-1.5.17.apk", "v1.5.17"), "exact asset");
        check(!UpdateVersion.isExpectedAsset("Jesty-Thor-Fix-1.5.17-unsigned.apk", "v1.5.17"),
                "unsigned asset");
        check(!UpdateVersion.isExpectedAsset("Jesty-Thor-Fix-1.5.16.apk", "v1.5.17"),
                "asset of another version");
        check(!UpdateVersion.isExpectedAsset("SHA256SUMS-1.5.17.txt", "v1.5.17"), "checksum file");

        String hex = "093b6af26e86e072343988c04cbf03256005703d567177e99d308b9badc71f2b";
        check(hex.equals(UpdateVersion.sha256FromDigest("sha256:" + hex)), "digest");
        check(hex.equals(UpdateVersion.sha256FromDigest("SHA256:" + hex.toUpperCase())),
                "digest case");
        check(UpdateVersion.sha256FromDigest("sha1:" + hex) == null, "wrong algorithm");
        check(UpdateVersion.sha256FromDigest("sha256:abc") == null, "short digest");
        check(UpdateVersion.sha256FromDigest(null) == null, "missing digest");

        String asset = "Jesty-Thor-Fix-1.5.17.apk";
        String base = "https://github.com/" + REPO + "/releases/download/v1.5.17/";
        check(UpdateVersion.isTrustedDownloadUrl(base + asset, REPO, "v1.5.17", asset),
                "own release asset");
        check(!UpdateVersion.isTrustedDownloadUrl(base + asset, REPO, "v1.5.16", asset),
                "other tag");
        check(!UpdateVersion.isTrustedDownloadUrl(base + "../x/" + asset, REPO, "v1.5.17", asset),
                "path traversal");
        check(!UpdateVersion.isTrustedDownloadUrl(base + asset + "?x=1", REPO, "v1.5.17", asset),
                "query string");
        check(!UpdateVersion.isTrustedDownloadUrl(
                "https://github.com/evil/repo/releases/download/v1.5.17/" + asset,
                REPO, "v1.5.17", asset), "other repository");
        check(!UpdateVersion.isTrustedDownloadUrl("http://github.com/" + REPO
                + "/releases/download/v1.5.17/" + asset, REPO, "v1.5.17", asset), "plain http");

        long hour = UpdateVersion.CHECK_INTERVAL_MS;
        check(UpdateVersion.shouldCheck(10L * hour, 0L, hour), "never checked");
        check(!UpdateVersion.shouldCheck(10L * hour, 10L * hour - 1L, hour), "checked recently");
        check(UpdateVersion.shouldCheck(10L * hour, 9L * hour, hour), "interval elapsed");
        check(UpdateVersion.shouldCheck(5L * hour, 9L * hour, hour), "clock moved backwards");

        long fresh = 900L;
        long stale = UpdateVersion.MAX_SAMPLE_AGE_MS + 1L;
        check(UpdateVersion.installBlocker(DeviceState.UNKNOWN, fresh, false, false) != null,
                "unknown state is not safe");
        check(UpdateVersion.installBlocker(DeviceState.RESPONDING, stale, false, false) != null,
                "stale sample is not safe");
        check(UpdateVersion.installBlocker(DeviceState.RESPONDING, -1L, false, false) != null,
                "sample from the future is not safe");
        check(UpdateVersion.installBlocker(DeviceState.RESPONDING, fresh, true, false) != null,
                "boot coordinator holds the update");
        check(UpdateVersion.installBlocker(DeviceState.UNAVAILABLE, fresh, true, false) != null,
                "a known boot hold survives a failed poll");
        check(UpdateVersion.installBlocker(DeviceState.RESPONDING, fresh, false, true) != null,
                "command in flight holds the update");
        check(UpdateVersion.installBlocker(DeviceState.UNAVAILABLE, fresh, false, true) != null,
                "command in flight holds even without daemon");
        check(UpdateVersion.installBlocker(DeviceState.RESPONDING, fresh, false, false) == null,
                "ready daemon allows the update");
        check(UpdateVersion.installBlocker(DeviceState.UNAVAILABLE, fresh, false, false) == null,
                "an unavailable daemon without a boot hold does not block a repair update");

        byte[] a = {1, 2, 3};
        byte[] b = {4, 5, 6};
        byte[] c = {7, 8, 9};
        check(UpdateVersion.signersCompatible(Arrays.asList(a), false, Arrays.asList(a), false),
                "same single signer");
        check(UpdateVersion.signersCompatible(Arrays.asList(b), false, Arrays.asList(a, b), false),
                "rotated lineage contains the current signer");
        check(!UpdateVersion.signersCompatible(Arrays.asList(b), false, Arrays.asList(a), false),
                "historical key alone is not the current signer");
        check(!UpdateVersion.signersCompatible(Arrays.asList(a), false, Arrays.asList(c), false),
                "foreign signer");
        check(UpdateVersion.signersCompatible(Arrays.asList(a, b), true, Arrays.asList(b, a), true),
                "same multi-signer set");
        check(!UpdateVersion.signersCompatible(Arrays.asList(a, b), true, Arrays.asList(a), false),
                "subset of a multi-signer set");
        check(!UpdateVersion.signersCompatible(Arrays.asList(a, b, c), true,
                Arrays.asList(a, b), true), "smaller multi-signer set");
        check(!UpdateVersion.signersCompatible(Arrays.asList(a), false, Arrays.<byte[]>asList(),
                false), "unsigned archive");
        check(UpdateVersion.handoverNote(DeviceState.RESPONDING, true).contains("30 seconds"),
                "BOTH handover");
        check(UpdateVersion.handoverNote(DeviceState.RESPONDING, false).contains("BOTH"),
                "TOP handover warns");
        check(UpdateVersion.handoverNote(DeviceState.UNKNOWN, false).contains("BOTH"),
                "unknown handover stays neutral");

        String notes = "# Jesty Thor Fix v1.5.17\n\n<p align=\"center\">x</p>\n"
                + "**Faster** boot with `ProcessWait`.\n\n## Details\nhidden";
        String summary = UpdateVersion.summarize(notes);
        check(summary.equals("Faster boot with ProcessWait."), "summary: " + summary);
        StringBuilder longNotes = new StringBuilder();
        for (int i = 0; i < 100; i++) longNotes.append("line ").append(i).append('\n');
        check(UpdateVersion.summarize(longNotes.toString()).length() <= 603, "summary cap");
        check(UpdateVersion.summarize(null).isEmpty(), "null notes");

        System.out.println("UpdateVersion tests passed");
    }
}
