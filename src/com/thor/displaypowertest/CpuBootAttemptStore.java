package com.thor.displaypowertest;

import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Durable app-private marker for one CPU-fix compositor restart attempt.
 *
 * The privileged daemon writes inside the app's files/ directory, so every
 * final-component open is O_NOFOLLOW and every inode is revalidated before
 * root reads, replaces or removes it.
 */
public final class CpuBootAttemptStore {
    private static final String DATA_DIR = "/data/user/0/com.thor.displaypowertest";
    private static final String FILES_DIR = DATA_DIR + "/files";
    private static final String MARKER_PATH = FILES_DIR + "/jesty-thor-cpu-boot-attempt-v1";
    private static final String TEMP_PATH = MARKER_PATH + ".tmp";
    private static final int MAX_BYTES = 512;

    public enum State { ABSENT, VALID, CORRUPT }

    public static final class ReadResult {
        public final State state;
        public final CpuBootAttemptModel.Attempt attempt;

        private ReadResult(State state, CpuBootAttemptModel.Attempt attempt) {
            this.state = state;
            this.attempt = attempt;
        }

        public static ReadResult absent() { return new ReadResult(State.ABSENT, null); }
        public static ReadResult valid(CpuBootAttemptModel.Attempt attempt) {
            return new ReadResult(State.VALID, attempt);
        }
        public static ReadResult corrupt() { return new ReadResult(State.CORRUPT, null); }
    }

    private CpuBootAttemptStore() {}

    public static String path() { return MARKER_PATH; }

    /**
     * Reads and strictly validates the current marker. Any unexpected object,
     * ownership/mode problem, oversized content or parse failure is CORRUPT,
     * never equivalent to an absent marker.
     */
    public static ReadResult read() {
        FileDescriptor fd = null;
        try {
            DirectoryInfo dirs = trustedDirectories();
            fd = Os.open(MARKER_PATH, OsConstants.O_RDONLY
                    | OsConstants.O_CLOEXEC | OsConstants.O_NOFOLLOW, 0);
            StructStat stat = Os.fstat(fd);
            if (!trustedMarker(stat, dirs.appUid)) return ReadResult.corrupt();
            if (stat.st_size <= 0L || stat.st_size > MAX_BYTES) return ReadResult.corrupt();

            if (stat.st_uid == 0) {
                Os.fchown(fd, dirs.appUid, dirs.filesGid);
                Os.fchmod(fd, 0600);
            }

            byte[] data = new byte[(int) stat.st_size];
            int offset = 0;
            while (offset < data.length) {
                int read = Os.read(fd, data, offset, data.length - offset);
                if (read <= 0) return ReadResult.corrupt();
                offset += read;
            }

            String text = new String(data, StandardCharsets.US_ASCII);
            CpuBootAttemptModel.Attempt attempt = CpuBootAttemptModel.Attempt.decode(text);
            String canonical = attempt.encode();
            if (!text.equals(canonical) && !text.equals(canonical + "\n")) {
                return ReadResult.corrupt();
            }
            return ReadResult.valid(attempt);
        } catch (ErrnoException error) {
            return error.errno == OsConstants.ENOENT ? ReadResult.absent() : ReadResult.corrupt();
        } catch (Throwable ignored) {
            return ReadResult.corrupt();
        } finally {
            if (fd != null) try { Os.close(fd); } catch (Throwable ignored) {}
        }
    }

    /**
     * Atomically replaces the marker and fsyncs both file contents and the
     * containing directory. The caller must persist RESTART_REQUESTED before
     * issuing ctl.restart.
     */
    public static void write(CpuBootAttemptModel.Attempt attempt) throws IOException {
        if (attempt == null) throw new IOException("CPU boot attempt is null");
        byte[] data = (attempt.encode() + "\n").getBytes(StandardCharsets.US_ASCII);
        if (data.length <= 0 || data.length > MAX_BYTES) {
            throw new IOException("CPU boot attempt marker too large");
        }

        FileDescriptor fd = null;
        int appUid = -1;
        try {
            DirectoryInfo dirs = trustedDirectories();
            appUid = dirs.appUid;
            removeTrustedTempIfPresent(dirs);
            checkReplaceableTarget(dirs);

            try {
                fd = Os.open(TEMP_PATH, OsConstants.O_WRONLY | OsConstants.O_CREAT
                        | OsConstants.O_EXCL | OsConstants.O_CLOEXEC | OsConstants.O_NOFOLLOW,
                        0600);
            } catch (ErrnoException error) {
                if (error.errno != OsConstants.EEXIST) throw error;
                removeTrustedTempIfPresent(dirs);
                fd = Os.open(TEMP_PATH, OsConstants.O_WRONLY | OsConstants.O_CREAT
                        | OsConstants.O_EXCL | OsConstants.O_CLOEXEC | OsConstants.O_NOFOLLOW,
                        0600);
            }

            StructStat temp = Os.fstat(fd);
            // Keep the temp inode root-owned while its contents are prepared.
            // The unprivileged app must never get a writable race against the
            // privileged daemon before the atomic rename.
            if (!OsConstants.S_ISREG(temp.st_mode) || temp.st_nlink != 1
                    || temp.st_uid != 0 || (temp.st_mode & 0777) != 0600) {
                throw new IOException("Untrusted CPU boot attempt temp inode");
            }

            int offset = 0;
            while (offset < data.length) {
                int written = Os.write(fd, data, offset, data.length - offset);
                if (written <= 0) throw new IOException("Short CPU boot attempt marker write");
                offset += written;
            }
            Os.fsync(fd);

            checkReplaceableTarget(dirs);
            Os.rename(TEMP_PATH, MARKER_PATH);

            // Finalize ownership only after the root-owned temp inode is at
            // the marker path. The still-open fd refers to that exact inode.
            Os.fchown(fd, dirs.appUid, dirs.filesGid);
            Os.fchmod(fd, 0600);
            Os.fsync(fd);

            StructStat marker = Os.fstat(fd);
            StructStat pathMarker = Os.lstat(MARKER_PATH);
            if (!trustedMarker(marker, dirs.appUid)
                    || marker.st_uid != dirs.appUid
                    || !trustedMarker(pathMarker, dirs.appUid)
                    || pathMarker.st_uid != dirs.appUid
                    || marker.st_ino != pathMarker.st_ino
                    || marker.st_dev != pathMarker.st_dev) {
                throw new IOException("CPU boot attempt marker verification failed");
            }
            Os.close(fd);
            fd = null;
            fsyncDirectory();
        } catch (Throwable error) {
            if (error instanceof IOException) throw (IOException) error;
            throw new IOException("Cannot persist CPU boot attempt marker", error);
        } finally {
            if (fd != null) try { Os.close(fd); } catch (Throwable ignored) {}
            if (appUid >= 10000) {
                try {
                    DirectoryInfo dirs = trustedDirectories();
                    if (dirs.appUid == appUid) removeTrustedTempIfPresent(dirs);
                } catch (Throwable ignored) {}
            }
        }
    }

    /**
     * Removes only a trusted marker inode. Used for a marker proven stale by
     * kernel boot_id; unexpected objects fail closed instead of being deleted.
     */
    public static void deleteTrusted() throws IOException {
        try {
            DirectoryInfo dirs = trustedDirectories();
            StructStat stat;
            try {
                stat = Os.lstat(MARKER_PATH);
            } catch (ErrnoException error) {
                if (error.errno == OsConstants.ENOENT) return;
                throw error;
            }
            if (!trustedMarker(stat, dirs.appUid)) {
                throw new IOException("Refusing to delete untrusted CPU boot attempt marker");
            }
            Os.unlink(MARKER_PATH);
            fsyncDirectory();
        } catch (Throwable error) {
            if (error instanceof IOException) throw (IOException) error;
            throw new IOException("Cannot delete CPU boot attempt marker", error);
        }
    }

    private static DirectoryInfo trustedDirectories() throws Exception {
        StructStat data = Os.lstat(DATA_DIR);
        StructStat files = Os.lstat(FILES_DIR);
        int appUid = data.st_uid;
        if (!OsConstants.S_ISDIR(data.st_mode)
                || !OsConstants.S_ISDIR(files.st_mode)
                || !IpcPeerPolicy.trustedDirectory(appUid, files.st_uid)) {
            throw new IOException("Untrusted app data directory for CPU boot attempt");
        }
        return new DirectoryInfo(appUid, files.st_gid);
    }

    private static boolean trustedMarker(StructStat stat, int appUid) {
        return appUid >= 10000
                && OsConstants.S_ISREG(stat.st_mode)
                && stat.st_nlink == 1
                && (stat.st_uid == appUid || stat.st_uid == 0)
                && (stat.st_mode & 0777) == 0600;
    }

    private static void checkReplaceableTarget(DirectoryInfo dirs) throws Exception {
        try {
            StructStat current = Os.lstat(MARKER_PATH);
            if (!trustedMarker(current, dirs.appUid)) {
                throw new IOException("Refusing to replace untrusted CPU boot attempt marker");
            }
        } catch (ErrnoException error) {
            if (error.errno != OsConstants.ENOENT) throw error;
        }
    }

    private static void removeTrustedTempIfPresent(DirectoryInfo dirs) throws Exception {
        StructStat temp;
        try {
            temp = Os.lstat(TEMP_PATH);
        } catch (ErrnoException error) {
            if (error.errno == OsConstants.ENOENT) return;
            throw error;
        }
        if (!trustedMarker(temp, dirs.appUid)) {
            throw new IOException("Refusing to remove untrusted CPU boot attempt temp file");
        }
        Os.unlink(TEMP_PATH);
    }

    private static void fsyncDirectory() throws Exception {
        FileDescriptor dir = null;
        try {
            dir = Os.open(FILES_DIR, OsConstants.O_RDONLY | OsConstants.O_DIRECTORY
                    | OsConstants.O_CLOEXEC | OsConstants.O_NOFOLLOW, 0);
            Os.fsync(dir);
        } finally {
            if (dir != null) try { Os.close(dir); } catch (Throwable ignored) {}
        }
    }

    private static final class DirectoryInfo {
        final int appUid;
        final int filesGid;

        DirectoryInfo(int appUid, int filesGid) {
            this.appUid = appUid;
            this.filesGid = filesGid;
        }
    }
}
