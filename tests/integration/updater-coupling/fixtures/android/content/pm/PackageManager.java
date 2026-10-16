package android.content.pm;
public class PackageManager {
    private final PackageInstaller installer=new PackageInstaller();
    public PackageInstaller getPackageInstaller() { return installer; }
}
