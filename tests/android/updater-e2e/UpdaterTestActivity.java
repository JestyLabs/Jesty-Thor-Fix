package com.thor.displaypowertest;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Bundle;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.security.MessageDigest;
import java.util.Locale;

/** Emulator-only Activity: actual updater/receiver, controlled transport, no vendor services. */
public final class UpdaterTestActivity extends Activity implements AppUpdater.Host {
    private AppUpdater updater;
    private boolean reserved;
    private int reservations;
    private static File fixtures;
    private static String scenario = "normal";
    private static Intent confirmation;
    private static int launches;
    private static int launchAttempts;
    private static int lastConfirmationSession = -1;
    private static boolean replayingLaunch;
    private static int replays;
    private static boolean transportInstalled;
    private final android.os.Handler snapshots = new android.os.Handler();
    private final Runnable snapshot = new Runnable() {
        @Override public void run() {
            try { inspect(); } catch (Exception failure) { android.util.Log.e("UpdaterE2E", "SNAPSHOT_ERROR", failure); }
            snapshots.postDelayed(this, 250);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        fixtures = getExternalFilesDir(null);
        if (!transportInstalled) {
            URL.setURLStreamHandlerFactory(protocol -> "https".equals(protocol)
                    ? new URLStreamHandler() {
                        @Override protected URLConnection openConnection(URL url) {
                            return new FixtureConnection(url);
                        }
                    } : null);
            transportInstalled = true;
        }
        TextView view = new TextView(this);
        view.setText("Disposable updater test. No vendor services.");
        setContentView(view);
        getSharedPreferences("updates", MODE_PRIVATE).edit().putBoolean("auto_check", false).commit();
        updater = new AppUpdater(this, this);
        updater.start();
        command(getIntent());
        snapshots.post(snapshot);
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent); command(intent);
    }
    @Override protected void onResume() { super.onResume(); updater.onResume(); }
    @Override protected void onPause() { updater.onPause(); super.onPause(); }
    @Override protected void onDestroy() { snapshots.removeCallbacks(snapshot); updater.shutdown(); super.onDestroy(); }

    private void command(Intent intent) {
        try {
            String mode = intent.getStringExtra("mode");
            if (mode == null || "init".equals(mode)) { mark("READY"); return; }
            if ("update".equals(mode)) {
                scenario = intent.getStringExtra("scenario");
                if (scenario == null) scenario = "normal";
                File apk = new File(fixtures, "candidate.apk");
                String hash = digest(apk);
                if ("hash".equals(scenario)) hash = String.format(Locale.US, "%064d", 0);
                JSONObject release = new JSONObject().put("tag", "v1.0.1")
                        .put("prerelease", false).put("notes", "Disposable emulator fixture")
                        .put("asset", "Jesty-Thor-Fix-1.0.1.apk")
                        .put("url", "https://github.com/JestyLabs/Jesty-Thor-Fix/releases/download/v1.0.1/Jesty-Thor-Fix-1.0.1.apk")
                        .put("size", apk.length()).put("sha256", hash);
                getSharedPreferences("updates", MODE_PRIVATE).edit().putString("release", release.toString()).commit();
                updater.start(); updater.promptUpdate(); mark("OFFERED");
            } else if ("inspect".equals(mode)) inspect();
            else if ("recreate".equals(mode)) {
                setIntent(new Intent(this, UpdaterTestActivity.class).putExtra("mode", "init"));
                recreate();
            }
            else if ("duplicate_pending".equals(mode)) {
                if (confirmation == null) throw new IllegalStateException("No native confirmation captured");
                replay(status(PackageInstaller.STATUS_PENDING_USER_ACTION, owner().currentSessionId())
                        .putExtra(Intent.EXTRA_INTENT, new Intent(confirmation)));
                mark("INJECTED_PENDING");
            } else if ("duplicate_terminal".equals(mode) || "unknown_terminal".equals(mode)) {
                int id = intent.getIntExtra("session", -1);
                replay(status(PackageInstaller.STATUS_FAILURE_ABORTED, id));
                replay(status(PackageInstaller.STATUS_FAILURE_ABORTED, id));
                mark("INJECTED_TERMINAL");
            } else if ("sentinel".equals(mode)) {
                File dir = new File(getCacheDir(), "updates"); dir.mkdirs();
                try (FileOutputStream out = new FileOutputStream(new File(dir, "sentinel"))) { out.write(1); }
                mark("SENTINEL_CREATED");
            } else throw new IllegalArgumentException("Unknown test command");
        } catch (Exception error) { android.util.Log.e("UpdaterE2E", "TEST_ERROR", error); }
    }
    private Intent status(int value, int id) {
        return new Intent(this, UpdateInstallReceiver.class).setAction(UpdateInstallReceiver.ACTION_STATUS)
                .putExtra(PackageInstaller.EXTRA_STATUS, value).putExtra(PackageInstaller.EXTRA_SESSION_ID, id);
    }
    private void replay(Intent intent) { replay(this, intent); }
    private static void replay(android.content.Context context, Intent intent) {
        context.sendOrderedBroadcast(intent, null, new android.content.BroadcastReceiver() {
            @Override public void onReceive(android.content.Context context, Intent delivered) {
                replays++; mark("REPLAY_DELIVERED");
            }
        }, null, 0, null, null);
    }
    static void replayPending(android.content.Context context, int requestedSession) throws Exception {
        Intent nativeIntent = confirmation;
        if (nativeIntent == null) {
            Field storeField = AppUpdater.class.getDeclaredField("pendingConfirmations"); storeField.setAccessible(true);
            Object store = storeField.get(null);
            Field pendingField = UpdateConfirmationStore.class.getDeclaredField("pending"); pendingField.setAccessible(true);
            UpdateConfirmationStore.Pending<?> pending = (UpdateConfirmationStore.Pending<?>) pendingField.get(store);
            if (pending != null) nativeIntent = (Intent) pending.confirmation;
        }
        if (nativeIntent == null) throw new IllegalStateException("No platform confirmation available to replay");
        int session = requestedSession >= 0 ? requestedSession : owner().currentSessionId();
        Intent callback = new Intent(context, UpdateInstallReceiver.class).setAction(UpdateInstallReceiver.ACTION_STATUS)
                .putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_PENDING_USER_ACTION)
                .putExtra(PackageInstaller.EXTRA_SESSION_ID, session)
                .putExtra(Intent.EXTRA_INTENT, new Intent(nativeIntent));
        replay(context, callback);
    }
    private static UpdateSessionOwnership owner() throws Exception {
        Field f = AppUpdater.class.getDeclaredField("installSessions"); f.setAccessible(true);
        return (UpdateSessionOwnership) f.get(null);
    }
    private void inspect() throws Exception {
        JSONObject state = new JSONObject().put("pid", android.os.Process.myPid())
                .put("session", owner().currentSessionId()).put("transfer", owner().transferInProgress())
                .put("nativeSessions", getPackageManager().getPackageInstaller().getMySessions().size())
                .put("reserved", reserved).put("reservations", reservations)
                .put("launches", launches).put("launchAttempts", launchAttempts)
                .put("lastConfirmationSession", lastConfirmationSession).put("replays", replays)
                .put("pending", pendingSession())
                .put("sentinel", new File(getCacheDir(), "updates/sentinel").exists())
                .put("version", getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
        File temporary = new File(fixtures, "state.tmp");
        try (FileOutputStream out = new FileOutputStream(temporary)) {
            out.write(state.toString().getBytes("UTF-8"));
        }
        if (!temporary.renameTo(new File(fixtures, "state.json"))) throw new IOException("Snapshot rename failed");
    }
    private int pendingSession() throws Exception {
        Field f = AppUpdater.class.getDeclaredField("pendingConfirmations"); f.setAccessible(true);
        return ((UpdateConfirmationStore<?>) f.get(null)).pendingSessionId();
    }
    @Override public void startActivity(Intent intent) {
        // Observe the actual platform-provided Intent; never invent a confirmation.
        if ("android.content.pm.action.CONFIRM_INSTALL".equals(intent.getAction())) {
            confirmation = new Intent(intent); launchAttempts++;
            lastConfirmationSession = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1);
            if ("launch_failure".equals(scenario)) throw new android.content.ActivityNotFoundException("Injected test launch failure");
            launches++; mark("NATIVE_CONFIRMATION");
            if ("foreground_replay".equals(scenario) && !replayingLaunch) {
                replayingLaunch = true;
                try {
                    Intent replay = status(PackageInstaller.STATUS_PENDING_USER_ACTION, lastConfirmationSession)
                            .putExtra(Intent.EXTRA_INTENT, new Intent(intent));
                    new UpdateInstallReceiver().onReceive(this, replay);
                } finally { replayingLaunch = false; }
            }
        }
        super.startActivity(intent);
    }
    @Override public void onUpdateAvailable(String version, boolean prerelease) { mark("AVAILABLE"); }
    @Override public void onNoUpdate() { mark("NO_UPDATE"); }
    @Override public String installBlocker() { return null; }
    @Override public String handoverNote() { return "Disposable emulator test update"; }
    @Override public void setInstallReserved(boolean value) {
        reserved = value; if (value) reservations++;
        mark(value ? "RESERVED" : "RELEASED");
        try { inspect(); } catch (Exception failure) { throw new IllegalStateException(failure); }
    }
    private static void mark(String value) { android.util.Log.i("UpdaterE2E", value); }
    private static String digest(File file) throws Exception {
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            byte[] b = new byte[65536]; int n; while ((n = in.read(b)) != -1) hash.update(b, 0, n);
        }
        StringBuilder result = new StringBuilder();
        for (byte b : hash.digest()) result.append(String.format(Locale.US, "%02x", b & 255));
        return result.toString();
    }
    private static final class FixtureConnection extends HttpURLConnection {
        FixtureConnection(URL url) { super(url); }
        @Override public void connect() { }
        @Override public void disconnect() { }
        @Override public boolean usingProxy() { return false; }
        @Override public int getResponseCode() throws IOException {
            if ("delayed".equals(scenario)) try { Thread.sleep(4000); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IOException(error); }
            return "http_failure".equals(scenario) ? 503 : 200;
        }
        @Override public long getContentLengthLong() { return new File(fixtures, "candidate.apk").length(); }
        @Override public InputStream getInputStream() throws IOException {
            // A transport adapter only: verification, disk IO and installer remain production code.
            if (!url.toString().startsWith("https://github.com/JestyLabs/Jesty-Thor-Fix/releases/download/"))
                throw new IOException("Unexpected fixture URL");
            return new FilterInputStream(new FileInputStream(new File(fixtures, "candidate.apk"))) {
                @Override public int read(byte[] b, int off, int len) throws IOException {
                    if ("slow".equals(scenario)) try { Thread.sleep(1200); }
                    catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IOException(error); }
                    return super.read(b, off, Math.min(len, 1024));
                }
            };
        }
    }
}
