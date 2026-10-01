import com.thor.displaypowertest.BootGateModel;
import com.thor.displaypowertest.LidGuardModel;
import com.thor.displaypowertest.ExternalDisplayModel;

public final class BootAndLidModelTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void boot() {
        BootGateModel gate = new BootGateModel(1000L, false);
        check(gate.observe(1000L, false, true, "1", "1", "1") == BootGateModel.Result.WAIT,
                "Android not complete");
        check(gate.observe(1500L, true, false, "1", "1", "1") == BootGateModel.Result.WAIT,
                "composer not ready");
        check(gate.observe(2000L, true, true, "?", "1", "1") == BootGateModel.Result.WAIT,
                "unknown mode must not pass");
        check(gate.observe(2500L, true, true, "1", "1", "1") == BootGateModel.Result.WAIT,
                "first sample");
        check(gate.observe(3000L, true, true, "1", "1", "1") == BootGateModel.Result.WAIT,
                "second sample");
        check(gate.observe(3500L, true, true, "1", "1", "0") == BootGateModel.Result.WAIT,
                "CRTC transition resets the candidate");
        check(gate.observe(4000L, true, true, "1", "1", "0") == BootGateModel.Result.WAIT,
                "second stable CRTC sample");
        check(gate.observe(4500L, true, true, "1", "1", "0") == BootGateModel.Result.WAIT,
                "three samples alone are insufficient");
        check(gate.observe(13500L, true, true, "1", "1", "0") == BootGateModel.Result.WAIT,
                "grace begins only after the third stable sample");
        check(gate.observe(14500L, true, true, "1", "1", "0") == BootGateModel.Result.READY,
                "10 second additional grace after three stable samples");

        BootGateModel post = new BootGateModel(20000L, true);
        post.observe(20000L, true, true, "0", "1", "1");
        post.observe(20500L, true, true, "0", "1", "1");
        check(post.observe(25000L, true, true, "0", "1", "1") == BootGateModel.Result.WAIT,
                "third sample starts post-composer grace");
        check(post.observe(30000L, true, true, "0", "1", "1") == BootGateModel.Result.READY,
                "five second post-composer grace");
        BootGateModel timeout = new BootGateModel(0L, false);
        check(timeout.observe(60000L, true, true, "1", "1", "0")
                == BootGateModel.Result.TIMEOUT, "safety timeout");
        check(!BootGateModel.displayActionRequired("?", true, "0"),
                "unknown mode must never power on");
        check(!BootGateModel.displayActionRequired("1", true, "0"),
                "already-off TOP is idempotent");
        check(BootGateModel.displayActionRequired("1", true, "1"),
                "TOP true-off requires an OFF command");
        check(!BootGateModel.displayActionRequired("0", true, "1"),
                "BOTH already on is idempotent");
        check(BootGateModel.displayActionRequired("0", true, "0"),
                "BOTH requires ON when lower display is off");
        check(!BootGateModel.displayActionRequired("2", true, "0"),
                "BOTTOM transition must not force ON from the mode flag alone");
        check(BootGateModel.displayActionRequired("1", false, "0"),
                "native TOP requires lower hardware on");
        // Reproduce the reported path: enable the fix in BOTH, then change to
        // TOP. The watcher must issue OFF even though the toggle stays ON.
        check(!BootGateModel.displayActionRequired("0", true, "1"),
                "enabling in BOTH leaves the lower panel on");
        check(BootGateModel.displayActionRequired("1", true, "1"),
                "BOTH to TOP with fix ON must power the lower panel off");
        check(!BootGateModel.displayActionRequired("1", true, "0"),
                "TOP true-off must remain off without repeated commands");
        check(BootGateModel.displayActionRequired("0", true, "0"),
                "TOP to BOTH must restore the lower panel");
        check(!BootGateModel.displayActionRequired("?", true, "1"),
                "unknown mode must not issue a speculative OFF");
        check(BootGateModel.shouldRepairStableTop("1", "1", "1", true, false),
                "AYN reactivation in stable TOP must be corrected");
        check(!BootGateModel.shouldRepairStableTop("1", "1", "0", true, false),
                "already-off lower hardware needs no repair");
        check(!BootGateModel.shouldRepairStableTop("1", "0", "1", true, false),
                "sleeping top must not cause a display action");
        check(!BootGateModel.shouldRepairStableTop("1", "1", "1", true, true),
                "scheduled wake repair must retain its delay");
        check(!BootGateModel.shouldRepairStableTop("0", "1", "1", true, false),
                "BOTH mode must not be forced into TOP true-off");
        check(!BootGateModel.shouldRepairStableTop("1", "1", "1", false, false),
                "disabled fix must not power off the lower panel");
        check(BootGateModel.cpuAction(true, "0", false)
                == BootGateModel.CpuAction.RESTART_ONCE, "CPU preference mismatch restarts once");
        check(BootGateModel.cpuAction(true, "0", true)
                == BootGateModel.CpuAction.FAIL_SAFE, "no second composer restart");
        check(BootGateModel.cpuAction(false, "1", false)
                == BootGateModel.CpuAction.RESTART_ONCE, "CPU fix disable needs restart");
        check(BootGateModel.cpuAction(true, "1", false)
                == BootGateModel.CpuAction.PROCEED, "matching CPU setting needs no restart");
        check(BootGateModel.cpuAction(true, "?", false)
                == BootGateModel.CpuAction.FAIL_SAFE, "unknown CPU state fails safe");
    }

    private static void lid() {
        LidGuardModel guard = new LidGuardModel();
        check(!guard.maySchedule(), "unknown lid must fail safe");
        guard.onSwitch(1, 1000L);
        check(guard.maySchedule(), "closed lid can arm guard");
        check(!guard.maySleep(1300L, true, false, true), "500ms debounce");
        check(!guard.maySleep(2500L, true, true, true), "external display exclusion");
        check(!guard.maySleep(2500L, true, false, false), "unknown environment exclusion");
        check(!guard.maySleep(2500L, false, false, true), "already asleep");
        check(guard.maySleep(2500L, true, false, true), "closed interactive sleep");
        guard.recordSleep(2500L, true);
        check(!guard.maySleep(3000L, true, false, true), "attempt spacing");
        check(guard.maySleep(4100L, true, false, true), "second attempt");
        guard.recordSleep(4100L, true);
        check(guard.maySleep(5700L, true, false, true), "third attempt");
        guard.recordSleep(5700L, true);
        check(!guard.maySleep(7300L, true, false, true) && guard.paused(),
                "fourth attempt blocked and guard paused");
        check(guard.blockedWakes() == 3, "blocked wake count");
        guard.onSwitch(0, 7400L);
        check(!guard.paused() && !guard.maySchedule(), "opening resets loop guard");
        guard.onSwitch(1, 7500L);
        check(guard.maySchedule(), "closing rearms guard");

        byte[] event = new byte[24];
        event[16] = 5;
        event[18] = 0;
        event[20] = 1;
        check(LidGuardModel.swLidValue(event, true) == 1, "64-bit SW_LID closed");
        event[18] = 1;
        check(LidGuardModel.swLidValue(event, true) == -1, "other switch ignored");
        byte[] shortEvent = new byte[16];
        shortEvent[8] = 5;
        check(LidGuardModel.swLidValue(shortEvent, false) == 0, "32-bit SW_LID open");
    }

    private static void externalDisplay() {
        String thor = "mViewports=[DisplayViewport{type=INTERNAL, valid=true, isActive=true, "
                + "displayId=0, uniqueId='local:top'}, DisplayViewport{type=EXTERNAL, "
                + "valid=true, isActive=true, displayId=4, uniqueId='local:bottom'}]";
        check(Boolean.FALSE.equals(ExternalDisplayModel.hasExtraExternalViewport(thor)),
                "integrated lower panel is not a dock");
        String docked = thor.replace("]", ", DisplayViewport{type=EXTERNAL, valid=true, "
                + "isActive=true, displayId=7, uniqueId='local:dock'}]");
        check(Boolean.TRUE.equals(ExternalDisplayModel.hasExtraExternalViewport(docked)),
                "third active external panel excludes sleep");
        check(ExternalDisplayModel.hasExtraExternalViewport("no display information") == null,
                "unknown viewport information fails safe");
    }

    public static void main(String[] args) {
        boot();
        lid();
        externalDisplay();
        System.out.println("Boot and lid model tests passed");
    }
}
