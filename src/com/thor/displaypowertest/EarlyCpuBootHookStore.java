package com.thor.displaypowertest;

import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Root-daemon ownership store for the firmware-global boot hook. */
public final class EarlyCpuBootHookStore {
    public static final String OWNER_PATH = "/data/.jesty-thor-boot-start-owner-v1";
    private static final String HOOK_TEMP = "/data/.jesty-thor-boot-start-hook-v1.tmp";
    private static final String OWNER_TEMP = "/data/.jesty-thor-boot-start-owner-v1.tmp";
    private static final int MAX_HOOK_BYTES = 8192;
    private static final int MAX_OWNER_BYTES = 160;

    public enum State {
        ABSENT, OWNED_EXACT, OWNED_STALE, OWNED_OWNER_ONLY, OCCUPIED_UNKNOWN
    }

    private EarlyCpuBootHookStore() {}

    public static State inspect(String expectedScript) {
        try {
            byte[] hook = readTrusted(EarlyCpuBootHookScript.HOOK_PATH, MAX_HOOK_BYTES);
            byte[] owner = readTrusted(OWNER_PATH, MAX_OWNER_BYTES);
            if (hook == null && owner == null) return State.ABSENT;
            if (hook != null && owner == null) return State.OCCUPIED_UNKNOWN;

            String ownerText = owner == null ? null
                    : new String(owner, StandardCharsets.US_ASCII);
            if (hook == null) {
                return validOwnerRecord(ownerText)
                        ? State.OWNED_OWNER_ONLY : State.OCCUPIED_UNKNOWN;
            }

            String actualHash = sha256(hook);
            String canonical = "v1|" + EarlyCpuBootHookScript.MAGIC + "|" + actualHash + "\n";
            if (!canonical.equals(ownerText)) return State.OCCUPIED_UNKNOWN;

            if (expectedScript != null
                    && MessageDigest.isEqual(hook,
                    expectedScript.getBytes(StandardCharsets.US_ASCII))) {
                return State.OWNED_EXACT;
            }
            return State.OWNED_STALE;
        } catch (Throwable ignored) {
            return State.OCCUPIED_UNKNOWN;
        }
    }

    public static void installOrReplace(String script) throws IOException {
        if (script == null) throw new IOException("Hook script is null");
        byte[] hook = script.getBytes(StandardCharsets.US_ASCII);
        if (hook.length <= 0 || hook.length > MAX_HOOK_BYTES) {
            throw new IOException("Hook script size");
        }

        State state = inspect(script);
        if (state == State.OWNED_EXACT) return;
        if (state == State.OCCUPIED_UNKNOWN) {
            throw new IOException("Refusing occupied firmware-global boot hook");
        }
        if (state == State.OWNED_STALE || state == State.OWNED_OWNER_ONLY) removeManaged();

        if (inspect(null) != State.ABSENT) {
            throw new IOException("Boot hook did not become absent before install");
        }

        String ownerText = "v1|" + EarlyCpuBootHookScript.MAGIC + "|"
                + sha256(hook) + "\n";
        byte[] owner = ownerText.getBytes(StandardCharsets.US_ASCII);

        writeTemp(HOOK_TEMP, hook);
        try {
            writeTemp(OWNER_TEMP, owner);
        } catch (Throwable error) {
            unlinkExactTemp(HOOK_TEMP);
            if (error instanceof IOException) throw (IOException) error;
            throw new IOException("Cannot prepare hook ownership", error);
        }

        try {
            // Ownership metadata lands first. A crash here leaves no active hook.
            Os.rename(OWNER_TEMP, OWNER_PATH);
            fsyncDataDirectory();
            Os.rename(HOOK_TEMP, EarlyCpuBootHookScript.HOOK_PATH);
            fsyncDataDirectory();
        } catch (Throwable error) {
            unlinkExactTemp(HOOK_TEMP);
            // If the final hook never landed, remove only the exact metadata
            // written for this script. Otherwise leave the pair for inspection.
            if (!exists(EarlyCpuBootHookScript.HOOK_PATH)) {
                removeOwnerIfExact(ownerText);
            }
            if (error instanceof IOException) throw (IOException) error;
            throw new IOException("Cannot install managed boot hook", error);
        }

        if (inspect(script) != State.OWNED_EXACT) {
            throw new IOException("Managed boot hook verification failed");
        }
    }

    public static void removeManaged() throws IOException {
        State state = inspect(null);
        if (state == State.ABSENT) return;
        if (state == State.OCCUPIED_UNKNOWN) {
            throw new IOException("Refusing to remove unknown boot hook");
        }
        try {
            if (state != State.OWNED_OWNER_ONLY) {
                if (!new java.io.File(EarlyCpuBootHookScript.HOOK_PATH).delete()) {
                    throw new IOException("Cannot remove managed boot hook");
                }
                fsyncDataDirectory();
            }
            if (!new java.io.File(OWNER_PATH).delete()) {
                throw new IOException("Cannot remove managed hook owner");
            }
            fsyncDataDirectory();
        } catch (Throwable error) {
            if (error instanceof IOException) throw (IOException) error;
            throw new IOException("Cannot remove managed boot hook", error);
        }
    }

    private static void writeTemp(String path, byte[] data) throws IOException {
        if (exists(path)) throw new IOException("Unexpected hook temp object: " + path);
        FileDescriptor fd = null;
        try {
            fd = Os.open(path, OsConstants.O_WRONLY | OsConstants.O_CREAT
                    | OsConstants.O_EXCL | OsConstants.O_CLOEXEC | OsConstants.O_NOFOLLOW,
                    0600);
            StructStat stat = Os.fstat(fd);
            if (!trustedMetadata(stat) || stat.st_size != 0L) {
                throw new IOException("Untrusted hook temp inode");
            }
            int offset = 0;
            while (offset < data.length) {
                int written = Os.write(fd, data, offset, data.length - offset);
                if (written <= 0) throw new IOException("Short hook temp write");
                offset += written;
            }
            Os.fsync(fd);
            StructStat complete = Os.fstat(fd);
            if (!trustedMetadata(complete) || complete.st_size != data.length) {
                throw new IOException("Hook temp verification failed");
            }
        } catch (Throwable error) {
            if (error instanceof IOException) throw (IOException) error;
            throw new IOException("Cannot write hook temp", error);
        } finally {
            if (fd != null) try { Os.close(fd); } catch (Throwable ignored) {}
        }
    }

    private static byte[] readTrusted(String path, int maxBytes) throws IOException {
        FileDescriptor fd = null;
        try {
            fd = Os.open(path, OsConstants.O_RDONLY
                    | OsConstants.O_CLOEXEC | OsConstants.O_NOFOLLOW, 0);
            StructStat stat = Os.fstat(fd);
            if (!trusted(stat, 1, maxBytes)) throw new IOException("Untrusted hook inode");
            byte[] data = new byte[(int) stat.st_size];
            int offset = 0;
            while (offset < data.length) {
                int read = Os.read(fd, data, offset, data.length - offset);
                if (read <= 0) throw new IOException("Short hook read");
                offset += read;
            }
            return data;
        } catch (ErrnoException error) {
            if (error.errno == OsConstants.ENOENT) return null;
            throw new IOException("Cannot read hook object", error);
        } finally {
            if (fd != null) try { Os.close(fd); } catch (Throwable ignored) {}
        }
    }

    private static boolean validOwnerRecord(String value) {
        return value != null && value.matches("v1\\|" + EarlyCpuBootHookScript.MAGIC
                + "\\|[0-9a-f]{64}\\n");
    }

    private static boolean trusted(StructStat stat, long minSize, long maxSize) {
        return trustedMetadata(stat)
                && stat.st_size >= minSize
                && stat.st_size <= maxSize;
    }

    private static boolean trustedMetadata(StructStat stat) {
        return OsConstants.S_ISREG(stat.st_mode)
                && stat.st_nlink == 1
                && stat.st_uid == 0
                && stat.st_gid == 0
                && (stat.st_mode & 0777) == 0600;
    }

    private static boolean exists(String path) throws IOException {
        try {
            Os.lstat(path);
            return true;
        } catch (ErrnoException error) {
            if (error.errno == OsConstants.ENOENT) return false;
            throw new IOException("Cannot inspect " + path, error);
        }
    }

    private static void unlinkExactTemp(String path) {
        try {
            StructStat stat = Os.lstat(path);
            if (OsConstants.S_ISREG(stat.st_mode) && stat.st_nlink == 1
                    && stat.st_uid == 0 && stat.st_gid == 0
                    && (stat.st_mode & 0777) == 0600) {
                new java.io.File(path).delete();
            }
        } catch (Throwable ignored) {}
    }

    private static void removeOwnerIfExact(String ownerText) {
        try {
            byte[] data = readTrusted(OWNER_PATH, MAX_OWNER_BYTES);
            if (data != null && ownerText.equals(
                    new String(data, StandardCharsets.US_ASCII))) {
                if (new java.io.File(OWNER_PATH).delete()) {
                    fsyncDataDirectory();
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void fsyncDataDirectory() throws IOException {
        FileDescriptor fd = null;
        try {
            fd = Os.open("/data", OsConstants.O_RDONLY | OsConstants.O_CLOEXEC, 0);
            StructStat stat = Os.fstat(fd);
            if (!OsConstants.S_ISDIR(stat.st_mode)) {
                throw new IOException("/data is not a directory");
            }
            Os.fsync(fd);
        } catch (Throwable error) {
            throw new IOException("Cannot fsync /data", error);
        } finally {
            if (fd != null) try { Os.close(fd); } catch (Throwable ignored) {}
        }
    }

    private static String sha256(byte[] data) {
        final byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256").digest(data);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
        StringBuilder out = new StringBuilder(64);
        for (byte value : digest) {
            out.append(Character.forDigit((value >>> 4) & 0x0f, 16));
            out.append(Character.forDigit(value & 0x0f, 16));
        }
        return out.toString();
    }
}
