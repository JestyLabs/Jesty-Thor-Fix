import com.thor.displaypowertest.DaemonLaunchScript;

public final class DaemonLaunchScriptTest {
    private static void check(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
    }

    public static void main(String[] args) {
        String log = "/data/local/tmp/td032.log";
        String command = DaemonLaunchScript.command(log, true, true, true, true,
                Long.MAX_VALUE, Long.MAX_VALUE, "STALE_PREVIOUS_BOOT", Long.MAX_VALUE);
        check(command.length() <= DaemonLaunchScript.MAX_COMMAND_CHARS,
                "worst-case command length=" + command.length());
        check(command.contains("[ ! -L \"$L\" ]")
                && command.contains("[ -f \"$L\" ]")
                && command.contains("[ -O \"$L\" ]")
                && command.contains("||L=/dev/null"), "root log guard");
        check(command.contains("app_process / D 1 hold 1 1"), "daemon identity");
        check(command.endsWith(">>\"$L\" 2>&1 &"), "background redirection");
        String[] decoded = DaemonLaunchScript.traceFields(DaemonLaunchScript.metadata(
                123456789L, 123456800L, "STALE_PREVIOUS_BOOT", 29L));
        check("123456789".equals(decoded[0]) && "123456800".equals(decoded[1])
                && "STALE_PREVIOUS_BOOT".equals(decoded[2])
                && "29".equals(decoded[3]), "metadata round trip");
        String[] unknown = DaemonLaunchScript.traceFields("1,2,Z,4");
        check("?".equals(unknown[0]) && "?".equals(unknown[2]),
                "invalid metadata fails closed");
        try {
            DaemonLaunchScript.command("/data/local/tmp/x;id", true, false, false,
                    false, -1, -1, "ABSENT", -1);
            throw new AssertionError("shell path injection accepted");
        } catch (IllegalArgumentException expected) {}
        System.out.println("DaemonLaunchScriptTest passed; max command=" + command.length());
    }
}
