import com.thor.displaypowertest.DaemonArgs;

/** The daemon command line is written by PServer and the compositor helper. */
public final class DaemonArgsTest {
    public static void main(String[] args) {
        DaemonArgs empty = DaemonArgs.parse(new String[0]);
        check(empty.displayFixEnabled, "a bare launch keeps the display fix enabled");
        check(!empty.holdRequested && !empty.cpuFixDesired && !empty.lidGuardDesired,
                "a bare launch requests nothing else");
        check(empty.phaseStartedAt == -1L, "no phase start means the daemon uses its own clock");
        check(!empty.afterComposerRestart && "run".equals(empty.launchKind()), "plain run");
        check(DaemonArgs.parse(null).displayFixEnabled, "null is treated as no arguments");

        DaemonArgs boot = DaemonArgs.parse(new String[] {"1", "hold", "1", "0"});
        check(boot.displayFixEnabled && boot.holdRequested && boot.cpuFixDesired,
                "PServer boot launch");
        check(!boot.lidGuardDesired && "hold".equals(boot.launchKind()), "hold launch");

        DaemonArgs post = DaemonArgs.parse(new String[] {"0", "hold", "0", "1", "64528", "post"});
        check(!post.displayFixEnabled && post.lidGuardDesired, "helper relaunch flags");
        check(post.phaseStartedAt == 64528L, "helper passes the phase start");
        check(post.afterComposerRestart && "post".equals(post.launchKind()),
                "post wins over hold in the trace");

        check(DaemonArgs.parse(new String[] {"1", "run"}).holdRequested == false,
                "run is not a hold");
        check(DaemonArgs.parse(new String[] {"x"}).displayFixEnabled,
                "only an explicit 0 disables the display fix");
        System.out.println("DaemonArgsTest passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
