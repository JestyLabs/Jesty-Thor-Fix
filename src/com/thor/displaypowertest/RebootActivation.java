package com.thor.displaypowertest;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Build;

/** A boot broadcast is a startup trigger, never authority to bypass the baseline. */
final class RebootActivation {
    private static final String BASELINE = "activation_v3_baseline";
    private static final String LIVE_PROOF = "activation_v3_live_handover";
    private static final Object LOCK = new Object();
    // commit(false) may still change the process-local preferences cache.
    private static String unconfirmedRecord;
    private static String unconfirmedLiveRecord;

    enum Evidence { RESTART_REQUIRED, BOOT_VERIFIED, LIVE_VERIFIED, STORAGE_UNAVAILABLE, IDENTITY_UNAVAILABLE }

    static final class Ticket {
        private final SharedPreferences preferences;
        private final String stamp;
        private final String boot;
        private Evidence failure;

        Ticket(SharedPreferences preferences, String stamp, String boot) {
            this.preferences = preferences;
            this.stamp = stamp;
            this.boot = boot;
        }

        private void prepare() {
            synchronized (LOCK) {
                if (stamp.isEmpty() || !RebootActivationPolicy.validBoot(boot)) {
                    failure = Evidence.IDENTITY_UNAVAILABLE;
                    return;
                }
                try {
                    String record = preferences.getString(BASELINE, "");
                    // Preserve the first observation across subsequent opens and boots.
                    boolean existing = RebootActivationPolicy.baselineBoot(record, stamp) != null;
                    if (existing && !record.equals(unconfirmedRecord)) return;
                    String baseline = existing ? record : RebootActivationPolicy.baselineRecord(stamp, boot);
                    if (baseline.isEmpty()) {
                        failure = Evidence.STORAGE_UNAVAILABLE;
                        return;
                    }
                    unconfirmedRecord = baseline;
                    if (!preferences.edit().putString(BASELINE, baseline).commit()) failure = Evidence.STORAGE_UNAVAILABLE;
                    else unconfirmedRecord = null;
                } catch (RuntimeException unavailable) { failure = Evidence.STORAGE_UNAVAILABLE; }
            }
        }

        Evidence evidence() {
            synchronized (LOCK) {
                if (failure != null) return failure;
                try {
                    String record = preferences.getString(BASELINE, "");
                    if (record.equals(unconfirmedRecord)) return Evidence.STORAGE_UNAVAILABLE;
                    String baseline = RebootActivationPolicy.baselineBoot(record, stamp);
                    if (baseline == null) return Evidence.STORAGE_UNAVAILABLE;
                    if (RebootActivationPolicy.authorized(stamp, stamp, boot, baseline)) {
                        return Evidence.BOOT_VERIFIED;
                    }
                    String live = preferences.getString(LIVE_PROOF, "");
                    if (live.equals(unconfirmedLiveRecord)) return Evidence.STORAGE_UNAVAILABLE;
                    String liveBoot = RebootActivationPolicy.baselineBoot(live, stamp);
                    return liveBoot != null && boot.equalsIgnoreCase(liveBoot)
                            ? Evidence.LIVE_VERIFIED : Evidence.RESTART_REQUIRED;
                } catch (RuntimeException unavailable) { return Evidence.STORAGE_UNAVAILABLE; }
            }
        }

        boolean isAuthorized() {
            Evidence result = evidence();
            return result == Evidence.BOOT_VERIFIED || result == Evidence.LIVE_VERIFIED;
        }

        /** Called only after a safe predecessor stop and authenticated new runtime STARTING/READY. */
        boolean confirmLiveHandover() {
            synchronized (LOCK) {
                if (evidence() != Evidence.RESTART_REQUIRED) return false;
                try {
                    String proof = RebootActivationPolicy.baselineRecord(stamp, boot);
                    if (proof.isEmpty()) return false;
                    unconfirmedLiveRecord = proof;
                    if (!preferences.edit().putString(LIVE_PROOF, proof).commit()) return false;
                    unconfirmedLiveRecord = null;
                    return evidence() == Evidence.LIVE_VERIFIED;
                } catch (RuntimeException unavailable) { return false; }
            }
        }

        String pendingMessage() {
            Evidence state = evidence();
            if (state == Evidence.STORAGE_UNAVAILABLE) {
                return "Activation evidence could not be saved or read. Do not keep restarting. "
                        + "Reopen the app after storage is available. If this persists, use owner-approved "
                        + "external recovery; do not uninstall or clear app data.";
            }
            if (state == Evidence.IDENTITY_UNAVAILABLE) {
                return "The kernel boot or installed package could not be verified. Do not keep restarting. "
                        + "Use owner-approved external recovery; do not uninstall or clear app data.";
            }
            if (state == Evidence.RESTART_REQUIRED) {
                return "The app is checking whether it can safely switch the background service without a reboot. "
                        + "If the device is in TOP mode, switch to BOTH and reopen the app. "
                        + "If automatic activation remains unavailable, restart normally. "
                        + "If activation remains blocked afterward, 
                        + "use owner-approved external recovery; do not uninstall or clear app data.";
            }
            return RebootActivationPolicy.restartMessage();
        }
    }

    static Ticket ticket(Context context, SharedPreferences preferences) {
        String stamp = "";
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            long code = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
            if (DaemonIdentity.RUNTIME_ID.equals(info.versionName) && code > 0L && info.lastUpdateTime > 0L) {
                stamp = info.versionName + ":" + code + ":" + info.lastUpdateTime;
            }
        } catch (Exception ignored) {
            // No timestamp arithmetic or inferred boot evidence.
        }
        String boot = "";
        try { boot = SecureChannel.currentBootId(); }
        catch (RuntimeException unavailable) { /* Missing kernel evidence holds activation. */ }
        Ticket ticket = new Ticket(preferences, stamp, boot);
        ticket.prepare();
        return ticket;
    }

    static boolean allowStart(Context context, SharedPreferences preferences) {
        return ticket(context, preferences).isAuthorized();
    }
}
