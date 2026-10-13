package android.system;
import java.io.FileDescriptor;

/**
 * Deterministic filesystem metadata. open() ALWAYS fails before creating a
 * native descriptor, so the production method can be tested safely on the host.
 */
public final class Os {
    public static int appUid=10042, filesUid=10042;
    public static int appMode=OsConstants.S_IFDIR|0700, filesMode=OsConstants.S_IFDIR|0700;
    public static boolean failStat;
    public static int openCalls, lastOpenFlags;
    public static String lastOpenPath;
    public static void reset() {
        appUid=10042; filesUid=10042;
        appMode=OsConstants.S_IFDIR|0700;
        filesMode=OsConstants.S_IFDIR|0700;
        failStat=false; openCalls=0; lastOpenFlags=0; lastOpenPath=null;
    }
    public static StructStat stat(String path) throws ErrnoException {
        if (failStat) throw new ErrnoException("stat", OsConstants.EACCES);
        if (path.equals("/data/user/0/com.thor.displaypowertest"))
            return new StructStat(appUid, appUid, appMode);
        if (path.equals("/data/user/0/com.thor.displaypowertest/files"))
            return new StructStat(filesUid, filesUid, filesMode);
        throw new ErrnoException("stat", OsConstants.ENOENT);
    }
    public static StructStat lstat(String path) throws ErrnoException {
        throw new ErrnoException("lstat", OsConstants.ENOENT);
    }
    public static FileDescriptor open(String path, int flags, int mode) throws ErrnoException {
        openCalls++; lastOpenFlags=flags; lastOpenPath=path;
        throw new ErrnoException("open intentionally blocked on host", OsConstants.EACCES);
    }
    public static StructStat fstat(FileDescriptor fd) { throw new AssertionError("No native fd"); }
    public static void fchown(FileDescriptor fd,int uid,int gid) { throw new AssertionError("No native fd"); }
    public static void fchmod(FileDescriptor fd,int mode) { throw new AssertionError("No native fd"); }
    public static void close(FileDescriptor fd) { throw new AssertionError("No native fd"); }
    public static void chown(String path,int uid,int gid) { throw new AssertionError("No native write"); }
    public static void chmod(String path,int mode) { throw new AssertionError("No native write"); }
}
