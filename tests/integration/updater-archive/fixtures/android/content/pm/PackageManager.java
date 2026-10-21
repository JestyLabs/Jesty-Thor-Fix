package android.content.pm;

import java.util.ArrayList;
import java.util.List;

/** Records metadata queries; no installer, filesystem, network or Android process. */
public class PackageManager {
    public static final int GET_SIGNING_CERTIFICATES = 0x08000000;
    public final List<String> calls = new ArrayList<>();
    public PackageInfo archive, installed, installedSigned;
    public NameNotFoundException signedFailure;
    public PackageInfo getPackageArchiveInfo(String path, int flags) {
        calls.add("archive:" + flags);
        return archive;
    }
    public PackageInfo getPackageInfo(String name, int flags) throws NameNotFoundException {
        calls.add("installed:" + flags);
        if (!"com.thor.displaypowertest".equals(name)) throw new AssertionError("Unexpected package query");
        if (flags == 0) return installed;
        if (flags != GET_SIGNING_CERTIFICATES) throw new AssertionError("Unexpected metadata flags");
        if (signedFailure != null) throw signedFailure;
        return installedSigned;
    }
    public PackageInstaller getPackageInstaller() {
        throw new AssertionError("Archive verification must not reach the installer");
    }
    public static class NameNotFoundException extends Exception {
        public NameNotFoundException(String message) { super(message); }
    }
}
