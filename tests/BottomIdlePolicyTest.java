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
        BottomIdlePolicy p = new BottomIdlePolicy();
        p.configure(5);
        p.mode("0");
        check(p.delayMs(300001) == -1, "silence is not verified inactivity");
        check(p.beginOff(900000) == -1, "never turn off without bottom touch");
        p.touch(1000);
        check(p.delayMs(1000) == 300000, "five minutes");
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
        try { p.configure(7); throw new AssertionError("unlisted timeout"); }
        catch (IllegalArgumentException expected) {}
        System.out.println("BottomIdlePolicyTest passed");
    }
}
