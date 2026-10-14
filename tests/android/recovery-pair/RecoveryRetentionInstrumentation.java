package org.jestylabs.thorfix.recoveryaudit;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/** Reads the real target's storage and UI in a disposable emulator; no hardware toggle. */
public final class RecoveryRetentionInstrumentation extends Instrumentation {
    private static final String TARGET = "com.thor.displaypowertest";
    private static final long LAST_CHECK = 1234567890123L;
    private Bundle args;
    private Activity activity;
    private Context target;
    private int checks;
    @Override public void onCreate(Bundle args) { super.onCreate(args); this.args = args; start(); }
    private void require(boolean ok, String reason) { checks++; if (!ok) throw new AssertionError(reason); }
    private static byte[] read(File file) throws Exception {
        try (FileInputStream in = new FileInputStream(file)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            return out.toByteArray();
        }
    }
    private static void write(File file, byte[] bytes) throws Exception {
        try (FileOutputStream out = new FileOutputStream(file)) { out.write(bytes); out.getFD().sync(); }
    }
    private static String hash(byte[] bytes) throws Exception {
        StringBuilder out = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) out.append(String.format("%02x", b & 255));
        return out.toString();
    }
    private File data() { return new File(target.getFilesDir(), "recovery_pair_retention.bin"); }
    private File checkpoint() { return new File(target.getFilesDir(), "recovery_pair_checkpoint.json"); }
    private byte[] expectedData() {
        byte[] seed = args.getString("nonce").getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[32768];
        for (int i = 0; i < bytes.length; i++) bytes[i] = seed[i % seed.length];
        return bytes;
    }
    private JSONObject xmlHashes() throws Exception {
        File dir = new File(target.getDataDir(), "shared_prefs");
        return new JSONObject().put("state", hash(read(new File(dir, "state.xml"))))
                .put("updates", hash(read(new File(dir, "updates.xml"))));
    }
    private JSONObject settings() throws Exception {
        JSONObject all = new JSONObject();
        for (String name : new String[]{"state", "updates"}) {
            Map<String, ?> values = target.getSharedPreferences(name, 0).getAll();
            JSONObject group = new JSONObject();
            String[] keys = name.equals("state") ? new String[]{"fix_enabled", "dashboard_cpu_fix_enabled", "lid_guard_enabled"}
                    : new String[]{"auto_check", "include_prereleases", "last_check", "prompted_tag"};
            for (String key : keys) {
                require(values.containsKey(key), "PREF:" + name + "." + key + " missing");
                Object value = values.get(key);
                Object expected = key.equals("include_prereleases") ? Boolean.TRUE :
                        key.equals("last_check") ? Long.valueOf(LAST_CHECK) :
                        key.equals("prompted_tag") ? "v1.7.2" : Boolean.FALSE;
                require(expected.getClass().equals(value.getClass()) && expected.equals(value), "PREF:" + name + "." + key);
                group.put(key, value);
            }
            all.put(name, group);
        }
        return all;
    }
    private PackageInfo identity() throws Exception {
        require(TARGET.equals(target.getPackageName()), "IDENTITY:target");
        PackageInfo info = target.getPackageManager().getPackageInfo(TARGET, 0);
        require(args.getString("version").equals(info.versionName), "IDENTITY:version");
        require(Integer.parseInt(args.getString("code")) == info.versionCode, "IDENTITY:code");
        Class<?> daemon = target.getClassLoader().loadClass(TARGET + ".DaemonIdentity");
        require(info.versionName.equals(daemon.getField("VERSION").get(null)), "IDENTITY:daemon-version");
        require(info.versionName.equals(daemon.getField("RUNTIME_ID").get(null)), "IDENTITY:daemon-runtime");
        return info;
    }
    private void visible(String text, boolean click) {
        long deadline = SystemClock.uptimeMillis() + 5000;
        do {
            AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
            if (root != null) {
                for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(text)) {
                    if (text.contentEquals(node.getText() == null ? "" : node.getText()) && node.isVisibleToUser()) {
                        if (click) require(node.performAction(AccessibilityNodeInfo.ACTION_CLICK), "UI:click " + text);
                        node.recycle(); root.recycle(); return;
                    }
                    node.recycle();
                }
                root.recycle();
            }
            SystemClock.sleep(100);
        } while (SystemClock.uptimeMillis() < deadline);
        throw new AssertionError("UI:missing " + text);
    }
    private void showSettings() {
        runOnMainSync(() -> {
            try {
                Field field = activity.getClass().getDeclaredField("updater"); field.setAccessible(true);
                Object updater = field.get(activity);
                Method method = updater.getClass().getDeclaredMethod("showSettings"); method.setAccessible(true); method.invoke(updater);
            } catch (Exception error) { throw new RuntimeException(error); }
        });
        waitForIdleSync(); visible("Updates", false);
    }
    private void open(boolean configure) throws Exception {
        activity = startActivitySync(new Intent().setClassName(TARGET, TARGET + ".MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        SystemClock.sleep(2000); waitForIdleSync();
        require(!activity.isFinishing() && !activity.isDestroyed(), "UI:activity startup");
        visible("TRUE BOTTOM SCREEN OFF", false);
        showSettings(); visible("Automatic check when opened: OFF", false); visible("Include test pre-releases: ON", false);
        if (configure) {
            // Exercise the real settings listener twice, ending with automatic checks OFF.
            visible("Automatic check when opened: OFF", true); waitForIdleSync();
            require(target.getSharedPreferences("updates", 0).getBoolean("auto_check", false), "UI:automatic enabled");
            showSettings(); visible("Automatic check when opened: ON", true); waitForIdleSync();
            require(!target.getSharedPreferences("updates", 0).getBoolean("auto_check", true), "UI:automatic disabled");
            require(target.getSharedPreferences("updates", 0).edit().commit(), "UI:preferences flushed");
        } else { sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); waitForIdleSync(); }
        settings(); require(hash(read(data())).equals(hash(expectedData())), "DATA:file after launch");
    }
    private JSONObject verify(boolean strictXml) throws Exception {
        PackageInfo info = identity();
        JSONObject preferences = settings();
        require(data().isFile(), "DATA:file missing");
        require(hash(read(data())).equals(hash(expectedData())), "DATA:file hash");
        require(checkpoint().isFile(), "DATA:checkpoint missing");
        JSONObject before = new JSONObject(new String(read(checkpoint()), StandardCharsets.UTF_8));
        require(args.getString("nonce").equals(before.getString("nonce")), "DATA:checkpoint generation");
        require(info.applicationInfo.uid == before.getInt("uid"), "INSTALL:UID changed");
        require(info.firstInstallTime == before.getLong("firstInstallTime"), "INSTALL:first install changed");
        require(info.applicationInfo.dataDir.equals(before.getString("dataDir")), "INSTALL:data directory changed");
        JSONObject xml = xmlHashes();
        if (strictXml) {
            require(xml.getString("state").equals(before.getJSONObject("xml").getString("state")), "XML:state changed during install");
            require(xml.getString("updates").equals(before.getJSONObject("xml").getString("updates")), "XML:updates changed during install");
        }
        return new JSONObject().put("version", info.versionName).put("code", info.versionCode)
                .put("preferences", preferences).put("privateDataSha256", hash(read(data())))
                .put("uidPreserved", true).put("firstInstallTimePreserved", true).put("dataDirectoryPreserved", true)
                .put("preferenceXmlUnchangedDuringInstall", strictXml);
    }
    @Override public void onStart() {
        target = getTargetContext(); Bundle result = new Bundle();
        String mode = args.getString("mode");
        try {
            PackageInfo info = identity();
            if (mode.equals("seed")) {
                require(target.getSharedPreferences("state", 0).edit().putBoolean("fix_enabled", false)
                        .putBoolean("dashboard_cpu_fix_enabled", false).putBoolean("lid_guard_enabled", false).commit(), "SEED:state");
                require(target.getSharedPreferences("updates", 0).edit().putBoolean("auto_check", false)
                        .putBoolean("include_prereleases", true).putLong("last_check", LAST_CHECK).putString("prompted_tag", "v1.7.2").commit(), "SEED:updates");
                write(data(), expectedData()); open(true);
                JSONObject before = new JSONObject().put("nonce", args.getString("nonce")).put("uid", info.applicationInfo.uid)
                        .put("firstInstallTime", info.firstInstallTime).put("dataDir", info.applicationInfo.dataDir).put("xml", xmlHashes());
                write(checkpoint(), before.toString().getBytes(StandardCharsets.UTF_8));
            } else if (mode.equals("tamper_pref") || mode.equals("restore_pref")) {
                require(target.getSharedPreferences("updates", 0).edit().putBoolean("auto_check", mode.equals("tamper_pref")).commit(), "CONTROL:preference write");
            } else if (mode.equals("tamper_data")) {
                write(data(), "corrupted control".getBytes(StandardCharsets.UTF_8));
            } else if (mode.equals("restore_data")) { write(data(), expectedData()); }
            else if (!mode.equals("verify") && !mode.equals("open")) { throw new AssertionError("MODE:unknown"); }
            if (mode.equals("seed") || mode.equals("verify") || mode.equals("open")) {
                JSONObject snapshot = verify("true".equals(args.getString("strictXml")));
                if (mode.equals("open")) { open(false); snapshot = verify(false).put("realActivityReopened", true).put("realUpdaterSettingsRead", true); }
                snapshot.put("checks", checks);
                write(new File(target.getExternalFilesDir(null), "recovery-pair-" + mode + ".json"), snapshot.toString().getBytes(StandardCharsets.UTF_8));
            }
            result.putString("result", "PASS"); result.putInt("checks", checks);
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("result", "FAIL"); result.putString("error", error.toString());
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}
