package com.thor.displaypowertest;

import android.os.SystemClock;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Panel-RAM bypass interception with the real coordinator and hardware classes and a
 * synthetic kernel boundary (FakeIo). Not a physical test.
 *
 * Hardware basis (2026-10-29, owner-supervised): the vendor framework writes the
 * attribute through the PServer root shell on every refresh change. Written while the
 * lower panel was off, it produced "DSI_PLL_0: lock failed"; the next two lower starts
 * failed and both screens went black. With a file bind-mounted over the attribute, the
 * same sequence left the kernel clean and the lower panel started normally.
 *
 * FakeIo models only what the interceptor touches: the attribute (a store with the lower
 * panel off poisons the boot), the bind and second sysfs mounts, the request file and its
 * watch. Arguments: none = all, or one case name.
 */
public final class BypassRamInterceptorTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message + "\n" + Log.LINES);
    }
    private static int count(String prefix) {
        int n = 0;
        for (String line : Log.LINES) if (line.startsWith("ThorDisplayDaemon: " + prefix)) n++;
        return n;
    }

    /** Synthetic kernel, mounts and request file. Lower power follows TestDisplayState.bottom. */
    static final class FakeIo implements BypassRamInterceptor.Io {
        String kernel = "0";
        boolean poisoned;
        final List<String> stores = new ArrayList<>();
        final Map<String, String> files = new HashMap<>();
        boolean bind, sysfs;
        boolean failSysfs, failBind, failWatch, failRealRead;
        String dpmsOverride;
        /** Synthetic: the framework writes the attribute while the bind command runs. */
        String frameworkDuringBind;
        Runnable watcher;
        int watchesOpened;

        void store(String value) {
            String lower = dpms();
            stores.add(value + "@" + lower);
            if (!"On".equals(lower)) poisoned = true;
            kernel = value;
        }
        String dpms() {
            if (dpmsOverride != null) return dpmsOverride;
            return "1".equals(TestDisplayState.bottom) ? "On" : "Off";
        }
        /** What the vendor framework does on a refresh change: "echo 0x<v> > attribute". */
        void framework(String value) {
            write(ThorHardwareProfile.BYPASS_RAM_NODE, "0x" + value);
        }

        @Override public boolean run(String... argv) {
            String line = String.join(" ", argv);
            if (line.equals(BypassRamInterceptor.MOUNT + " -t sysfs sysfs " + BypassRamInterceptor.SYSFS)) {
                if (failSysfs) return false;
                sysfs = true;
                return true;
            }
            if (line.equals(BypassRamInterceptor.MOUNT + " --bind " + BypassRamInterceptor.REQUEST + " "
                    + ThorHardwareProfile.BYPASS_RAM_NODE)) {
                if (frameworkDuringBind != null) {
                    framework(frameworkDuringBind);
                    frameworkDuringBind = null;
                }
                if (failBind) return false;
                bind = true;
                return true;
            }
            if (line.equals(BypassRamInterceptor.UMOUNT + " " + ThorHardwareProfile.BYPASS_RAM_NODE)) {
                if (!bind) return false;
                bind = false;
                return true;
            }
            if (line.equals(BypassRamInterceptor.UMOUNT + " " + BypassRamInterceptor.SYSFS)) {
                if (!sysfs) return false;
                sysfs = false;
                return true;
            }
            throw new AssertionError("Unexpected command: " + line);
        }
        @Override public String read(String path) {
            if (path.equals(ThorHardwareProfile.BYPASS_RAM_NODE)) {
                return bind ? files.get(BypassRamInterceptor.REQUEST) : kernel;
            }
            if (path.equals(BypassRamInterceptor.REAL)) return sysfs && !failRealRead ? kernel : null;
            if (path.equals(ThorHardwareProfile.BOTTOM_DPMS_PATH)) return dpms();
            String value = files.get(path);
            return value == null ? null : value.trim();
        }
        @Override public boolean write(String path, String value) {
            if (path.equals(ThorHardwareProfile.BYPASS_RAM_NODE)) {
                if (!bind) {
                    store(BypassRamPolicy.parse(value));
                    return true;
                }
                path = BypassRamInterceptor.REQUEST;
            }
            if (path.equals(BypassRamInterceptor.REAL)) {
                if (!sysfs) return false;
                store(BypassRamPolicy.parse(value));
                return true;
            }
            files.put(path, value);
            if (path.equals(BypassRamInterceptor.REQUEST) && watcher != null) watcher.run();
            return true;
        }
        @Override public boolean mkdir(String path) { return true; }
        @Override public String mountInfo() {
            StringBuilder out = new StringBuilder("20 25 0:21 / /sys rw,relatime shared:5 - sysfs sysfs rw\n");
            if (sysfs) out.append("431 82 0:21 / ").append(BypassRamInterceptor.SYSFS)
                    .append(" rw,relatime - sysfs sysfs rw\n");
            if (bind) out.append("477 20 0:19 /jesty-bypass/requested ").append(ThorHardwareProfile.BYPASS_RAM_NODE)
                    .append(" rw - tmpfs tmpfs rw\n");
            return out.toString();
        }
        @Override public String interactive() { return TestDisplayState.interactive ? "1" : "0"; }
        @Override public boolean lowerLatched() { return DisplayActionCoordinator.isLowerFailureLatched(); }
        @Override public AutoCloseable watch(String path, Runnable onWrite) {
            if (failWatch) return null;
            watchesOpened++;
            watcher = onWrite;
            return () -> watcher = null;
        }
    }

    private static FakeIo io;
    private static BypassRamInterceptor interceptor;

    /** BOTH at 120 Hz with both panels on, fix enabled, interceptor installed and armed. */
    private static void start(boolean install) {
        DisplayHardware.setLowerPanelListener(null);
        SystemClock.now += 100000L;
        TestDisplayState.baseline();
        TestDisplayState.mode = "0";
        TestDisplayState.bottom = "1";
        DisplayActionCoordinator.onWatcherSample("0");
        Log.LINES.clear();
        io = new FakeIo();
        interceptor = new BypassRamInterceptor(io, Runnable::run);
        if (install) {
            DisplayHardware.setLowerPanelListener(interceptor);
            sample();
        }
    }
    private static void sample() {
        SystemClock.now += 50L;
        DisplayActionCoordinator.onWatcherSample(TestDisplayState.mode);
    }
    /** The owner's mode switch, seen by the watcher (TOP = "1", BOTH = "0"). */
    private static void switchMode(String mode) {
        TestDisplayState.mode = mode;
        sample();
        SystemClock.now += 2000L;
        sample();
    }
    private static void finish() { DisplayHardware.setLowerPanelListener(null); }

    static void arm() {
        start(true);
        check("ARMED".equals(DisplayHardware.lowerPanelStatus()), "BR-01 armed: " + DisplayHardware.lowerPanelStatus());
        check(io.bind && io.sysfs && io.watcher != null, "BR-01b bind, sysfs and watch in place");
        check("0".equals(io.files.get(BypassRamInterceptor.REQUEST).trim()), "BR-01c request starts at the real value");
        check(count("BYPASS_INTERCEPT_READY real=0") == 1, "BR-01d ready logged once");
        finish();
    }

    static void requestLowerOn() {
        start(true);
        io.framework("1");
        check(io.stores.size() == 1 && "1@On".equals(io.stores.get(0)) && !io.poisoned,
                "BR-02 request applied at once with the lower panel on: " + io.stores);
        check(count("BYPASS_APPLIED value=1;source=REQUEST") == 1, "BR-02b applied logged");
        check("ARMED".equals(DisplayHardware.lowerPanelStatus()), "BR-02c nothing pending");
        finish();
    }

    /** The 2026-10-29 blackout sequence (M-C), now intercepted: TOP, refresh change, BOTH. */
    static void topRefreshThenBoth() {
        start(true);
        switchMode("1");
        check("0".equals(TestDisplayState.bottom), "BR-03 TOP powered the lower panel off");
        io.framework("1");
        check(io.stores.isEmpty() && !io.poisoned, "BR-03b no store with the lower panel off: " + io.stores);
        check("ARMED_PENDING".equals(DisplayHardware.lowerPanelStatus())
                && count("BYPASS_DEFERRED value=1;reason=LOWER_OFF") == 1, "BR-03c deferred while off");
        switchMode("0");
        check("1".equals(TestDisplayState.bottom), "BR-04 BOTH powered the lower panel on");
        check(io.stores.size() == 1 && "1@On".equals(io.stores.get(0)) && !io.poisoned,
                "BR-04b applied right after the lower ON: " + io.stores);
        check(count("BYPASS_APPLIED value=1;source=LOWER_ON") == 1, "BR-04c applied by the lower ON");
        finish();
    }

    /** Negative control: the same sequence without interception poisons the boot. */
    static void withoutInterception() {
        start(false);
        switchMode("1");
        io.framework("1");
        check(io.poisoned && "1@Off".equals(io.stores.get(0)),
                "BR-05 control: without interception the store happens with the lower panel off: " + io.stores);
        finish();
    }

    /** The platform powers the lower panel without an app call (e.g. a wake in BOTH). */
    static void platformOn() {
        start(true);
        TestDisplayState.bottom = "0";
        io.framework("1");
        check(io.stores.isEmpty(), "BR-06 deferred while the lower panel is off");
        TestDisplayState.bottom = "1";
        sample();
        check(io.stores.size() == 1 && "1@On".equals(io.stores.get(0)), "BR-06b applied on the watcher's next DRM read: " + io.stores);
        finish();
    }

    static void nonInteractive() {
        start(true);
        TestDisplayState.interactive = false;
        io.framework("1");
        check(io.stores.isEmpty() && count("BYPASS_DEFERRED value=1;reason=NON_INTERACTIVE") == 1,
                "BR-07 no store while non-interactive: " + io.stores);
        TestDisplayState.interactive = true;
        sample();
        check(io.stores.size() == 1 && "1@On".equals(io.stores.get(0)), "BR-07b applied once interactive again: " + io.stores);
        finish();
    }

    static void rejects() {
        start(true);
        for (String raw : new String[]{"0x2", "abc", "", "1 1", "0x10"}) {
            io.write(ThorHardwareProfile.BYPASS_RAM_NODE, raw);
            check(io.stores.isEmpty(), "BR-08 rejected value never reaches the kernel: " + raw);
        }
        check(count("BYPASS_REJECTED") >= 4 && "ARMED".equals(DisplayHardware.lowerPanelStatus()),
                "BR-08b rejections logged, nothing pending");
        finish();
    }

    static void lastRequestWins() {
        start(true);
        switchMode("1");
        io.framework("1");
        io.framework("0");
        check("ARMED".equals(DisplayHardware.lowerPanelStatus()), "BR-09 a request equal to the real value clears the wait");
        io.framework("1");
        switchMode("0");
        check(io.stores.size() == 1 && "1@On".equals(io.stores.get(0)), "BR-09b only the last request is applied: " + io.stores);
        finish();
    }

    /** The lower panel goes off between the event and the write: the fresh read wins. */
    static void staleEvent() {
        start(true);
        switchMode("1");
        io.framework("1");
        io.dpmsOverride = "Off";
        interceptor.onLowerPoweredOn("WATCH_BOTH");
        check(io.stores.isEmpty() && !io.poisoned, "BR-10 no store when the fresh read says off: " + io.stores);
        io.dpmsOverride = "?";
        interceptor.onLowerPoweredOn("WATCH_BOTH");
        check(io.stores.isEmpty() && count("BYPASS_DEFERRED value=1;reason=LOWER_UNKNOWN") == 1,
                "BR-10b unknown power state also waits");
        finish();
    }

    static void latched() {
        start(true);
        switchMode("1");
        io.framework("1");
        setLatch(true);
        TestDisplayState.bottom = "1";
        interceptor.onLowerPoweredOn("SYSTEM");
        check(io.stores.isEmpty() && count("BYPASS_DEFERRED value=1;reason=LOWER_LATCHED") == 1,
                "BR-11 latched boot: no store: " + io.stores);
        setLatch(false);
        finish();
    }

    static void fixOffIdle() {
        start(true);
        TestDisplayState.enabled = false;
        sample();
        check(!io.bind && !io.sysfs && io.watcher == null && "OFF".equals(DisplayHardware.lowerPanelStatus()),
                "BR-12 fix off with nothing waiting unmounts at once");
        io.framework("0");
        check(io.stores.size() == 1 && "0@On".equals(io.stores.get(0)), "BR-12b the framework writes directly again: " + io.stores);
        finish();
    }

    static void fixOffDrains() {
        start(true);
        switchMode("1");
        io.framework("1");
        TestDisplayState.enabled = false;
        sample();
        check(io.bind && io.sysfs && "DRAINING".equals(DisplayHardware.lowerPanelStatus()),
                "BR-13 fix off with a waiting request keeps the interception: " + DisplayHardware.lowerPanelStatus());
        io.framework("1");
        sample();
        check(io.stores.isEmpty(), "BR-13b still intercepted while draining with the lower panel off");
        TestDisplayState.bottom = "1";
        sample();
        check(io.stores.isEmpty(), "BR-13c drain checks are rate-limited");
        SystemClock.now += BypassRamInterceptor.DRAIN_CHECK_MS;
        sample();
        check(io.stores.size() == 1 && "1@On".equals(io.stores.get(0)) && !io.bind && !io.sysfs
                && "OFF".equals(DisplayHardware.lowerPanelStatus()),
                "BR-13d applied with the lower panel on, then unmounted: " + io.stores);
        check(count("BYPASS_INTERCEPT_OFF cause=DRAINED") == 1, "BR-13e drained logged");
        finish();
    }

    /** The owner's toggle: turning the fix off powers the lower panel first (USER_RECONCILE). */
    static void fixOffByUser() {
        start(true);
        switchMode("1");
        io.framework("1");
        String reply = DisplayActionCoordinator.requestFix(false);
        check(reply.startsWith("ok=1;"), "BR-21 fix off accepted: " + reply);
        check(io.stores.size() == 1 && "1@On".equals(io.stores.get(0)) && !io.poisoned,
                "BR-21b waiting request applied by the reconcile ON: " + io.stores);
        sample();
        check(!io.bind && !io.sysfs && "OFF".equals(DisplayHardware.lowerPanelStatus()),
                "BR-21c then unmounted: " + DisplayHardware.lowerPanelStatus());
        finish();
    }

    static void sysfsFails() {
        start(false);
        io.failSysfs = true;
        DisplayHardware.setLowerPanelListener(interceptor);
        sample();
        check(!io.bind && "UNAVAILABLE_SYSFS".equals(DisplayHardware.lowerPanelStatus()),
                "BR-14 never bind without a path to the real attribute: " + DisplayHardware.lowerPanelStatus());
        finish();
    }

    static void bindFails() {
        start(false);
        io.failBind = true;
        DisplayHardware.setLowerPanelListener(interceptor);
        sample();
        check(!io.bind && !io.sysfs && "UNAVAILABLE_BIND".equals(DisplayHardware.lowerPanelStatus()),
                "BR-15 failed bind leaves nothing mounted");
        finish();
    }

    static void watchFails() {
        start(false);
        io.failWatch = true;
        DisplayHardware.setLowerPanelListener(interceptor);
        sample();
        check(!io.bind && !io.sysfs && "UNAVAILABLE_WATCH".equals(DisplayHardware.lowerPanelStatus()),
                "BR-16 never keep a bind without a watch on the request file");
        finish();
    }

    static void realUnreadable() {
        start(false);
        io.failRealRead = true;
        DisplayHardware.setLowerPanelListener(interceptor);
        sample();
        check(!io.bind && !io.sysfs && "UNAVAILABLE_REAL_UNREADABLE".equals(DisplayHardware.lowerPanelStatus()),
                "BR-17 unreadable real attribute: no interception");
        finish();
    }

    /** A daemon replaced in the same boot finds the mounts and a request left waiting. */
    static void reuse() {
        start(false);
        io.sysfs = true;
        io.bind = true;
        io.files.put(BypassRamInterceptor.REQUEST, "0x1");
        DisplayHardware.setLowerPanelListener(interceptor);
        sample();
        check("ARMED".equals(DisplayHardware.lowerPanelStatus()) && count("BYPASS_INTERCEPT_REUSED real=0;request=1") == 1,
                "BR-18 mounts reused: " + DisplayHardware.lowerPanelStatus());
        check(io.stores.size() == 1 && "1@On".equals(io.stores.get(0)), "BR-18b waiting request applied at start: " + io.stores);
        finish();
    }

    /** A framework write between the copy and the bind already reached the attribute. */
    static void writeDuringBind() {
        start(false);
        io.frameworkDuringBind = "1";
        DisplayHardware.setLowerPanelListener(interceptor);
        sample();
        check("ARMED".equals(DisplayHardware.lowerPanelStatus()) && "1".equals(io.kernel),
                "BR-19 armed after the racing write");
        check("1".equals(io.files.get(BypassRamInterceptor.REQUEST).trim()) && io.stores.size() == 1,
                "BR-19b the request follows the racing write, nothing re-applied: " + io.files + " " + io.stores);
        finish();
    }

    static void status() {
        DisplayHardware.setLowerPanelListener(null);
        check("NONE".equals(DisplayHardware.lowerPanelStatus()), "BR-20 no listener: NONE");
    }

    private static void setLatch(boolean value) {
        try {
            java.lang.reflect.Field latch = DisplayActionCoordinator.class.getDeclaredField("lowerFailureLatched");
            latch.setAccessible(true);
            latch.setBoolean(null, value);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    public static void main(String[] args) {
        Map<String, Runnable> cases = new java.util.LinkedHashMap<>();
        cases.put("arm", BypassRamInterceptorTest::arm);
        cases.put("request", BypassRamInterceptorTest::requestLowerOn);
        cases.put("topthenboth", BypassRamInterceptorTest::topRefreshThenBoth);
        cases.put("control", BypassRamInterceptorTest::withoutInterception);
        cases.put("platformon", BypassRamInterceptorTest::platformOn);
        cases.put("noninteractive", BypassRamInterceptorTest::nonInteractive);
        cases.put("rejects", BypassRamInterceptorTest::rejects);
        cases.put("lastwins", BypassRamInterceptorTest::lastRequestWins);
        cases.put("stale", BypassRamInterceptorTest::staleEvent);
        cases.put("latched", BypassRamInterceptorTest::latched);
        cases.put("fixoff", BypassRamInterceptorTest::fixOffIdle);
        cases.put("drain", BypassRamInterceptorTest::fixOffDrains);
        cases.put("fixoffuser", BypassRamInterceptorTest::fixOffByUser);
        cases.put("sysfsfails", BypassRamInterceptorTest::sysfsFails);
        cases.put("bindfails", BypassRamInterceptorTest::bindFails);
        cases.put("watchfails", BypassRamInterceptorTest::watchFails);
        cases.put("realunreadable", BypassRamInterceptorTest::realUnreadable);
        cases.put("reuse", BypassRamInterceptorTest::reuse);
        cases.put("bindrace", BypassRamInterceptorTest::writeDuringBind);
        cases.put("status", BypassRamInterceptorTest::status);
        if (args.length == 1) {
            Runnable selected = cases.get(args[0]);
            if (selected == null) throw new IllegalArgumentException("unknown case " + args[0]);
            selected.run();
        } else {
            for (Runnable test : cases.values()) test.run();
        }
        System.out.println("BypassRamInterceptorTest PASS checks=" + checks);
    }
}
