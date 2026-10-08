package org.jestylabs.thorfix.updategateharness;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInstaller;
import android.os.Bundle;

import com.thor.displaypowertest.UpdateCommitGate;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Disposable Android 13 emulator test. Compiles the real UpdateCommitGate
 * source into a separate test-only APK; it never uses the vendor bridge.
 *
 * Covers native device threading plus PackageInstaller session abandonment
 * after accepted cancellation. It does not claim to run AppUpdater's network
 * download or the production UI.
 */
public final class GateTestActivity extends Activity {
    private static final String PREFS = "update_gate_ci";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String mode = getIntent().getStringExtra("mode");
        if ("init".equals(mode)) {
            if (getExternalFilesDir(null) == null) throw new IllegalStateException("no files dir");
            set("result", "READY");
            finish();
            return;
        }
        if (!"run".equals(mode)) {
            set("result", "UNEXPECTED_MODE");
            finish();
            return;
        }
        set("result", "RUNNING");
        new Thread(() -> {
            try {
                runChecks();
                set("result", "PASS");
            } catch (Throwable t) {
                android.util.Log.e("ThorGateCI", "emulator test failure", t);
                String reason = t.getClass().getSimpleName();
                if (t.getMessage() != null) reason += ": " + t.getMessage();
                set("failure", reason.length() > 160 ? reason.substring(0, 160) : reason);
                set("result", "FAIL");
            } finally {
                runOnUiThread(this::finish);
            }
        }, "thor-gate-ci").start();
    }

    private void runChecks() throws Exception {
        // Real Android threads use the production Java gate, not a reimplementation.
        for (int i = 0; i < 64; i++) {
            UpdateCommitGate gate = new UpdateCommitGate();
            require(gate.start(), "race start");
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch release = new CountDownLatch(1);
            AtomicBoolean cancelled = new AtomicBoolean();
            AtomicBoolean committing = new AtomicBoolean();
            Thread cancel = new Thread(() -> {
                ready.countDown();
                await(release);
                cancelled.set(gate.cancel());
            }, "cancel");
            Thread commit = new Thread(() -> {
                ready.countDown();
                await(release);
                committing.set(gate.beginCommit());
            }, "commit");
            cancel.start();
            commit.start();
            require(ready.await(5, TimeUnit.SECONDS), "race threads not ready");
            release.countDown();
            cancel.join(5000);
            commit.join(5000);
            require(!cancel.isAlive() && !commit.isAlive(), "race threads stuck");
            require(cancelled.get() != committing.get(), "Cancel and commit both/neither won");
            require(gate.wasCancelled() == cancelled.get(), "wrong final state");
            require(!gate.beginCommit(), "double commit");
            gate.finish();
        }
        set("race_result", "PASS");

        UpdateCommitGate cancelledGate = new UpdateCommitGate();
        require(cancelledGate.start(), "cancelled install start");
        File apk = new File(getExternalFilesDir(null), "candidate.apk");
        require(apk.isFile() && apk.length() > 1000L, "test APK missing");
        PackageInstaller installer = getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(getPackageName());
        params.setSize(apk.length());
        int id = installer.createSession(params);
        boolean abandoned = false;
        try {
            try (PackageInstaller.Session session = installer.openSession(id)) {
                try (FileInputStream input = new FileInputStream(apk);
                     OutputStream output = session.openWrite("base.apk", 0, apk.length())) {
                    byte[] bytes = new byte[32768];
                    int read;
                    while ((read = input.read(bytes)) != -1) output.write(bytes, 0, read);
                    session.fsync(output);
                }
                // Simulates Cancel winning immediately before the handoff.
                require(cancelledGate.cancel(), "cancel before commit rejected");
                require(cancelledGate.wasCancelled(), "accepted Cancel missing");
                require(!cancelledGate.beginCommit(), "cancelled session passed commit gate");
                // Critically, do not call session.commit() if the gate rejects.
            }
        } finally {
            installer.abandonSession(id);
            abandoned = true;
            cancelledGate.finish();
        }
        require(abandoned, "session not abandoned");
        boolean present = true;
        for (int attempt = 0; attempt < 21; attempt++) {
            present = false;
            for (PackageInstaller.SessionInfo entry : installer.getMySessions()) {
                if (entry.getSessionId() == id) { present = true; break; }
            }
            if (!present) break;
            if (attempt < 20) Thread.sleep(100L);
        }
        require(!present, "cancelled session still listed");
        set("abandon_result", "PASS");

        UpdateCommitGate commitFirst = new UpdateCommitGate();
        require(commitFirst.start(), "commit-first start");
        require(commitFirst.beginCommit(), "commit allowed");
        require(!commitFirst.cancel(), "late Cancel incorrectly accepted");
        require(!commitFirst.wasCancelled(), "late Cancel changed state");
        commitFirst.finish();
        require(commitFirst.start(), "cleanup did not re-arm gate");
        require(commitFirst.cancel(), "re-armed Cancel rejected");
        require(!commitFirst.beginCommit(), "re-armed Cancel did not block");
        commitFirst.finish();
        set("commit_boundary_result", "PASS");
    }

    private static void require(boolean value, String text) {
        if (!value) throw new AssertionError(text);
    }

    private static void await(CountDownLatch latch) {
        try { latch.await(); } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        }
    }

    private void set(String key, String value) {
        if (!getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(key, value).commit()) {
            throw new IllegalStateException("could not persist " + key);
        }
    }
}
