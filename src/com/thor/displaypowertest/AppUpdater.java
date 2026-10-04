package com.thor.displaypowertest;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Checks this repository's GitHub releases when the app is open, offers a newer
 * build, downloads it, verifies it and hands it to Android's PackageInstaller.
 * Android always shows its own confirmation and refuses an APK that is not
 * signed with the installed app's certificate. The root daemon is not involved.
 */
final class AppUpdater {
    interface Host {
        void onUpdateAvailable(String version, boolean prerelease);
        void onNoUpdate();
        /** Null when installing now is safe, otherwise the reason to wait. */
        String installBlocker();
        String handoverNote();
        /** UI thread: while reserved, the dashboard refuses new switch commands. */
        void setInstallReserved(boolean reserved);
    }

    // Shared with UpdateInstallReceiver (same process, main thread).
    private static WeakReference<Activity> resumedActivity = new WeakReference<>(null);
    private static Intent pendingConfirmation;
    private static int pendingSessionId = -1;

    static final String REPOSITORY = "JestyLabs/Jesty-Thor-Fix";
    private static final String API = "https://api.github.com/repos/" + REPOSITORY;
    private static final String TAG = "ThorDisplayUpdate";
    private static final String PREFS = "updates";
    private static final String PREF_AUTO_CHECK = "auto_check";
    private static final String PREF_PRERELEASES = "include_prereleases";
    private static final String PREF_LAST_CHECK = "last_check";
    private static final String PREF_RELEASE = "release";
    private static final String PREF_PROMPTED = "prompted_tag";
    private static final int MAX_RELEASE_JSON_BYTES = 1024 * 1024;
    private static final long MAX_APK_BYTES = 64L * 1024L * 1024L;

    private static final class Release {
        final String tag;
        final boolean prerelease;
        final String notes;
        final String assetName;
        final String apkUrl;
        final long apkSize;
        final String sha256;

        Release(String tag, boolean prerelease, String notes, String assetName, String apkUrl,
                long apkSize, String sha256) {
            this.tag = tag;
            this.prerelease = prerelease;
            this.notes = notes;
            this.assetName = assetName;
            this.apkUrl = apkUrl;
            this.apkSize = apkSize;
            this.sha256 = sha256;
        }

        String version() { return UpdateVersion.stripV(tag); }

        /** Every field is revalidated, including a release read back from the cache. */
        boolean valid() {
            return UpdateVersion.parse(tag) != null
                    && UpdateVersion.isExpectedAsset(assetName, tag)
                    && UpdateVersion.isTrustedDownloadUrl(apkUrl, REPOSITORY, tag, assetName)
                    && sha256 != null && UpdateVersion.sha256FromDigest("sha256:" + sha256) != null
                    && apkSize > 0L && apkSize <= MAX_APK_BYTES;
        }
    }

    private final Activity activity;
    private final Host host;
    private final SharedPreferences prefs;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean checking = new AtomicBoolean(false);
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private Release available;
    private Release awaitingInstallPermission;
    private AlertDialog progressDialog;
    private boolean resumed;

    AppUpdater(Activity activity, Host host) {
        this.activity = activity;
        this.host = host;
        this.prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean supported() {
        // Archive signer checks need SigningInfo (API 28). The Thor runs Android 13.
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P;
    }

    /** Shows a cached update immediately; network checks happen from onResume. */
    void start() {
        if (!supported()) return;
        if (!busy.get()) {
            clearDownloads(activity);
            abandonStaleSessions(activity);
        }
        Release cached = readCached();
        if (cached != null && UpdateVersion.isNewer(cached.tag, installedVersion())
                && (!cached.prerelease || includePrereleases())) {
            publish(cached, false);
        } else if (cached != null) {
            prefs.edit().remove(PREF_RELEASE).apply();
        }
    }

    void onResume() {
        resumed = true;
        resumedActivity = new WeakReference<>(activity);
        Intent confirmation = pendingConfirmation;
        if (confirmation != null) {
            int sessionId = pendingSessionId;
            pendingConfirmation = null;
            pendingSessionId = -1;
            showConfirmation(activity, confirmation, sessionId);
            return;
        }
        Release release = awaitingInstallPermission;
        if (release != null) {
            awaitingInstallPermission = null;
            if (canInstallPackages()) {
                confirmAndDownload(release);
            } else {
                toast("Updating needs \"Install unknown apps\" for Jesty Thor Fix");
            }
            return;
        }
        if (supported() && autoCheck() && UpdateVersion.shouldCheck(System.currentTimeMillis(),
                prefs.getLong(PREF_LAST_CHECK, 0L), UpdateVersion.CHECK_INTERVAL_MS)) {
            check(false);
        }
    }

    void onPause() {
        resumed = false;
        if (resumedActivity.get() == activity) resumedActivity = new WeakReference<>(null);
    }

    void shutdown() {
        cancelled.set(true);
        if (progressDialog != null) dismiss(progressDialog);
        progressDialog = null;
        worker.shutdownNow();
    }

    /**
     * Called by UpdateInstallReceiver for STATUS_PENDING_USER_ACTION. Android 13
     * may silently drop an activity start from a non-visible app, so the
     * confirmation is launched only from a resumed activity; otherwise it waits
     * for the next onResume.
     */
    static void deliverConfirmation(Context context, Intent confirmation, int sessionId) {
        Activity visible = resumedActivity.get();
        if (visible != null && !visible.isFinishing() && !visible.isDestroyed()) {
            showConfirmation(visible, confirmation, sessionId);
            return;
        }
        pendingConfirmation = confirmation;
        pendingSessionId = sessionId;
        Toast.makeText(context, "Open Jesty Thor Fix to finish the update",
                Toast.LENGTH_LONG).show();
    }

    private static void showConfirmation(Activity visible, Intent confirmation, int sessionId) {
        try {
            visible.startActivity(confirmation);
        } catch (Throwable error) {
            Log.w(TAG, "could not show the install confirmation", error);
            abandonSession(visible, sessionId);
            clearDownloads(visible);
            Toast.makeText(visible, "Update failed: could not show Android's confirmation",
                    Toast.LENGTH_LONG).show();
        }
    }

    static void abandonSession(Context context, int sessionId) {
        if (sessionId < 0) return;
        try {
            context.getPackageManager().getPackageInstaller().abandonSession(sessionId);
        } catch (Throwable ignored) {
            // Already finished or expired.
        }
    }

    /** Sessions left by an earlier process (e.g. a confirmation that never appeared). */
    private static void abandonStaleSessions(Context context) {
        try {
            PackageInstaller installer = context.getPackageManager().getPackageInstaller();
            for (PackageInstaller.SessionInfo session : installer.getMySessions()) {
                if (session.getSessionId() != pendingSessionId) {
                    abandonSession(context, session.getSessionId());
                }
            }
        } catch (Throwable error) {
            Log.w(TAG, "could not list update sessions", error);
        }
    }

    /** Called from the top-bar UPDATE button. */
    void promptUpdate() {
        if (available != null) showUpdateDialog(available);
    }

    /** Long-press on GITHUB: manual check and the two update preferences. */
    void showSettings() {
        if (!supported()) {
            toast("In-app updates need Android 9 or newer");
            return;
        }
        String[] items = {
                "Check for updates now",
                "Automatic check when opened: " + (autoCheck() ? "ON" : "OFF"),
                "Include test pre-releases: " + (includePrereleases() ? "ON" : "OFF")
        };
        dialog().setTitle("Updates")
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        check(true);
                    } else if (which == 1) {
                        prefs.edit().putBoolean(PREF_AUTO_CHECK, !autoCheck()).apply();
                        toast("Automatic update check " + (autoCheck() ? "on" : "off"));
                    } else {
                        setIncludePrereleases(!includePrereleases());
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void setIncludePrereleases(boolean include) {
        if (!include) {
            prefs.edit().putBoolean(PREF_PRERELEASES, false).remove(PREF_RELEASE)
                    .putLong(PREF_LAST_CHECK, 0L).apply();
            clearAvailable();
            toast("Test pre-releases off");
            check(true);
            return;
        }
        dialog().setTitle("Include test pre-releases?")
                .setMessage("Pre-releases are diagnostic builds that may not have been tested"
                        + " on a Thor. The stable release stays recommended.")
                .setPositiveButton("Include", (d, w) -> {
                    prefs.edit().putBoolean(PREF_PRERELEASES, true)
                            .putLong(PREF_LAST_CHECK, 0L).apply();
                    check(true);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void check(boolean userRequested) {
        if (!checking.compareAndSet(false, true)) return;
        final boolean includePre = includePrereleases();
        final String installed = installedVersion();
        worker.execute(() -> {
            try {
                Release release = fetchBest(includePre, installed);
                SharedPreferences.Editor edit = prefs.edit()
                        .putLong(PREF_LAST_CHECK, System.currentTimeMillis());
                if (release == null) edit.remove(PREF_RELEASE);
                else edit.putString(PREF_RELEASE, toCache(release));
                edit.apply();
                onUi(() -> {
                    if (release != null) {
                        publish(release, true);
                    } else {
                        clearAvailable();
                        if (userRequested) toast("Jesty Thor Fix is up to date (v" + installed + ")");
                    }
                });
            } catch (Throwable error) {
                Log.w(TAG, "update check failed", error);
                if (userRequested) onUi(() -> toast("Update check failed: " + reason(error)));
            } finally {
                checking.set(false);
            }
        });
    }

    private void publish(Release release, boolean promptIfNew) {
        available = release;
        host.onUpdateAvailable(release.version(), release.prerelease);
        if (promptIfNew && resumed && !release.tag.equals(prefs.getString(PREF_PROMPTED, null))) {
            prefs.edit().putString(PREF_PROMPTED, release.tag).apply();
            showUpdateDialog(release);
        }
    }

    private void clearAvailable() {
        available = null;
        host.onNoUpdate();
    }

    private void showUpdateDialog(Release release) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        StringBuilder message = new StringBuilder(String.format(Locale.US,
                "Version %s%s is available. You have %s.", release.version(),
                release.prerelease ? " (test pre-release)" : "", installedVersion()));
        String notes = UpdateVersion.summarize(release.notes);
        if (!notes.isEmpty()) message.append("\n\n").append(notes);
        message.append("\n\nYour settings are kept. Android will ask you to confirm.");
        dialog().setTitle("Update available")
                .setMessage(message)
                .setPositiveButton("Update", (d, which) -> startUpdate(release))
                .setNegativeButton("Later", null)
                .show();
    }

    private void startUpdate(Release release) {
        String blocker = host.installBlocker();
        if (blocker != null) {
            dialog().setTitle("Update not started").setMessage(blocker)
                    .setPositiveButton("OK", null).show();
            return;
        }
        if (canInstallPackages()) {
            confirmAndDownload(release);
            return;
        }
        dialog().setTitle("Allow updates")
                .setMessage("Allow Jesty Thor Fix to install apps: turn on"
                        + " \"Allow from this source\", then come back.")
                .setPositiveButton("Open settings", (d, which) -> {
                    awaitingInstallPermission = release;
                    try {
                        activity.startActivity(new Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + activity.getPackageName())));
                    } catch (Throwable error) {
                        awaitingInstallPermission = null;
                        toast("Could not open settings");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Apps targeting API 25 cannot use canRequestPackageInstalls() (it always
     * returns false); Android's own install confirmation asks for the
     * unknown-sources permission instead. The pre-check applies only from API 26.
     */
    private boolean canInstallPackages() {
        if (activity.getApplicationInfo().targetSdkVersion < Build.VERSION_CODES.O) return true;
        return activity.getPackageManager().canRequestPackageInstalls();
    }

    private void confirmAndDownload(Release release) {
        dialog().setTitle("Before updating")
                .setMessage(host.handoverNote())
                .setPositiveButton("Download", (d, which) -> download(release))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void download(Release release) {
        if (busy.get()) return;
        // UI thread: check and reserve together, so no switch command can start
        // between this check and the end of the install session commit.
        String blocker = host.installBlocker();
        if (blocker != null) {
            dialog().setTitle("Update not started").setMessage(blocker)
                    .setPositiveButton("OK", null).show();
            return;
        }
        if (!busy.compareAndSet(false, true)) return;
        cancelled.set(false);
        host.setInstallReserved(true);

        int padding = Math.round(24 * activity.getResources().getDisplayMetrics().density);
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(padding, padding / 2, padding, 0);
        TextView label = new TextView(activity);
        label.setText("Downloading v" + release.version() + "...");
        box.addView(label);
        ProgressBar bar = new ProgressBar(activity, null,
                android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        box.addView(bar);
        AlertDialog progress = dialog().setTitle("Updating")
                .setView(box)
                .setCancelable(false)
                .setNegativeButton("Cancel", (d, which) -> cancelled.set(true))
                .show();
        progressDialog = progress;

        worker.execute(() -> {
            try {
                File apk = downloadVerified(release, percent -> onUi(() -> bar.setProgress(percent)));
                onUi(() -> label.setText("Checking the update..."));
                verifyArchive(apk, release);
                install(apk);
                // The committed session holds its own copy of the APK.
                clearDownloads(activity);
                onUi(() -> toast("Confirm the update in Android's installer"));
            } catch (Throwable error) {
                Log.w(TAG, "update failed", error);
                clearDownloads(activity);
                String message = cancelled.get() ? "Update cancelled"
                        : "Update failed: " + reason(error);
                onUi(() -> toast(message));
            } finally {
                busy.set(false);
                onUi(() -> {
                    dismiss(progress);
                    if (progressDialog == progress) progressDialog = null;
                    host.setInstallReserved(false);
                });
            }
        });
    }

    /** Worker thread: waits briefly for a fresh dashboard sample, then gives the reason or null. */
    private String waitForInstallGate() throws InterruptedException {
        String blocker = host.installBlocker();
        for (int i = 0; i < 15 && blocker != null && !cancelled.get(); i++) {
            Thread.sleep(200L);
            blocker = host.installBlocker();
        }
        return blocker;
    }

    private interface Progress {
        void update(int percent);
    }

    private File downloadVerified(Release release, Progress progress) throws Exception {
        if (!release.valid()) throw new IOException("unexpected release information");
        clearDownloads(activity);
        File directory = downloadDirectory(activity);
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("cannot create the download folder");
        }
        File apk = new File(directory, "update.apk");
        HttpURLConnection connection = open(release.apkUrl, "application/octet-stream");
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        long total = 0L;
        int lastPercent = -1;
        try {
            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) throw new IOException("HTTP " + code);
            if (!"https".equals(connection.getURL().getProtocol())) {
                throw new IOException("download left HTTPS");
            }
            long length = connection.getContentLengthLong();
            if (length >= 0 && length != release.apkSize) {
                throw new IOException("download size does not match the release");
            }
            try (InputStream input = connection.getInputStream();
                 OutputStream output = new FileOutputStream(apk)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (cancelled.get() || Thread.currentThread().isInterrupted()) {
                        throw new IOException("cancelled");
                    }
                    total += read;
                    if (total > release.apkSize) throw new IOException("download is too large");
                    sha256.update(buffer, 0, read);
                    output.write(buffer, 0, read);
                    int percent = (int) (total * 100L / release.apkSize);
                    if (percent != lastPercent) {
                        lastPercent = percent;
                        progress.update(percent);
                    }
                }
            }
        } finally {
            connection.disconnect();
        }
        if (total != release.apkSize) throw new IOException("download is incomplete");
        if (!release.sha256.equals(hex(sha256.digest()))) {
            throw new IOException("checksum does not match the release");
        }
        return apk;
    }

    private void verifyArchive(File apk, Release release) throws Exception {
        PackageManager packages = activity.getPackageManager();
        String packageName = activity.getPackageName();
        PackageInfo archive = packages.getPackageArchiveInfo(apk.getAbsolutePath(),
                PackageManager.GET_SIGNING_CERTIFICATES);
        if (archive == null) throw new IOException("the download is not a valid app");
        if (!packageName.equals(archive.packageName)) {
            throw new IOException("the download is a different app");
        }
        if (!release.version().equals(archive.versionName)) {
            throw new IOException("the app version does not match the release");
        }
        PackageInfo installed = packages.getPackageInfo(packageName, 0);
        if (archive.getLongVersionCode() <= installed.getLongVersionCode()) {
            throw new IOException("the download is not newer than the installed app");
        }
        SigningInfo signing = archive.signingInfo;
        PackageInfo installedSigned = packages.getPackageInfo(packageName,
                PackageManager.GET_SIGNING_CERTIFICATES);
        SigningInfo current = installedSigned.signingInfo;
        if (signing == null || current == null) {
            throw new IOException("could not read the app signatures");
        }
        // The archive must carry the installed app's current signer: the same set
        // for multi-signer APKs, or the current certificate in a single signer's
        // rotation lineage. Android enforces its own rules again at install time.
        boolean trusted = UpdateVersion.signersCompatible(
                encoded(current.getApkContentsSigners()), current.hasMultipleSigners(),
                encoded(signing.hasMultipleSigners() ? signing.getApkContentsSigners()
                        : signing.getSigningCertificateHistory()),
                signing.hasMultipleSigners());
        if (!trusted) throw new IOException("the update is not signed with this app's key");
    }

    private static List<byte[]> encoded(Signature[] signatures) {
        List<byte[]> result = new ArrayList<>();
        if (signatures != null) {
            for (Signature signature : signatures) result.add(signature.toByteArray());
        }
        return result;
    }

    private void install(File apk) throws Exception {
        PackageInstaller installer = activity.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(activity.getPackageName());
        params.setSize(apk.length());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED);
        }
        int sessionId = installer.createSession(params);
        try (PackageInstaller.Session session = installer.openSession(sessionId)) {
            try (InputStream input = new FileInputStream(apk);
                 OutputStream output = session.openWrite("base.apk", 0, apk.length())) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (cancelled.get() || Thread.currentThread().isInterrupted()) {
                        throw new IOException("cancelled");
                    }
                    output.write(buffer, 0, read);
                }
                session.fsync(output);
            }
            // Last check immediately before the commit; nothing installs after a cancel.
            String blocker = waitForInstallGate();
            if (cancelled.get()) throw new IOException("cancelled");
            if (blocker != null) throw new IOException(blocker);
            Intent status = new Intent(activity, UpdateInstallReceiver.class)
                    .setAction(UpdateInstallReceiver.ACTION_STATUS);
            // PackageInstaller adds the status extras, so the intent must stay mutable.
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags |= PendingIntent.FLAG_MUTABLE;
            PendingIntent pending = PendingIntent.getBroadcast(activity, sessionId, status, flags);
            session.commit(pending.getIntentSender());
        } catch (Exception error) {
            abandonSession(activity, sessionId);
            throw error;
        }
    }

    private Release fetchBest(boolean includePre, String installed) throws Exception {
        List<JSONObject> objects = new ArrayList<>();
        if (includePre) {
            JSONArray releases = new JSONArray(fetchJson(API + "/releases?per_page=20"));
            for (int i = 0; i < releases.length(); i++) {
                JSONObject release = releases.optJSONObject(i);
                if (release != null) objects.add(release);
            }
        } else {
            objects.add(new JSONObject(fetchJson(API + "/releases/latest")));
        }
        List<Release> parsed = new ArrayList<>();
        List<UpdateVersion.Candidate> candidates = new ArrayList<>();
        for (JSONObject object : objects) {
            Release release = parseRelease(object);
            parsed.add(release);
            candidates.add(new UpdateVersion.Candidate(object.optString("tag_name", ""),
                    object.optBoolean("draft"), object.optBoolean("prerelease"),
                    release != null));
        }
        int index = UpdateVersion.best(candidates, installed, includePre);
        return index < 0 ? null : parsed.get(index);
    }

    private static Release parseRelease(JSONObject release) {
        String tag = release.optString("tag_name", "");
        if (release.optBoolean("draft") || UpdateVersion.parse(tag) == null) return null;
        JSONArray assets = release.optJSONArray("assets");
        if (assets == null) return null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset == null || !UpdateVersion.isExpectedAsset(asset.optString("name"), tag)) {
                continue;
            }
            Release candidate = new Release(tag, release.optBoolean("prerelease"),
                    release.optString("body", ""), asset.optString("name"),
                    asset.optString("browser_download_url", ""), asset.optLong("size", 0L),
                    UpdateVersion.sha256FromDigest(
                            asset.isNull("digest") ? null : asset.optString("digest")));
            return candidate.valid() ? candidate : null;
        }
        return null;
    }

    private static String toCache(Release release) throws Exception {
        return new JSONObject()
                .put("tag", release.tag)
                .put("prerelease", release.prerelease)
                .put("notes", release.notes)
                .put("asset", release.assetName)
                .put("url", release.apkUrl)
                .put("size", release.apkSize)
                .put("sha256", release.sha256)
                .toString();
    }

    private Release readCached() {
        String json = prefs.getString(PREF_RELEASE, null);
        if (json == null) return null;
        try {
            JSONObject cached = new JSONObject(json);
            Release release = new Release(cached.optString("tag"),
                    cached.optBoolean("prerelease"), cached.optString("notes"),
                    cached.optString("asset"), cached.optString("url"),
                    cached.optLong("size"), cached.optString("sha256"));
            return release.valid() ? release : null;
        } catch (Throwable error) {
            return null;
        }
    }

    private static String fetchJson(String url) throws Exception {
        HttpURLConnection connection = open(url, "application/vnd.github+json");
        try {
            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) throw new IOException("HTTP " + code);
            try (InputStream input = connection.getInputStream()) {
                ByteArrayOutputStream body = new ByteArrayOutputStream();
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    body.write(buffer, 0, read);
                    if (body.size() > MAX_RELEASE_JSON_BYTES) {
                        throw new IOException("release response is too large");
                    }
                }
                return new String(body.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static HttpURLConnection open(String url, String accept) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(30_000);
        connection.setInstanceFollowRedirects(true);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept", accept);
        connection.setRequestProperty("User-Agent", "Jesty-Thor-Fix-Updater");
        return connection;
    }

    private boolean autoCheck() { return prefs.getBoolean(PREF_AUTO_CHECK, true); }
    private boolean includePrereleases() { return prefs.getBoolean(PREF_PRERELEASES, false); }

    private String installedVersion() {
        try {
            String version = activity.getPackageManager()
                    .getPackageInfo(activity.getPackageName(), 0).versionName;
            return version == null ? "0" : version;
        } catch (Throwable error) {
            return "0";
        }
    }

    private static String reason(Throwable error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) value.append(String.format(Locale.US, "%02x", b & 0xff));
        return value.toString();
    }

    private static File downloadDirectory(Context context) {
        return new File(context.getCacheDir(), "updates");
    }

    static void clearDownloads(Context context) {
        File[] files = downloadDirectory(context).listFiles();
        if (files == null) return;
        for (File file : files) {
            if (!file.delete()) Log.w(TAG, "could not delete " + file);
        }
    }

    private AlertDialog.Builder dialog() {
        return new AlertDialog.Builder(activity, android.R.style.Theme_Material_Dialog_Alert);
    }

    private void toast(String message) {
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
    }

    private void onUi(Runnable action) {
        activity.runOnUiThread(() -> {
            if (!activity.isFinishing() && !activity.isDestroyed()) action.run();
        });
    }

    private static void dismiss(AlertDialog dialog) {
        try {
            if (dialog.isShowing()) dialog.dismiss();
        } catch (Throwable ignored) {
            // The Activity may already be gone.
        }
    }
}
