package com.thor.displaypowertest;

/**
 * Root-daemon reconciler for the stock pservice early CPU-restart hook.
 *
 * Production eligibility is intentionally narrow: the saved CPU Fix intent
 * must be ON and the per-install Direct-Boot opt-in must authenticate exactly.
 */
public final class EarlyCpuBootHookManager {
    private EarlyCpuBootHookManager() {}

    public static String reconcile(boolean desiredCpuFix) {
        EarlyCpuOptIn.Identity identity = EarlyCpuOptIn.readIdentityForRoot();
        boolean optIn = EarlyCpuOptIn.rootOptInMatches(identity);

        String expected = identity == null ? null
                : EarlyCpuBootHookScript.build(identity.uid, identity.token);
        EarlyCpuBootHookStore.State state = EarlyCpuBootHookStore.inspect(expected);
        boolean want = desiredCpuFix && optIn && identity != null;

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
                        + ";enabled=1;optin=1";
            }

            if (state == EarlyCpuBootHookStore.State.OWNED_EXACT
                    || state == EarlyCpuBootHookStore.State.OWNED_STALE
                    || state == EarlyCpuBootHookStore.State.OWNED_OWNER_ONLY) {
                EarlyCpuBootHookStore.removeManaged();
                return "ok=1;early_hook=removed;enabled="
                        + (desiredCpuFix ? "1" : "0")
                        + ";optin=" + (optIn ? "1" : "0");
            }

            if (state == EarlyCpuBootHookStore.State.OCCUPIED_UNKNOWN) {
                return "ok=1;early_hook=unknown_preserved;enabled="
                        + (desiredCpuFix ? "1" : "0")
                        + ";optin=" + (optIn ? "1" : "0");
            }

            return "ok=1;early_hook=absent;enabled="
                    + (desiredCpuFix ? "1" : "0")
                    + ";optin=" + (optIn ? "1" : "0");
        } catch (Throwable error) {
            android.util.Log.e("ThorDisplayDaemon", "early CPU hook reconcile failed", error);
            return "ok=0;error=EARLY_HOOK_RECONCILE_FAILED";
        }
    }

}
