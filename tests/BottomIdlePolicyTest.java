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
        check(restore.touch(60101), "OFF touch requests restoration");
        // Mirror the real callback: touch() followed by the pointer DOWN transition.
        long prematureLease = restore.generation();
        restore.pointerDown(true);
        long oldLease = restore.generation();
        check(!restore.canRestore(prematureLease),
                "TOCTOU: a lease captured before pointer DOWN is already invalid");
        check(restore.canRestore(oldLease), "current restore lease valid");
        check(restore.touch(60102), "ACTION_UP must retain restore pending");
        long staleBeforeUp = restore.generation();
        restore.pointerDown(false);
        check(!restore.canRestore(staleBeforeUp),
                "TOCTOU: ACTION_UP pointer transition also advances the lease");
        check(!restore.canRestore(oldLease), "ACTION_UP invalidates only the old callback");
        long freshLease = restore.generation();
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
        System.out.println("BottomIdlePolicyTest passed");
    }
}
