package com.thor.displaypowertest;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import com.thor.displaypowertest.UpdateReleaseCatalog.Release;

/** Calls the actual AppUpdater entry; only Android metadata/UI boundaries are inert. */
public final class UpdaterArchiveHostTest {
    private static int checks, cases;
    private static final int SIGN = PackageManager.GET_SIGNING_CERTIFICATES;
    private static final String OLD = "the download is not newer than the installed app";
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static Signature[] certs(String... values) {
        Signature[] signatures = new Signature[values.length];
        for (int i = 0; i < values.length; i++)
            signatures[i] = new Signature(values[i].getBytes(StandardCharsets.UTF_8));
        return signatures;
    }
    private static SigningInfo single(String contents, String... history) {
        return new SigningInfo(false, certs(contents), certs(history));
    }
    private static PackageInfo info(String version, long code, SigningInfo signer) {
        PackageInfo info = new PackageInfo();
        info.packageName = "com.thor.displaypowertest";
        info.versionName = version; info.code = code; info.signingInfo = signer;
        return info;
    }
    private static PackageManager baseline() {
        PackageManager p = new PackageManager();
        p.installed = info("1.7.4", 84L, null);
        // Installed history contains an old key; only current contents may authorize.
        p.installedSigned = info("1.7.4", 84L, single("current", "old", "current"));
        p.archive = info("1.7.5", 85L, single("current", "current"));
        return p;
    }
    private static final class MetadataActivity extends Activity {
        private final PackageManager manager;
        MetadataActivity(PackageManager manager) { super(new File("unused-cache")); this.manager = manager; }
        public PackageManager getPackageManager() { manager.calls.add("manager"); return manager; }
        public String getPackageName() { manager.calls.add("package"); return "com.thor.displaypowertest"; }
    }
    private static final AppUpdater.Host HOST = new AppUpdater.Host() {
        public void onUpdateAvailable(String version, boolean pre) { throw new AssertionError("No update UI"); }
        public void onNoUpdate() { throw new AssertionError("No update UI"); }
        public String installBlocker() { throw new AssertionError("No installer gate"); }
        public String handoverNote() { throw new AssertionError("No handover"); }
        public void setInstallReserved(boolean reserved) { throw new AssertionError("No install reservation"); }
    };
    private static void verify(String id, PackageManager p, String error, int lookups) throws Exception {
        AppUpdater updater = new AppUpdater(new MetadataActivity(p), HOST);
        Method method = AppUpdater.class.getDeclaredMethod("verifyArchive", File.class, Release.class);
        method.setAccessible(true);
        Release release = new Release("v1.7.5", false, "", "", "", 1L, "");
        Throwable actual = null;
        try { method.invoke(updater, new File("synthetic-candidate.apk"), release); }
        catch (InvocationTargetException invoked) { actual = invoked.getCause(); }
        finally { updater.shutdown(); }
        if (p.signedFailure != null) {
            check(actual == p.signedFailure, "ARCHIVE " + id + " exception identity preserved");
        } else if (error == null) {
            check(actual == null, "ARCHIVE " + id + " expected acceptance; got "
                    + (actual == null ? "none" : actual.getClass().getSimpleName() + ":" + actual.getMessage()));
        } else {
            check(actual instanceof IOException && error.equals(actual.getMessage()),
                    "ARCHIVE " + id + " expected rejection: " + error);
        }
        String[] expected = lookups == 1 ? new String[]{"manager", "package", "archive:" + SIGN}
                : lookups == 2 ? new String[]{"manager", "package", "archive:" + SIGN, "installed:0"}
                : new String[]{"manager", "package", "archive:" + SIGN, "installed:0", "installed:" + SIGN};
        check(p.calls.equals(Arrays.asList(expected)), "ARCHIVE " + id + " metadata order/flags: " + p.calls);
        cases++;
        System.out.println("PASS " + id + ";outcome=" + (actual == null ? "ACCEPT" : actual.getClass().getSimpleName())
                + ";queries=" + p.calls);
    }
    public static void main(String[] args) throws Exception {
        PackageManager p = baseline(); verify("valid-current-signer", p, null, 3);
        p = baseline(); p.archive = null;
        verify("missing-archive", p, "the download is not a valid app", 1);
        p = baseline(); p.archive.packageName = "org.example.other";
        verify("wrong-package", p, "the download is a different app", 1);
        p = baseline(); p.archive.versionName = "1.7.4";
        verify("wrong-release-version", p, "the app version does not match the release", 1);
        p = baseline(); p.archive.code = 83L; verify("lower-code", p, OLD, 2);
        p = baseline(); p.archive.code = 84L; verify("equal-code", p, OLD, 2);
        p = baseline(); p.installed.code = 2147483649L; p.archive.code = 2147483650L;
        verify("long-code-not-truncated", p, null, 3);
        p = baseline(); p.archive.signingInfo = null;
        verify("missing-archive-signing", p, "could not read the app signatures", 3);
        p = baseline(); p.installedSigned.signingInfo = null;
        verify("missing-installed-signing", p, "could not read the app signatures", 3);
        p = baseline(); p.archive.signingInfo = single("foreign", "foreign");
        verify("foreign-signer", p, "the update is not signed with this app's key", 3);
        p = baseline(); p.archive.signingInfo = single("successor", "current", "successor");
        verify("rotation-lineage", p, null, 3);
        p = baseline(); p.archive.signingInfo = single("old", "old");
        verify("historical-key-alone", p, "the update is not signed with this app's key", 3);
        p = baseline(); p.installedSigned.signingInfo = new SigningInfo(true, certs("a", "b"), null);
        p.archive.signingInfo = new SigningInfo(true, certs("b", "a"), null);
        verify("same-multiple-signers", p, null, 3);
        p = baseline(); p.installedSigned.signingInfo = new SigningInfo(true, certs("a", "b"), null);
        p.archive.signingInfo = new SigningInfo(true, certs("a"), null);
        verify("multiple-signer-subset", p, "the update is not signed with this app's key", 3);
        p = baseline(); p.archive.signingInfo = single("current");
        verify("empty-archive-history", p, "the update is not signed with this app's key", 3);
        p = baseline(); p.installedSigned.signingInfo = new SigningInfo(true, certs("current"), null);
        verify("mixed-signing-shapes", p, "the update is not signed with this app's key", 3);
        p = baseline(); p.signedFailure = new PackageManager.NameNotFoundException("synthetic lookup failure");
        verify("framework-lookup-failure", p, null, 3);
        System.out.println("UpdaterArchiveHostTest PASS: " + cases + " cases / " + checks
                + " assertions; actual updater entry, metadata adapted; Android APK parsing NOT RUN");
    }
}
