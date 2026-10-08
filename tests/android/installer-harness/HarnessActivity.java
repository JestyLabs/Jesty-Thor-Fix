package org.jestylabs.thorfix.installerharness;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;

/**
 * Disposable Android emulator-only test component. It never runs as the Thor
 * application and is deliberately not included in any production APK.
 */
public final class HarnessActivity extends Activity {
    static final String PREFS = "installer_ci";
    static final String ACTION_STATUS = "org.jestylabs.thorfix.installerharness.STATUS";
    private static final String TARGET_PACKAGE = "com.thor.displaypowertest";
    private static final String CANDIDATE_FILE = "candidate.apk";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String mode = getIntent().getStringExtra("mode");
        if (mode == null) mode = "unknown";
        try {
            if ("init".equals(mode)) {
                if (getExternalFilesDir(null) == null) throw new IllegalStateException("no app files dir");
                record("init_result", "READY");
            } else if ("abandon".equals(mode)) {
                testAbandon();
            } else if ("commit".equals(mode)) {
                testCommit();
            } else if ("confirm".equals(mode)) {
                openSystemConfirmation();
            } else {
                throw new IllegalArgumentException("unsupported test mode");
            }
        } catch (Exception failure) {
            // Test-only diagnostic: record the failing API stage and exception.
            // No APK bytes, private device data or authentication tokens are logged.
            android.util.Log.e("ThorInstallerCI", "harness mode=" + mode, failure);
            String reason = failure.getMessage();
            if (reason == null) reason = "";
            reason = reason.replaceAll("[\\r\\n\\t]", " ");
            if (reason.length() > 180) reason = reason.substring(0, 180);
            record("failure", mode + ":" + failure.getClass().getSimpleName() + ":" + reason);
        } finally {
            finish();
        }
    }

    private PackageInstaller installer() {
        return getPackageManager().getPackageInstaller();
    }

    private File candidate() {
        File root = getExternalFilesDir(null);
        if (root == null) throw new IllegalStateException("no candidate directory");
        File file = new File(root, CANDIDATE_FILE);
        if (!file.isFile() || file.length() < 1000L) {
            throw new IllegalStateException("candidate file missing or too small");
        }
        return file;
    }

    private int createPopulatedSession() throws Exception {
        File apk = candidate();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(TARGET_PACKAGE);
        if (Build.VERSION.SDK_INT >= 31) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED);
        }
        int id = installer().createSession(params);
        boolean prepared = false;
        try (PackageInstaller.Session session = installer().openSession(id);
             FileInputStream from = new FileInputStream(apk);
             OutputStream to = session.openWrite("base.apk", 0, apk.length())) {
            byte[] block = new byte[32768];
            int size;
            while ((size = from.read(block)) != -1) to.write(block, 0, size);
            session.fsync(to);
            prepared = true;
        } finally {
            if (!prepared) {
                try { installer().abandonSession(id); } catch (Exception ignored) { }
            }
        }
        return id;
    }

    private void testAbandon() throws Exception {
        // Each invocation has a new result so repeated tests cannot accidentally
        // pass from a previous invocation's persisted success marker.
        record("abandon_result", "STARTED");
        record("abandon_phase", "CREATING_SESSION");
        int id = createPopulatedSession();
        record("abandon_phase", "SESSION_POPULATED");
        installer().abandonSession(id);
        record("abandon_phase", "ABANDON_SENT");

        // PackageInstaller session inventory is observed through a separate
        // system-server query. Allow a bounded asynchronous removal window
        // rather than assuming the listing changes synchronously with abandon().
        // Never ignore a stuck session: fail with the precise stage and session ID.
        for (int attempt = 0; attempt < 21; attempt++) {
            boolean stillOpen = false;
            for (PackageInstaller.SessionInfo item : installer().getMySessions()) {
                if (item.getSessionId() == id) {
                    stillOpen = true;
                    break;
                }
            }
            if (!stillOpen) {
                record("abandon_phase", "CONFIRMED_GONE");
                record("abandon_result", "ABANDONED");
                return;
            }
            if (attempt < 20) Thread.sleep(100L);
        }
        record("abandon_phase", "TIMED_OUT");
        throw new IllegalStateException("session " + id + " still listed after 2s");
    }

    private void testCommit() throws Exception {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove("callback_result").remove("confirmation_result").remove("failure")
                .commit();
        int id = createPopulatedSession();
        record("commit_result", "SESSION_PREPARED");
        Intent callback = new Intent(this, InstallerStatusReceiver.class)
                .setAction(ACTION_STATUS)
                .putExtra("expected_session", id);
        PendingIntent result = PendingIntent.getBroadcast(this, id, callback,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        try (PackageInstaller.Session session = installer().openSession(id)) {
            // This is the actual Android API path, not 'adb install -r'.
            session.commit(result.getIntentSender());
        }
        record("commit_result", "COMMIT_CALLED");
    }

    private void openSystemConfirmation() {
        Intent confirmation = InstallerStatusReceiver.takeConfirmation();
        if (confirmation == null) {
            record("confirmation_result", "MISSING_PENDING_INTENT");
            return;
        }
        record("confirmation_result", "OPENED_SYSTEM_UI");
        confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(confirmation);
    }

    private void record(String key, String value) {
        if (!getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(key, value).commit()) {
            throw new IllegalStateException("could not persist " + key);
        }
    }
}
