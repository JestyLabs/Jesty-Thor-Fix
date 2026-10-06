import com.thor.displaypowertest.EarlyCpuBootHookScript;

public final class EarlyCpuBootHookScriptTest {
    private static final String TOKEN =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static int count(String text, String needle) {
        int total = 0;
        int at = 0;
        while ((at = text.indexOf(needle, at)) >= 0) {
            total++;
            at += needle.length();
        }
        return total;
    }

    public static void main(String[] args) {
        String script = EarlyCpuBootHookScript.build(10163, TOKEN);
        require(script.equals(EarlyCpuBootHookScript.build(10163, TOKEN)),
                "script must be deterministic");
        require(script.startsWith("#!/system/bin/sh\n# "
                        + EarlyCpuBootHookScript.MAGIC + "\n"),
                "script must have exact ownership magic");

        require(script.contains("GATE='" + EarlyCpuBootHookScript.PROTOTYPE_GATE + "'"),
                "one-shot prototype gate path");
        require(script.contains("OPT='" + EarlyCpuBootHookScript.OPT_IN_PATH + "'"),
                "device-protected opt-in path");
        require(script.contains("ATT='" + EarlyCpuBootHookScript.ATTEMPT_PATH + "'"),
                "boot-scoped attempt path");
        require(script.contains("APP_UID='10163'"), "app UID must be pinned");
        require(script.contains("TOKEN='" + TOKEN + "'"), "install token must be pinned");

        require(script.contains("[ -f \"$OPT\" ] && [ ! -L \"$OPT\" ]"),
                "opt-in must reject symlinks/non-regular files");
        require(script.contains("stat -c %u"), "opt-in owner must be verified");
        require(script.contains("stat -c %a"), "opt-in mode must be verified");
        require(script.contains("[ ! -e \"$ATT\" ] || exit 0"),
                "same-boot attempt must be one-shot");

        int consume = script.indexOf("rm -f \"$GATE\"");
        int prepared = script.indexOf("write_attempt PREPARED");
        int setprop = script.indexOf("setprop \"$PROP\" 1");
        int verified = script.indexOf("write_attempt PROPERTY_VERIFIED");
        int requested = script.indexOf("write_attempt RESTART_REQUESTED");
        int restart = script.indexOf("setprop ctl.restart \"$COMP\"");
        require(consume >= 0 && prepared > consume && setprop > prepared
                        && verified > setprop && requested > verified && restart > requested,
                "gate/provenance/restart ordering must be strict");

        require(count(script, "setprop ctl.restart \"$COMP\"") == 1,
                "exactly one composer restart command may exist");
        require(!script.contains("ctl.restart surfaceflinger"),
                "hook must never restart SurfaceFlinger directly");
        require(!script.contains("ctl.restart zygote"),
                "hook must never restart zygote directly");
        require(!script.contains(" reboot") && !script.contains("\nreboot"),
                "hook must never reboot the device");
        require(!script.contains("display.power.state")
                        && !script.contains("SurfaceControl")
                        && !script.contains("bootanimation"),
                "hook must not mutate display routing/power or bootanimation");

        boolean uidRejected = false;
        try {
            EarlyCpuBootHookScript.build(9999, TOKEN);
        } catch (IllegalArgumentException expected) {
            uidRejected = true;
        }
        require(uidRejected, "non-app UID must be rejected");

        boolean tokenRejected = false;
        try {
            EarlyCpuBootHookScript.build(10163, "not-a-token");
        } catch (IllegalArgumentException expected) {
            tokenRejected = true;
        }
        require(tokenRejected, "invalid install token must be rejected");

        System.out.println("Early CPU boot-hook script tests passed");
    }
}
