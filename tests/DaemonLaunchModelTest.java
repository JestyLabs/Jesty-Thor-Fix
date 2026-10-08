import com.thor.displaypowertest.DaemonLaunchModel;
import com.thor.displaypowertest.DaemonLaunchModel.SocketState;

public final class DaemonLaunchModelTest {
    private static final String BOOT_A = "00000000-0000-4000-8000-000000000011";
    private static final String BOOT_B = "00000000-0000-4000-8000-000000000019";
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

        String stalled = STARTING.replace("WAITING_FOR_ANDROID", "APPLYING_CPU_FIX")
                .replace(";fix=", ";phase_ms=90000;fix=");
        check(DaemonLaunchModel.starting(stalled, "1.5.16", true),
                "phase_ms does not change the starting classification");
        check(DaemonLaunchModel.replaceablePid(stalled, "1.5.16", true) == 8123,
                "a starting phase unchanged for 90 s is stalled");
        check(DaemonLaunchModel.replaceablePid(stalled.replace("phase_ms=90000", "phase_ms=89999"),
                "1.5.16", true) == -1, "a phase younger than 90 s is not stalled");
        check(DaemonLaunchModel.replaceablePid(STARTING, "1.5.16", true) == -1,
                "a daemon without phase_ms is never stalled");
        check(DaemonLaunchModel.replaceablePid(stalled.replace("phase_ms=90000", "phase_ms=-1"),
                "1.5.16", true) == -1, "a negative age is malformed");
        check(DaemonLaunchModel.replaceablePid(stalled.replace("phase_ms=90000", "phase_ms=9x"),
                "1.5.16", true) == -1, "a malformed age is not stalled");
        check(DaemonLaunchModel.replaceablePid(stalled.replace("APPLYING_CPU_FIX", "READY"),
                "1.5.16", true) == -1, "READY is never stalled");
        check(DaemonLaunchModel.replaceablePid(stalled.replace("APPLYING_CPU_FIX",
                "BOOT_SAFETY_TIMEOUT"), "1.5.16", true) == -1,
                "the safety timeout is a final state, not a stalled phase");
        check(DaemonLaunchModel.replaceablePid(stalled, "1.5.17", true) == -1,
                "another version is never replaced through this path");
        check(DaemonLaunchModel.replaceablePid(stalled, "1.5.16", false) == -1,
                "a different display intent is not replaced through this path");
        check(DaemonLaunchModel.replaceablePid(stalled.replace("pid=8123", "pid=99"),
                "1.5.16", true) == -1, "a low PID is never signalled");
        check(DaemonLaunchModel.replaceablePid(null, "1.5.16", true) == -1, "null response");

        String failed = STARTING.replace("WAITING_FOR_ANDROID", "HANDOFF_FAILED")
                .replace(";fix=", ";phase_ms=0;fix=");
        check(!DaemonLaunchModel.starting(failed, "1.5.16", true),
                "a failed handover is not a starting coordinator");
        check(DaemonLaunchModel.replaceablePid(failed, "1.5.16", true) == 8123,
                "a failed handover is replaceable at once");
        check(DaemonLaunchModel.replaceablePid(failed.replace(";phase_ms=0", ""),
                "1.5.16", true) == 8123, "a failed handover needs no phase age");
        check(DaemonLaunchModel.replaceablePid(failed, "1.5.17", true) == -1,
                "a failed handover of another version is not replaced here");
        check(DaemonLaunchModel.replaceablePid(failed, "1.5.16", false) == -1,
                "a failed handover with another intent is not replaced here");
        check(DaemonLaunchModel.replaceablePid(failed.replace("protocol=2", "protocol=1"),
                "1.5.16", true) == -1, "another protocol is not replaced here");
        check(DaemonLaunchModel.replaceablePid(failed.replace("ok=1;", "ok=0;"),
                "1.5.16", true) == -1, "a rejection is not replaceable");
        System.out.println("DaemonLaunchModelTest passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
