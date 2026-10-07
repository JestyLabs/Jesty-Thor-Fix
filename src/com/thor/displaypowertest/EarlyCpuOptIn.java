package com.thor.displaypowertest;

import android.content.Context;
import android.os.Process;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * Per-install Direct-Boot identity and enable marker for the early CPU prototype.
 *
 * The identity survives app updates but is removed with app data on uninstall.
 * The opt-in file exists only while the saved CPU Fix preference is ON.
 */
public final class EarlyCpuOptIn {
    private static final String IDENTITY_NAME = "jesty-thor-early-cpu-install-v1";
    private static final String OPT_IN_NAME = "jesty-thor-early-cpu-optin-v1";
    private static final String FILES_PATH =
            "/data/user_de/0/com.thor.displaypowertest/files";
    private static final String IDENTITY_PATH = FILES_PATH + "/" + IDENTITY_NAME;
    private static final int MAX_BYTES = 80;
    private static final SecureRandom RANDOM = new SecureRandom();

    public static final class Identity {
        public final int uid;
        public final String token;

        Identity(int uid, String token) {
            this.uid = uid;
            this.token = token;
        }

        String encoded() { return "v1:" + token + "\n"; }
    }

    private EarlyCpuOptIn() {}

    /** App-process entry point. Keeps the Direct-Boot marker aligned with the saved toggle. */
    public static synchronized Identity setEnabled(Context context, boolean enabled)
            throws IOException {
        Context dp = context.createDeviceProtectedStorageContext();
        File files = dp.getFilesDir();
        if (files == null || !FILES_PATH.equals(files.getAbsolutePath())) {
            throw new IOException("Unexpected device-protected files path");
        }

        Identity identity = readIdentity(IDENTITY_PATH, Process.myUid());
        if (identity == null) {
            identity = new Identity(Process.myUid(), newToken());
            writePrivate(dp, IDENTITY_NAME, identity.encoded());
            Identity verified = readIdentity(IDENTITY_PATH, Process.myUid());
            if (verified == null || !verified.token.equals(identity.token)) {
                throw new IOException("Direct-Boot identity verification failed");
            }
            identity = verified;
        }

        if (enabled) {
            Identity opt = readIdentity(EarlyCpuBootHookScript.OPT_IN_PATH, Process.myUid());
            if (opt == null) {
                writePrivate(dp, OPT_IN_NAME, identity.encoded());
            } else if (!opt.token.equals(identity.token)) {
                throw new IOException("Unexpected early CPU opt-in identity");
            }
            Identity verified = readIdentity(
                    EarlyCpuBootHookScript.OPT_IN_PATH, Process.myUid());
            if (verified == null || !verified.token.equals(identity.token)) {
                throw new IOException("Early CPU opt-in verification failed");
            }
        } else {
            removeExact(dp, OPT_IN_NAME, identity);
        }
        return identity;
    }

    /** Root-daemon read used only to build/verify the next-boot hook. */
    public static Identity readIdentityForRoot() {
        try {
            return readIdentity(IDENTITY_PATH, -1);
        } catch (IOException ignored) {
            return null;
        }
    }

    /** Root-daemon proof that the current installation is explicitly opted in. */
    public static boolean rootOptInMatches(Identity identity) {
        if (identity == null) return false;
        try {
            Identity opt = readIdentity(EarlyCpuBootHookScript.OPT_IN_PATH, identity.uid);
            return opt != null && identity.token.equals(opt.token);
        } catch (IOException ignored) {
            return false;
        }
    }

    private static Identity readIdentity(String path, int expectedUid) throws IOException {
        FileDescriptor fd = null;
        try {
            fd = Os.open(path, OsConstants.O_RDONLY
                    | OsConstants.O_CLOEXEC | OsConstants.O_NOFOLLOW, 0);
            StructStat stat = Os.fstat(fd);
            if (!OsConstants.S_ISREG(stat.st_mode)
                    || stat.st_nlink != 1
                    || stat.st_uid < 10000
                    || (expectedUid >= 10000 && stat.st_uid != expectedUid)
                    || (stat.st_mode & 0777) != 0600
                    || stat.st_size <= 0L
                    || stat.st_size > MAX_BYTES) {
                throw new IOException("Untrusted Direct-Boot identity inode");
            }

            byte[] data = new byte[(int) stat.st_size];
            int offset = 0;
            while (offset < data.length) {
                int read = Os.read(fd, data, offset, data.length - offset);
                if (read <= 0) throw new IOException("Short Direct-Boot identity read");
                offset += read;
            }

            String text = new String(data, StandardCharsets.US_ASCII);
            String trimmed = text.endsWith("\n")
                    ? text.substring(0, text.length() - 1) : text;
            if (!trimmed.matches("v1:[0-9a-f]{64}")
                    || !(text.equals(trimmed) || text.equals(trimmed + "\n"))) {
                throw new IOException("Invalid Direct-Boot identity contents");
            }
            return new Identity((int) stat.st_uid, trimmed.substring(3));
        } catch (ErrnoException error) {
            if (error.errno == OsConstants.ENOENT) return null;
            throw new IOException("Cannot open Direct-Boot identity", error);
        } finally {
            if (fd != null) try { Os.close(fd); } catch (Throwable ignored) {}
        }
    }

    private static void writePrivate(Context dp, String name, String text)
            throws IOException {
        File target = new File(dp.getFilesDir(), name);
        File temp = new File(dp.getFilesDir(), name + ".tmp");
        if (temp.exists()) {
            Identity stale;
            try {
                stale = readIdentity(temp.getAbsolutePath(), Process.myUid());
            } catch (IOException error) {
                throw new IOException("Untrusted Direct-Boot temp file", error);
            }
            if (stale != null && !temp.delete()) {
                throw new IOException("Cannot remove trusted Direct-Boot temp file");
            }
        }

        byte[] data = text.getBytes(StandardCharsets.US_ASCII);
        FileDescriptor fd = null;
        try {
            fd = Os.open(temp.getAbsolutePath(),
                    OsConstants.O_WRONLY | OsConstants.O_CREAT | OsConstants.O_EXCL
                    | OsConstants.O_CLOEXEC | OsConstants.O_NOFOLLOW, 0600);
            StructStat initial = Os.fstat(fd);
            if (!OsConstants.S_ISREG(initial.st_mode)
                    || initial.st_nlink != 1
                    || initial.st_uid != Process.myUid()
                    || (initial.st_mode & 0777) != 0600
                    || initial.st_size != 0L) {
                throw new IOException("Untrusted Direct-Boot temp inode");
            }
            int offset = 0;
            while (offset < data.length) {
                int written = Os.write(fd, data, offset, data.length - offset);
                if (written <= 0) throw new IOException("Short Direct-Boot identity write");
                offset += written;
            }
            Os.fsync(fd);
            StructStat complete = Os.fstat(fd);
            if (complete.st_size != data.length) {
                throw new IOException("Direct-Boot temp verification failed");
            }
        } catch (ErrnoException error) {
            throw new IOException("Cannot create Direct-Boot temp file", error);
        } finally {
            if (fd != null) try { Os.close(fd); } catch (Throwable ignored) {}
        }

        try {
            Os.rename(temp.getAbsolutePath(), target.getAbsolutePath());
        } catch (ErrnoException error) {
            throw new IOException("Cannot atomically replace Direct-Boot identity", error);
        }
    }

    private static void removeExact(Context dp, String name, Identity expected)
            throws IOException {
        File target = new File(dp.getFilesDir(), name);
        Identity current = readIdentity(target.getAbsolutePath(), Process.myUid());
        if (current == null) return;
        if (!current.token.equals(expected.token)) {
            throw new IOException("Refusing to remove unexpected Direct-Boot opt-in");
        }
        if (!target.delete()) throw new IOException("Cannot remove Direct-Boot opt-in");
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        StringBuilder out = new StringBuilder(64);
        for (byte value : bytes) {
            out.append(Character.forDigit((value >>> 4) & 0x0f, 16));
            out.append(Character.forDigit(value & 0x0f, 16));
        }
        return out.toString();
    }
}
