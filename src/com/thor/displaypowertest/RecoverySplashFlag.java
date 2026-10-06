package com.thor.displaypowertest;

import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;

/**
 * Physical-test-only opt-in. The daemon never creates this marker; an ADB shell
 * user must create an empty regular file for each test session.
 */
public final class RecoverySplashFlag {
    public static final String FLAG_PATH =
            "/data/local/tmp/jesty-thor-recovery-splash.flag";
    private static final int SHELL_UID = 2000;

    private RecoverySplashFlag() {}

    public static boolean enabled() {
        try {
            StructStat stat = Os.lstat(FLAG_PATH);
            return OsConstants.S_ISREG(stat.st_mode)
                    && stat.st_uid == SHELL_UID
                    && stat.st_nlink == 1
                    && stat.st_size == 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * The helper embeds this value in a single-quoted shell assignment. Accept
     * only the normal Android /data/app base-APK shape and a narrow character set.
     */
    public static boolean classPathUsable(String path) {
        if (path == null
                || !path.matches("/data/app/[A-Za-z0-9_./=~+@-]+\\.apk")) {
            return false;
        }
        try {
            StructStat stat = Os.lstat(path);
            return OsConstants.S_ISREG(stat.st_mode) && stat.st_nlink == 1;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
