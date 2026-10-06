package com.thor.displaypowertest;

import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;

import java.io.FileDescriptor;
import java.nio.charset.StandardCharsets;

/** Strict read-only view of the boot-scoped attempt written by the stock pservice hook. */
public final class EarlyCpuBootAttemptStore {
    private static final int MAX_BYTES = 512;

    public enum State { ABSENT, VALID, CORRUPT }

    public static final class ReadResult {
        public final State state;
        public final CpuBootAttemptModel.Attempt attempt;

        private ReadResult(State state, CpuBootAttemptModel.Attempt attempt) {
            this.state = state;
            this.attempt = attempt;
        }

        static ReadResult absent() { return new ReadResult(State.ABSENT, null); }
        static ReadResult valid(CpuBootAttemptModel.Attempt attempt) {
            return new ReadResult(State.VALID, attempt);
        }
        static ReadResult corrupt() { return new ReadResult(State.CORRUPT, null); }
    }

    private EarlyCpuBootAttemptStore() {}

    public static ReadResult read() {
        FileDescriptor fd = null;
        try {
            fd = Os.open(EarlyCpuBootHookScript.ATTEMPT_PATH,
                    OsConstants.O_RDONLY | OsConstants.O_CLOEXEC | OsConstants.O_NOFOLLOW, 0);
            StructStat stat = Os.fstat(fd);
            if (!OsConstants.S_ISREG(stat.st_mode)
                    || stat.st_nlink != 1
                    || stat.st_uid != 0
                    || stat.st_gid != 0
                    || (stat.st_mode & 0777) != 0600
                    || stat.st_size <= 0L
                    || stat.st_size > MAX_BYTES) {
                return ReadResult.corrupt();
            }

            byte[] bytes = new byte[(int) stat.st_size];
            int offset = 0;
            while (offset < bytes.length) {
                int read = Os.read(fd, bytes, offset, bytes.length - offset);
                if (read <= 0) return ReadResult.corrupt();
                offset += read;
            }

            String text = new String(bytes, StandardCharsets.US_ASCII);
            CpuBootAttemptModel.Attempt attempt = CpuBootAttemptModel.Attempt.decode(text);
            String canonical = attempt.encode();
            if (!text.equals(canonical) && !text.equals(canonical + "\n")) {
                return ReadResult.corrupt();
            }
            return ReadResult.valid(attempt);
        } catch (ErrnoException error) {
            return error.errno == OsConstants.ENOENT
                    ? ReadResult.absent() : ReadResult.corrupt();
        } catch (Throwable ignored) {
            return ReadResult.corrupt();
        } finally {
            if (fd != null) try { Os.close(fd); } catch (Throwable ignored) {}
        }
    }
}
