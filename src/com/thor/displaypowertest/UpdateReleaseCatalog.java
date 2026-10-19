package com.thor.displaypowertest;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Release metadata and cache codec; trusted-release policy stays in UpdateVersion. */
final class UpdateReleaseCatalog {
    static final String REPOSITORY = "JestyLabs/Jesty-Thor-Fix";
    private static final int MAX_RELEASE_JSON_BYTES = 1024 * 1024;
    private static final long MAX_APK_BYTES = 64L * 1024L * 1024L;

    static final class Release {
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

    static Release select(String json, boolean includePre, String installed) throws Exception {
        List<JSONObject> objects = new ArrayList<>();
        if (includePre) {
            JSONArray releases = new JSONArray(json);
            for (int i = 0; i < releases.length(); i++) {
                JSONObject release = releases.optJSONObject(i);
                if (release != null) objects.add(release);
            }
        } else {
            objects.add(new JSONObject(json));
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

    static Release parseRelease(JSONObject release) {
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

    static String toCache(Release release) throws Exception {
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

    static Release readCached(String json) {
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

    /** Caller retains stream/connection ownership, including cleanup on read failure. */
    static String readResponse(InputStream input) throws IOException {
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

    private UpdateReleaseCatalog() { }
}
