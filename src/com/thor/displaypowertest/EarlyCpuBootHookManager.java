package com.thor.displaypowertest;

import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;

import java.io.FileDescriptor;
import java.nio.charset.StandardCharsets;

/**
 * Root-daemon reconciler for the one-shot stock pservice prototype.
 *
 * It never creates the prototype gate. The user/test harness must arm that
 * separately; the early hook consumes it before mutating the CPU property.
 */
public final class EarlyCpuBootHookManager {
    private EarlyCpuBootHookManager() {}

    public static String reconcile(boolean desiredCpuFix) {
        EarlyCpuOptIn.Identity identity = EarlyCpuOptIn.readIdentityForRoot();
        boolean optIn = EarlyCpuOptIn.rootOptInMatches(identity);
        boolean gate = prototypeArmed();

        String expected = identity == null ? null
                : EarlyCpuBootHookScript.build(identity.uid, identity.token);
        EarlyCpuBootHookStore.State state = EarlyCpuBootHookStore.inspect(expected);
        boolean want = desiredCpuFix && gate && optIn && identity != null;

        try {
            if (want) {
                if (state == EarlyCpuBootHookStore.State.OCCUPIED_UNKNOWN) {
                    return "ok=0;error=EARLY_HOOK_PATH_OCCUPIED";
                }
                EarlyCpuBootHookStore.installOrReplace(expected);
                if (EarlyCpuBootHookStore.inspect(expected)
                        != EarlyCpuBootHookStore.State.OWNED_EXACT) {
                    return "ok=0;error=EARLY_HOOK_INSTALL_UNVERIFIED";
                }
                return "ok=1;early_hook="
                        + (state == EarlyCpuBootHookStore.State.OWNED_EXACT
                        ? "kept" : "installed")
                        + ";gate=1;optin=1";
            }

            if (state == EarlyCpuBootHookStore.State.OWNED_EXACT
                    || state == EarlyCpuBootHookStore.State.OWNED_STALE
                    || state == EarlyCpuBootHookStore.State.OWNED_OWNER_ONLY) {
                EarlyCpuBootHookStore.removeManaged();
                return "ok=1;early_hook=removed;gate=" + (gate ? "1" : "0")
                        + ";optin=" + (optIn ? "1" : "0");
            }

            if (state == EarlyCpuBootHookStore.State.OCCUPIED_UNKNOWN) {
                return "ok=1;early_hook=unknown_preserved;gate=" + (gate ? "1" : "0")
                        + ";optin=" + (optIn ? "1" : "0");
            }

            return "ok=1;early_hook=absent;gate=" + (gate ? "1" : "0")
                    + ";optin=" + (optIn ? "1" : "0");
        } catch (Throwable error) {
            android.util.Log.e("ThorDisplayDaemon", "early CPU hook reconcile failed", error);
            return "ok=0;error=EARLY_HOOK_RECONCILE_FAILED";
        }
    }

    static boolean prototypeArmed() {
        FileDescriptor fd = null;
        try {
            fd = Os.open(EarlyCpuBootHookScript.PROTOTYPE_GATE,
                    OsConstants.O_RDONLY | OsConstants.O_CLOEXEC | OsConstants.O_NOFOLLOW, 0);
            StructStat stat = Os.fstat(fd);
            int mode = stat.st_mode & 0777;
            if (!OsConstants.S_ISREG(stat.st_mode)
                    || stat.st_nlink != 1
                    || (stat.st_uid != 0 && stat.st_uid != 2000)
                    || mode != 0644
                    || stat.st_size <= 0L
                    || stat.st_size > 2L) {
                return false;
            }
            byte[] data = new byte[(int) stat.st_size];
            int offset = 0;
            while (offset < data.length) {
                int read = Os.read(fd, data, offset, data.length - offset);
                if (read <= 0) return false;
                offset += read;
            }
            String value = new String(data, StandardCharsets.US_ASCII);
            return "1".equals(value) || "1\n".equals(value);
        } catch (ErrnoException error) {
            return false;
        } catch (Throwable ignored) {
            return false;
        } finally {
            if (fd != null) try { Os.close(fd); } catch (Throwable ignored) {}
        }
    }
}
