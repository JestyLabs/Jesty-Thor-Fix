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
        long second = p.beginOff(606001);
        check(p.completeOff(second, true, 606001), "confirmed auto-off");
        check(p.isOff() && p.suppressNativeRestore(), "preserve intentional off");
        check(p.touch(606002), "touch requests restoration");
        check(!p.isOff() && p.delayMs(606002) == 300000, "rearm after touch");
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
        System.out.println("BottomIdlePolicyTest passed");
    }
}
