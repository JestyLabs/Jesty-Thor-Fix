package com.thor.displaypowertest;

import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;

import java.io.FileDescriptor;
import java.nio.charset.StandardCharsets;

/**
 * Root-written diagnostics in /data/local/tmp. That directory belongs to the
 * shell user, which could plant a symlink or its own file under these names.
 * Root writes therefore never follow a final-component link and only append to
 * a regular, single-link file owned by root. The files stay world-readable so
 * the read-only ADB collector can still copy them; they contain no secrets.
 */
public final class RootLogFiles {
    public static final String BOOT_TRACE = "/data/local/tmp/jesty-thor-boot-trace.log";
    public static final String DAEMON_LOG = "/data/local/tmp/td032.log";

    /**
     * mksh builtins only, so the check forks nothing: true when the path is
     * absent, or is a regular non-link file owned by the effective (root) UID.
     * A shell check cannot close the race with a concurrent swap; it narrows it
     * and turns a planted link or foreign file into a skipped write.
     */
    public static final String SHELL_GUARD = "safe_log(){ [ ! -L \"$1\" ] && { [ ! -e \"$1\" ]"
            + " || { [ -f \"$1\" ] && [ -O \"$1\" ]; }; }; }";

    private RootLogFiles() {}

    /** Appends one line; false when the path is unsafe or the write failed. */
    public static boolean append(String path, String text) {
        FileDescriptor fd = null;
        try {
            fd = Os.open(path, OsConstants.O_WRONLY | OsConstants.O_APPEND | OsConstants.O_CREAT
                    | OsConstants.O_NOFOLLOW | OsConstants.O_CLOEXEC, 0644);
            StructStat stat = Os.fstat(fd);
            if (!trusted(stat)) return false;
            Os.fchmod(fd, 0644);
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            int offset = 0;
            while (offset < bytes.length) {
                int written = Os.write(fd, bytes, offset, bytes.length - offset);
                if (written <= 0) return false;
                offset += written;
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        } finally {
            if (fd != null) try { Os.close(fd); } catch (Throwable ignored) {}
        }
    }

    /** rename(2) moves a link itself, but only rotate a trusted regular file. */
    public static void rotateIfLarger(String path, long maxBytes) {
        try {
            StructStat stat = Os.lstat(path);
            if (trusted(stat) && stat.st_size > maxBytes) Os.rename(path, path + ".1");
        } catch (ErrnoException ignored) {
            // Absent or unreadable: nothing to rotate.
        } catch (Throwable ignored) {}
    }

    private static boolean trusted(StructStat stat) {
        return OsConstants.S_ISREG(stat.st_mode) && stat.st_uid == 0 && stat.st_nlink == 1;
    }
}
