package android.system;
public final class OsConstants {
    public static final int O_CREAT=0x40, O_RDWR=0x2, O_CLOEXEC=0x80000, O_NOFOLLOW=0x20000;
    public static final int ENOENT=2, EACCES=13;
    public static final int S_IFDIR=0040000, S_IFREG=0100000, S_IFSOCK=0140000;
    public static boolean S_ISDIR(int mode) { return (mode & 0170000) == S_IFDIR; }
    public static boolean S_ISREG(int mode) { return (mode & 0170000) == S_IFREG; }
    public static boolean S_ISSOCK(int mode) { return (mode & 0170000) == S_IFSOCK; }
}
