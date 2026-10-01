import com.thor.displaypowertest.DaemonLaunchModel;
import com.thor.displaypowertest.DaemonLaunchModel.SocketState;

public final class DaemonLaunchModelTest {
    private static final String BOOT_A = "85266414-667f-4691-bce0-e3ecaca53068";
    private static final String BOOT_B = "f7062f15-b3fb-44ac-87a6-6dfad529035d";
    private static final String STARTING = "ok=1;protocol=2;version=1.5.16;pid=8123;"
            + "boot_phase=WAITING_FOR_ANDROID;fix=1;watcher=STARTING";

    public static void main(String[] args) {
        check(DaemonLaunchModel.classify(true, true, null, null) == SocketState.ALIVE,
                "a reachable authenticated socket is alive regardless of stamps");
        check(DaemonLaunchModel.classify(false, false, null, BOOT_A) == SocketState.ABSENT,
                "missing socket path");
        check(DaemonLaunchModel.classify(true, false, BOOT_B, BOOT_A)
                == SocketState.STALE_PREVIOUS_BOOT, "inode stamped by an earlier kernel boot");
        check(DaemonLaunchModel.classify(true, false, BOOT_A, BOOT_A)
                == SocketState.STALE_UNKNOWN, "same boot may be a relaunch in flight");
        check(DaemonLaunchModel.classify(true, false, "", BOOT_A)
                == SocketState.STALE_UNKNOWN, "an older daemon without a stamp is conservative");
        check(DaemonLaunchModel.classify(true, false, BOOT_B, null)
                == SocketState.STALE_UNKNOWN, "unreadable current boot ID is conservative");
        check(DaemonLaunchModel.classify(true, false, "../../x", BOOT_A)
                == SocketState.STALE_UNKNOWN, "malformed stamp is conservative");

        check(DaemonLaunchModel.unreachableGracePolls(SocketState.ABSENT) == 0, "no wait when absent");
        check(DaemonLaunchModel.unreachableGracePolls(SocketState.STALE_PREVIOUS_BOOT) == 0,
                "no 30-second wait for an inode from a previous boot");
        check(DaemonLaunchModel.unreachableGracePolls(SocketState.STALE_UNKNOWN)
                == DaemonLaunchModel.SAME_BOOT_GRACE_POLLS, "bounded same-boot grace");
        check(DaemonLaunchModel.SAME_BOOT_GRACE_POLLS < 150,
                "same-boot grace must stay shorter than the former 30-second wait");

        check(DaemonLaunchModel.starting(STARTING, "1.5.16", true), "boot phase is starting");
        for (String phase : new String[]{"BOOT_HOLD", "APPLYING_CPU_FIX",
                "WAITING_AFTER_COMPOSER", "RECONCILING_DISPLAY"}) {
            check(DaemonLaunchModel.starting(STARTING.replace("WAITING_FOR_ANDROID", phase),
                    "1.5.16", true), phase + " is starting");
        }
        check(!DaemonLaunchModel.starting(STARTING.replace("WAITING_FOR_ANDROID", "READY"),
                "1.5.16", true), "READY is judged by the full health check");
        check(!DaemonLaunchModel.starting(STARTING.replace("WAITING_FOR_ANDROID",
                "BOOT_SAFETY_TIMEOUT"), "1.5.16", true), "timeout is not a starting phase");
        check(!DaemonLaunchModel.starting(STARTING, "1.5.17", true),
                "another version is not accepted");
        check(!DaemonLaunchModel.starting(STARTING, "1.5.16", false),
                "a different display intent is not accepted");
        check(!DaemonLaunchModel.starting(STARTING.replace("protocol=2", "protocol=1"),
                "1.5.16", true), "another protocol is not accepted");
        check(!DaemonLaunchModel.starting(STARTING.replace("pid=8123", "pid=0"),
                "1.5.16", true), "invalid pid is not accepted");
        check(!DaemonLaunchModel.starting("ok=0;error=BOOT_HOLD", "1.5.16", true),
                "rejections are not starting");
        check(!DaemonLaunchModel.starting(null, "1.5.16", true), "null response");
        System.out.println("DaemonLaunchModelTest passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
