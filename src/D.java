import com.thor.displaypowertest.DaemonArgs;
import com.thor.displaypowertest.DaemonRuntime;

/**
 * Entry point of {@code app_process / D}. The class name and its default
 * package are part of the launch and identity checks in PServer and in the
 * compositor helper; all behavior lives in DaemonRuntime.
 */
public final class D {
    private D() {}

    public static void main(String[] args) throws Exception {
        new DaemonRuntime(DaemonArgs.parse(args)).run();
    }
}
