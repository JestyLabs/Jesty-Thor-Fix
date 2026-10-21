package android.content.pm;

/** Metadata only; never parses or installs an APK. */
public class PackageInfo {
    public String packageName;
    public String versionName;
    public SigningInfo signingInfo;
    public long code;
    public long getLongVersionCode() { return code; }
}
