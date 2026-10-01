import com.thor.displaypowertest.BootGateModel;
import com.thor.displaypowertest.LidGuardModel;
import com.thor.displaypowertest.ExternalDisplayModel;

public final class BootAndLidModelTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void cadence() {
        BootGateModel gate = new BootGateModel(0L, false);
        check(gate.nextSampleDelayMs(1000L, 1040L) == 460L,
                "fixed-rate cadence subtracts the sample's own cost");
        check(gate.nextSampleDelayMs(1000L, 1700L) == 1L,
                "a slow sample is followed promptly, never with a negative sleep");
        gate.observe(1000L, true, true, "0", "1", "1");
        gate.observe(1500L, true, true, "0", "1", "1");
        check(gate.nextSampleDelayMs(1500L, 1520L) == 480L,
                "no grace clamp before the third stable sample");
        gate.observe(2000L, true, true, "0", "1", "1");
        check(gate.nextSampleDelayMs(11800L, 11850L) == 150L,
                "the sample after the grace is taken when the grace completes");
        check(gate.observe(11999L, true, true, "0", "1", "1") == BootGateModel.Result.WAIT,
                "one millisecond early is still inside the grace");
        check(gate.observe(12000L, true, true, "0", "1", "1") == BootGateModel.Result.READY,
                "READY needs a fresh valid sample taken at or after the grace end");

        // Simulate the daemon loop with 30 ms of reads per sample and one slow
        // 650 ms sample that shifts the phase: READY is never earlier than the
        // full grace and no longer overshoots it.
        for (int post = 0; post < 2; post++) {
            BootGateModel loop = new BootGateModel(0L, post == 1);
            long now = 37L;
            long readyAt = -1L;
            for (int i = 0; i < 200 && readyAt < 0L; i++) {
                long sampleStart = now;
                if (loop.observe(now, true, true, "0", "1", "1")
                        == BootGateModel.Result.READY) {
                    readyAt = now;
                } else {
                    now += i == 4 ? 650L : 30L;
                    now += loop.nextSampleDelayMs(sampleStart, now);
                }
            }
            check(readyAt >= 0L && readyAt - loop.stableSinceMs() == loop.graceMs(),
                    "simulated cadence reaches READY exactly at the full grace");
            check(loop.stableSinceMs() == 37L + 2L * BootGateModel.SAMPLE_PERIOD_MS,
                    "the third stable sample is two fixed periods after the first");
        }
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
        BootGateModel latePost = new BootGateModel(59000L, true);
        latePost.observe(60000L, true, true, "0", "1", "1");
        latePost.observe(60500L, true, true, "0", "1", "1");
        latePost.observe(61000L, true, true, "0", "1", "1");
        check(latePost.observe(66000L, true, true, "0", "1", "1")
                == BootGateModel.Result.READY,
                "post-composer grace retains a fresh deadline after a late initial boot");
        BootGateModel timeout = new BootGateModel(0L, false);
        check(timeout.observe(60000L, true, true, "1", "1", "0")
                == BootGateModel.Result.TIMEOUT, "safety timeout");

        BootGateModel observed = new BootGateModel(0L, false);
        check(observed.graceMs() == 10000L && post.graceMs() == 5000L,
                "trace exposes the configured grace without changing it");
        observed.observe(500L, true, true, "0", "1", "1");
        observed.observe(1000L, true, true, "0", "1", "1");
        check(observed.stableSamples() == 2 && observed.stableSinceMs() < 0L,
                "grace has not begun before the third sample");
        observed.observe(1500L, true, true, "0", "1", "1");
        check(observed.stableSamples() == 3 && observed.stableSinceMs() == 1500L
                && "0:1:1".equals(observed.candidate()), "grace start is observable");
        observed.observe(2000L, true, true, "0", "1", "0");
        check(observed.stableSamples() == 1 && observed.stableSinceMs() < 0L,
                "a CRTC change is observable as a candidate reset");
        cadence();
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
        check(BootGateModel.cpuAction(true, "UNSET", false)
                == BootGateModel.CpuAction.RESTART_ONCE,
                "confirmed unconfigured property can be applied once");
        check(BootGateModel.cpuAction(true, "UNSET", true)
                == BootGateModel.CpuAction.FAIL_SAFE,
                "unconfigured property after composer restart must not loop");
        check(BootGateModel.cpuAction(false, "UNSET", false)
                == BootGateModel.CpuAction.FAIL_SAFE,
                "unconfigured property is not confirmed OFF");
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
