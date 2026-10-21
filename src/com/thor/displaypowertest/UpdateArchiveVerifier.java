package com.thor.displaypowertest;

import com.thor.displaypowertest.UpdateReleaseCatalog.Release;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Checks Android archive metadata before AppUpdater opens an install session. */
final class UpdateArchiveVerifier {
    private UpdateArchiveVerifier() {}

    static void verify(Context context, File apk, Release release) throws Exception {
        PackageManager packages = context.getPackageManager();
        String packageName = context.getPackageName();
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
}
