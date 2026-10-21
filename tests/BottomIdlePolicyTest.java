import com.thor.displaypowertest.BottomIdlePolicy;
import com.thor.displaypowertest.BottomIdleSettings;

public final class BottomIdlePolicyTest {
    static void check(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
    public static void main(String[] args) {
        for (int i = 0; i < BottomIdleSettings.count(); i++) {
            int minutes = BottomIdleSettings.minutesAt(i);
            check(BottomIdleSettings.minutesFor(BottomIdleSettings.commandFor(minutes)) == minutes,
                    "closed IPC encoding " + minutes);
        }
        check(BottomIdleSettings.minutesFor('z') == 0, "off command");
        check(BottomIdleSettings.minutesFor('E') == -1, "existing IPC must not alias");
        for (String mode : new String[]{"0", "1", "2", "?", ""}) {
            check(BottomIdlePolicy.shouldObserve(mode, true) == "0".equals(mode),
                    "monitor present only in BOTH: " + mode);
            check(!BottomIdlePolicy.shouldObserve(mode, false),
                    "stock mode must have no idle input watcher: " + mode);
        }
        BottomIdlePolicy p = new BottomIdlePolicy();
        p.configure(5);
        p.mode("0");
        check(p.delayMs(300001) == -1, "silence is not verified inactivity");
        check(p.beginOff(900000) == -1, "never turn off without bottom touch");
        p.touch(1000);
        check(p.delayMs(1000) == 300000, "five minutes");
        p.pointerDown(true);
        check(p.delayMs(900000) == -1, "held finger is active usage");
        check(p.beginOff(900000) == -1, "held touch must never darken bottom");
        p.pointerDown(false);
        p.touch(1000);
        check(p.delayMs(1000) == 300000, "released touch rearms");

        p.touch(6000);
        check(p.delayMs(6000) == 300000, "fresh touch resets deadline");
        check(p.beginOff(305999) == -1, "not early");
        long token = p.beginOff(306000);
        check(token > 0 && p.isPending(token), "exact deadline");
        p.touch(306001);
        check(!p.isPending(token), "touch cancels stale timeout");
        check(!p.completeOff(token, true, 306002), "stale completion cannot claim off");
        long cancelledRestore = p.generation();
        check(p.completeRestore(cancelledRestore, true),
                "cancelled pending OFF requires confirmed restore before rearm");
        long second = p.beginOff(606001);
        check(p.completeOff(second, true, 606001), "confirmed auto-off");
        check(p.isOff() && p.suppressNativeRestore(), "preserve intentional off");
        check(p.touch(606002), "touch requests restoration");
        check(!p.isOff() && p.delayMs(606002) == -1,
                "a pending restore must block a new timeout");
        check(p.completeRestore(p.generation(), true), "confirmed restore clears guard");
        check(p.delayMs(606002) == 300000, "rearm after confirmed restore");
        p.mode("1");
        check(p.delayMs(999999) == -1, "TOP never arms");
        p.mode("0");
        check(p.delayMs(999999) == -1, "fresh BOTH needs new touch");
        p.touch(1000100);
        p.wake();
        check(p.delayMs(1000200) == -1, "wake fails closed");
        p.touch(1000500);
        p.configure(0);
        check(p.delayMs(999999) == -1 && !p.enabled(), "disabled has no timer");
        // Negative controls: disabled or unknown mode can never mint an OFF lease.
        for (int minute : new int[]{1, 2, 3, 5, 10, 15, 20, 30, 45, 60}) {
            p.configure(minute);
            p.mode("?");
            p.touch(100L);
            check(p.beginOff(100L + minute * 60000L + 1L) == -1L,
                    "unknown mode must disarm, timeout " + minute);
            p.mode("0");
            check(p.beginOff(100L + minute * 60000L + 1L) == -1L,
                    "new mode requires verified touch, timeout " + minute);
            p.touch(100L);
            check(p.beginOff(100L + minute * 60000L - 1L) == -1L,
                    "must not expire before deadline " + minute);
            long lease = p.beginOff(100L + minute * 60000L);
            check(lease > 0L && p.isPending(lease), "deadline arms exactly " + minute);
            p.configure(0);
            check(!p.isPending(lease), "disabled invalidates lease " + minute);
            check(!p.completeOff(lease, true, 5000000L),
                    "stale lease cannot claim ownership " + minute);
        }
        try { p.configure(7); throw new AssertionError("unlisted timeout"); }
        catch (IllegalArgumentException expected) {}
        BottomIdlePolicy restore = new BottomIdlePolicy();
        restore.configure(1); restore.mode("0");
        restore.touch(100);
        long firstOff = restore.beginOff(60100);
        check(firstOff > 0L, "first timed OFF admitted");
        check(restore.completeOff(firstOff, true, 60100), "physical confirmation owned");
        // Exercise the same single-call API as BottomIdleRuntime, not a fake
        // touch/gesture sequence that could miss the actual restore bug.
        long oldLease = restore.touchEvent(60101, 1); // ACTION_DOWN
        check(oldLease > 0L && restore.canRestore(oldLease),
                "ACTION_DOWN must return a current valid restore lease");
        long freshLease = restore.touchEvent(60102, 0); // ACTION_UP
        check(freshLease > oldLease && restore.canRestore(freshLease),
                "ACTION_UP must reissue a current restore lease");
        check(!restore.canRestore(oldLease),
                "ACTION_UP invalidates the earlier DOWN restore");
        check(restore.canRestore(freshLease), "ACTION_UP re-issues current restore");
        check(restore.delayMs(60200) == -1L, "do not rearm while restore is pending");
        check(!restore.completeRestore(oldLease, true), "stale restore ACK rejected");
        check(restore.completeRestore(freshLease, true), "fresh confirmed restore clears pending");
        check(!restore.canRestore(freshLease), "completed restore lease consumed");
        long nextOff = restore.beginOff(120102);
        check(nextOff > 0L, "new OFF allowed after restore confirmation");
        check(!restore.canRestore(freshLease), "new OFF invalidates previous restore");
        restore.touch(120103);
        long pendingRestore = restore.generation();
        restore.mode("1");
        check(!restore.canRestore(pendingRestore), "TOP mode invalidates queued restore");
        restore.configure(0);
        long disabledLease = restore.requireRestore();
        check(restore.canRestore(disabledLease), "disable can own a restore lease");
        restore.configure(1);
        check(!restore.canRestore(disabledLease), "reenable revokes queued disable restore");
        restore.mode("1");
        check(restore.touchEvent(130000, 1) == BottomIdlePolicy.REJECTED_TOUCH,
                "TOP touch cannot mint a restore lease");
        restore.configure(0);
        check(restore.touchEvent(130001, 0) == BottomIdlePolicy.REJECTED_TOUCH,
                "disabled touch never verifies display inactivity");
        // A single API call must own each previously reported CodeQL
        // check/use site; disabled branches cannot change their generation.
        BottomIdlePolicy atomic = new BottomIdlePolicy();
        long disabledGeneration = atomic.generation();
        check(atomic.sampleModeIfEnabled("0") == -1, "disabled sample rejected");
        check(!atomic.wakeIfEnabled(), "disabled wake rejected");
        check(atomic.inputUnavailableRestoreLease() == BottomIdlePolicy.REJECTED_TOUCH,
                "disabled input failure rejected");
        check(atomic.generation() == disabledGeneration,
                "disabled snapshots have no policy mutations");
        atomic.configure(1);
        check(atomic.sampleModeIfEnabled("0") == 1,
                "enabled BOTH mode edge recognized");
        check(atomic.sampleModeIfEnabled("0") == 0,
                "unchanged BOTH never invalidates proof");
        atomic.touch(50);
        check(atomic.inputUnavailableRestoreLease() == -1L,
                "monitor failure without pending/off ownership cannot restore");
        check(atomic.delayMs(65050) == -1L,
                "input failure disarms deadline before first new touch");
        atomic.touch(70);
        long aOff = atomic.beginOff(60070);
        check(aOff > 0L && atomic.completeOff(aOff, true, 60070),
                "owned lower CRTC OFF fixture");
        long retry = atomic.inputUnavailableRestoreLease();
        check(retry >= 0L && atomic.canRestore(retry),
                "input failure returns exact current restoration lease");
        check(!atomic.isOff(), "input failure drops stale OFF ownership");
        long retry2 = atomic.inputUnavailableRestoreLease();
        check(retry2 > retry && atomic.canRestore(retry2)
                        && !atomic.canRestore(retry),
                "repeated failed monitor must not lose pending restore");
        check(!atomic.completeRestore(retry, true),
                "old failure restore cannot acknowledge newer generation");
        check(atomic.completeRestore(retry2, true),
                "latest restore acknowledgment clears lease");
        atomic.touch(300);
        long old = atomic.generation();
        check(atomic.wakeIfEnabled(), "wake resets touch proof");
        check(atomic.generation() > old && atomic.delayMs(60300) == -1L,
                "wake revokes old touch deadline");
        check(atomic.sampleModeIfEnabled("1") == 1, "TOP mode edge");
        check(atomic.sampleModeIfEnabled("0") == 1, "BOTH mode edge again");
        System.out.println("BottomIdlePolicyTest passed");
    }
}
