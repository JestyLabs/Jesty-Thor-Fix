package com.thor.displaypowertest;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/** Disposable APK only: native Android JSON/cache with controlled HTTP responses. */
final class UpdateMetadataCharacterization {
    private static final String HASH = new String(new char[64]).replace('\0', 'a');
    private static final String BASE = "https://github.com/JestyLabs/Jesty-Thor-Fix/releases/download/";
    private static final String API = "https://api.github.com/repos/JestyLabs/Jesty-Thor-Fix";
    private static byte[] response;
    private static String fault;
    private static Connection last;
    private static int assertions;
    private static final JSONArray results = new JSONArray();
    private static Class<?> metadata;
    private interface Action { void run() throws Exception; }

    static HttpURLConnection connection(URL url) {
        if (!url.toString().startsWith(API + "/releases")) return null;
        return last = new Connection(url);
    }
    private static final class Connection extends HttpURLConnection {
        boolean disconnected, closed;
        Connection(URL url) { super(url); }
        public void connect() { }
        public void disconnect() { disconnected = true; }
        public boolean usingProxy() { return false; }
        public int getResponseCode() throws IOException {
            if ("status_io".equals(fault)) throw new IOException("fixture status failure");
            return "status".equals(fault) ? 503 : 200;
        }
        public InputStream getInputStream() throws IOException {
            if ("open_io".equals(fault)) throw new IOException("fixture input failure");
            return new FilterInputStream(new ByteArrayInputStream(response)) {
                public int read(byte[] bytes, int off, int len) throws IOException {
                    if ("read_io".equals(fault)) throw new IOException("fixture read failure");
                    return super.read(bytes, off, len);
                }
                public void close() throws IOException { closed = true; super.close(); }
            };
        }
    }
    private static Object invoke(Class<?> type, Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = type.getDeclaredMethod(name, types); method.setAccessible(true);
        try { return method.invoke(target, args); }
        catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw error;
        }
    }
    private static Object field(Object value, String name) throws Exception {
        Field field = value.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(value);
    }
    private static void check(boolean value, String label) {
        assertions++; if (!value) throw new AssertionError(label);
    }
    private static void test(String name, Action action) throws Exception {
        action.run(); results.put(new JSONObject().put("case", name).put("result", "PASS"));
        android.util.Log.i("UpdaterMetadata", "PASS: " + name);
    }
    private static JSONObject release(String tag, boolean pre) throws Exception {
        String asset = "Jesty-Thor-Fix-" + UpdateVersion.stripV(tag) + ".apk";
        return new JSONObject().put("tag_name", tag).put("prerelease", pre).put("draft", false)
                .put("body", "Notes \u00e7 \ud83d\udd12\nsecond line")
                .put("assets", new JSONArray().put(new JSONObject().put("name", asset)
                        .put("browser_download_url", BASE + tag + "/" + asset)
                        .put("size", 4096).put("digest", "sha256:" + HASH)));
    }
    private static Object parse(JSONObject json) throws Exception {
        return invoke(metadata, null, "parseRelease", new Class<?>[]{JSONObject.class}, json);
    }
    private static Object cached(AppUpdater updater, SharedPreferences prefs, String json) throws Exception {
        prefs.edit().putString("release", json).commit();
        return invoke(AppUpdater.class, updater, "readCached", new Class<?>[0]);
    }
    private static Object fetch(AppUpdater updater, String json, boolean pre, String installed) throws Exception {
        response = json.getBytes(StandardCharsets.UTF_8); fault = null;
        return invoke(AppUpdater.class, updater, "fetchBest", new Class<?>[]{boolean.class, String.class}, pre, installed);
    }
    private static void throwsFailure(Action action, Class<?> expected, String message) throws Exception {
        try { action.run(); throw new AssertionError("Expected failure"); }
        catch (Exception error) {
            check(expected.isInstance(error), "failure type " + expected.getSimpleName());
            if (message != null) check(error.getMessage().contains(message), "failure reason");
        }
    }
    private static void clientClosed(boolean inputOpened) {
        check(last.disconnected, "HTTP disconnected");
        if (inputOpened) check(last.closed, "input closed");
        check(last.getConnectTimeout() == 15000 && last.getReadTimeout() == 30000, "timeouts preserved");
        check(last.getInstanceFollowRedirects() && !last.getUseCaches(), "connection flags preserved");
        check("application/vnd.github+json".equals(last.getRequestProperty("Accept")), "JSON accept header");
    }
    private static void performCheck(AppUpdater updater) throws Exception {
        invoke(AppUpdater.class, updater, "check", new Class<?>[]{boolean.class}, false);
        AtomicBoolean checking = (AtomicBoolean) field(updater, "checking");
        long end = System.currentTimeMillis() + 4000;
        while (checking.get() && System.currentTimeMillis() < end) Thread.sleep(5);
        check(!checking.get(), "check worker released");
    }

    static void run(UpdaterTestActivity activity, AppUpdater updater, File output) throws Exception {
        try { metadata = Class.forName("com.thor.displaypowertest.UpdateReleaseCatalog"); }
        catch (ClassNotFoundException originalBaseline) { metadata = AppUpdater.class; }
        SharedPreferences prefs = activity.getSharedPreferences("updates", 0);
        test("metadata-valid-release", () -> {
            Object value = parse(release("v1.0.1", false));
            check(value != null && "v1.0.1".equals(field(value, "tag")), "valid release accepted");
        });
        for (String mode : new String[]{"draft", "tag", "assets", "asset_name", "http", "foreign_repo", "size_zero", "size_negative", "size_over", "digest_missing", "digest_null", "digest_algorithm", "digest_short"}) {
            test("metadata-reject-" + mode, () -> {
                JSONObject json = release("v1.0.1", false); JSONObject asset = json.getJSONArray("assets").getJSONObject(0);
                if (mode.equals("draft")) json.put("draft", true);
                else if (mode.equals("tag")) json.put("tag_name", "bad");
                else if (mode.equals("assets")) json.remove("assets");
                else if (mode.equals("asset_name")) asset.put("name", "other.apk");
                else if (mode.equals("http")) asset.put("browser_download_url", BASE.replace("https:", "http:") + "v1.0.1/Jesty-Thor-Fix-1.0.1.apk");
                else if (mode.equals("foreign_repo")) asset.put("browser_download_url", BASE.replace("JestyLabs", "other") + "v1.0.1/Jesty-Thor-Fix-1.0.1.apk");
                else if (mode.equals("size_zero")) asset.put("size", 0);
                else if (mode.equals("size_negative")) asset.put("size", -1);
                else if (mode.equals("size_over")) asset.put("size", 64L * 1024 * 1024 + 1);
                else if (mode.equals("digest_missing")) asset.remove("digest");
                else if (mode.equals("digest_null")) asset.put("digest", JSONObject.NULL);
                else if (mode.equals("digest_algorithm")) asset.put("digest", "sha512:" + HASH);
                else asset.put("digest", "sha256:aa");
                check(parse(json) == null, "invalid metadata rejected");
            });
        }
        test("metadata-size-upper-bound", () -> {
            JSONObject json = release("v1.0.1", false); json.getJSONArray("assets").getJSONObject(0).put("size", 64L * 1024 * 1024);
            check(parse(json) != null, "64 MiB accepted");
        });
        test("metadata-native-json-coercion", () -> {
            JSONObject json = release("v1.0.1", false);
            json.getJSONArray("assets").getJSONObject(0).put("size", "4096");
            check(parse(json) != null, "native optLong accepts decimal string");
        });
        test("metadata-first-matching-invalid-asset", () -> {
            JSONObject json = release("v1.0.1", false);
            JSONObject good = new JSONObject(json.getJSONArray("assets").getJSONObject(0).toString());
            json.getJSONArray("assets").getJSONObject(0).remove("digest"); json.getJSONArray("assets").put(good);
            check(parse(json) == null, "first matching invalid asset does not fall through");
        });
        test("metadata-skip-unrelated-assets", () -> {
            JSONObject json = release("v1.0.1", false);
            json.put("assets", new JSONArray().put("ignored").put(new JSONObject().put("name", "readme.txt"))
                    .put(json.getJSONArray("assets").getJSONObject(0)));
            check(parse(json) != null, "unrelated assets skipped");
        });
        test("metadata-prerelease-selection", () -> {
            JSONArray json = new JSONArray().put(JSONObject.NULL).put("ignored").put(release("v1.0.2-rc1", true))
                    .put(release("v1.0.1", false)).put(release("v2.0.0", false).put("draft", true));
            check("v1.0.2-rc1".equals(field(fetch(updater, json.toString(), true, "1.0.0"), "tag")), "highest eligible release selected");
            check(last.getURL().toString().equals(API + "/releases?per_page=20"), "preview endpoint"); clientClosed(true);
        });
        test("metadata-latest-stable-and-installed", () -> {
            String json = release("v1.0.1", false).toString();
            check(fetch(updater, json, false, "1.0.0") != null, "new stable accepted");
            check(last.getURL().toString().equals(API + "/releases/latest"), "stable endpoint");
            check(fetch(updater, json, false, "1.0.1") == null, "equal ignored");
            check(fetch(updater, json, false, "1.0.2") == null, "older ignored");
            check(fetch(updater, release("v1.0.2-rc1", true).toString(), false, "1.0.0") == null, "stable mode rejects preview");
        });
        final Object good = parse(release("v1.0.1", false));
        final String cache = (String) invoke(metadata, null, "toCache", new Class<?>[]{good.getClass()}, good);
        test("metadata-cache-roundtrip-native-json", () -> {
            Object value = cached(updater, prefs, cache);
            for (String name : new String[]{"tag", "prerelease", "notes", "assetName", "apkUrl", "apkSize", "sha256"})
                check(field(good, name).equals(field(value, name)), "roundtrip " + name);
            check(new JSONObject(cache).length() == 7, "cache format retained");
        });
        test("metadata-cache-absent-and-malformed", () -> {
            prefs.edit().remove("release").commit(); check(invoke(AppUpdater.class, updater, "readCached", new Class<?>[0]) == null, "missing cache");
            check(cached(updater, prefs, "{") == null, "malformed ignored");
            check("{".equals(prefs.getString("release", null)), "malformed cache not silently evicted");
            check(cached(updater, prefs, "[]") == null, "wrong shape ignored");
        });
        for (String name : new String[]{"tag", "asset", "url", "size", "sha256"}) {
            test("metadata-cache-revalidates-" + name, () -> {
                JSONObject json = new JSONObject(cache); json.remove(name);
                check(cached(updater, prefs, json.toString()) == null, "missing trusted field");
            });
        }
        test("metadata-response-byte-boundary", () -> {
            String json = release("v1.0.1", false).toString(); byte[] prefix = json.getBytes(StandardCharsets.UTF_8);
            byte[] bytes = new byte[1024 * 1024]; java.util.Arrays.fill(bytes, (byte) ' '); System.arraycopy(prefix, 0, bytes, 0, prefix.length);
            response = bytes; fault = null;
            check(invoke(AppUpdater.class, updater, "fetchBest", new Class<?>[]{boolean.class, String.class}, false, "1.0.0") != null, "exact byte limit"); clientClosed(true);
            response = java.util.Arrays.copyOf(bytes, bytes.length + 1); response[response.length - 1] = ' ';
            throwsFailure(() -> invoke(AppUpdater.class, updater, "fetchJson", new Class<?>[]{String.class}, API + "/releases/latest"), IOException.class, "too large"); clientClosed(true);
        });
        test("metadata-response-malformed-native-json", () -> {
            throwsFailure(() -> fetch(updater, "{", false, "1.0.0"), org.json.JSONException.class, null); clientClosed(true);
            throwsFailure(() -> fetch(updater, "{}", true, "1.0.0"), org.json.JSONException.class, null);
        });
        for (String mode : new String[]{"status", "status_io", "open_io", "read_io"}) {
            test("metadata-response-" + mode, () -> {
                response = new byte[0]; fault = mode;
                throwsFailure(() -> invoke(AppUpdater.class, updater, "fetchJson", new Class<?>[]{String.class}, API + "/releases/latest"), IOException.class, mode.equals("status") ? "HTTP 503" : "fixture");
                clientClosed(mode.equals("read_io"));
            });
        }
        test("metadata-check-failure-preserves-cache", () -> {
            prefs.edit().putString("release", cache).putLong("last_check", 123).putBoolean("include_prereleases", false).commit();
            response = new byte[0]; fault = "status"; performCheck(updater);
            check(cache.equals(prefs.getString("release", null)), "failed check keeps cache");
            check(prefs.getLong("last_check", 0) == 123, "failed check keeps timestamp");
        });
        test("metadata-check-empty-clears-cache", () -> {
            prefs.edit().putString("release", cache).putBoolean("include_prereleases", true).commit();
            response = "[]".getBytes(StandardCharsets.UTF_8); fault = null; performCheck(updater);
            check(!prefs.contains("release"), "no eligible release evicts cache"); check(prefs.getLong("last_check", 0) > 123, "successful check timestamp");
        });
        test("metadata-check-valid-replaces-cache", () -> {
            prefs.edit().putString("release", "{").putString("prompted_tag", "v1.0.1").putBoolean("include_prereleases", false).commit();
            response = release("v1.0.1", false).toString().getBytes(StandardCharsets.UTF_8); fault = null; performCheck(updater);
            Object value = invoke(AppUpdater.class, updater, "readCached", new Class<?>[0]);
            check(value != null && "v1.0.1".equals(field(value, "tag")), "successful check replaces bad cache");
        });
        JSONObject summary = new JSONObject().put("cases", results).put("assertions", assertions).put("implementation", metadata.getName());
        try (FileOutputStream stream = new FileOutputStream(new File(output, "metadata-results.json"))) {
            stream.write(summary.toString().getBytes(StandardCharsets.UTF_8));
        }
        android.util.Log.i("UpdaterMetadata", "METADATA_COMPLETE");
    }
}
