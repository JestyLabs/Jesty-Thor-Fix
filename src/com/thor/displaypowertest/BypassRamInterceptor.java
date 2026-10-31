package com.thor.displaypowertest;

import android.os.SystemClock;
import android.util.Log;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Keeps the vendor's panel-RAM bypass write away from the kernel while the lower
 * panel is off (2026-10-29: such a write reproduced the blackout on demand).
 *
 * With the display fix enabled, a regular file is bind-mounted over the attribute
 * in the init mount namespace, where the vendor PServer shell runs. The framework's
 * write then lands in that file; this class applies it to the real attribute,
 * reached through a second sysfs mount, only while the lower panel is powered and
 * the device is interactive. A request made with the panel off waits for the next
 * ON. Turning the fix off applies any waiting request first, then unmounts.
 *
 * Every device access goes through Io; all state changes run on the worker.
 */
final class BypassRamInterceptor implements DisplayHardware.LowerPanelListener {
    private static final String TAG = "ThorDisplayDaemon";
    static final String DIR = "/dev/jesty-bypass";
    static final String REQUEST = DIR + "/requested";
    static final String SYSFS = DIR + "/sysfs";
    static final String REAL = SYSFS
            + "/devices/virtual/bypass_ram_class/bypass_ram_device/bypass_ram";
    static final String MOUNT = "/system/bin/mount";
    static final String UMOUNT = "/system/bin/umount";
    static final long DRAIN_CHECK_MS = 1000L;

    /** Device access. The daemon uses BypassRamRuntime; host tests use a fake. */
    interface Io {
        /** True when the command exited 0 within its timeout. */
        boolean run(String... argv);
        /** Trimmed first line, or null when unreadable. */
        String read(String path);
        boolean write(String path, String value);
        boolean mkdir(String path);
        /** /proc/self/mountinfo, or null when unreadable. */
        String mountInfo();
        /** "1", "0" or "?" from PowerManager. */
        String interactive();
        boolean lowerLatched();
        /** Calls onWrite after each completed write to path; null when unavailable. */
        AutoCloseable watch(String path, Runnable onWrite);
    }

    enum State { OFF, ARMED, DRAINING, UNAVAILABLE }

    private final Io io;
    private final Executor worker;
    private final AtomicBoolean attemptQueued = new AtomicBoolean();
    private AutoCloseable watch;
    // Written on the worker only; read by the watcher, hardware and IPC threads.
    private volatile State state = State.OFF;
    private volatile String unavailableReason = "";
    private volatile String pending;
    private volatile long pendingSince = -1L;
    private volatile String lastDeferReason;
    private volatile String lastRejected;
    // Written on the watcher thread only.
    private volatile Boolean fixEnabled;
    private long lastDrainCheckAt = -1L;

    BypassRamInterceptor(Io io, Executor worker) {
        this.io = io;
        this.worker = worker;
    }

    @Override public String status() {
        State current = state;
        if (current == State.UNAVAILABLE) return "UNAVAILABLE_" + unavailableReason;
        if (current == State.ARMED && pending != null) return "ARMED_PENDING";
        return current.name();
    }

    @Override public void onWatcherSample(boolean enabled, String bottomCrtc) {
        Boolean previous = fixEnabled;
        if (previous == null || previous != enabled) {
            fixEnabled = enabled;
            worker.execute(enabled ? this::arm : this::disarmOrDrain);
            return;
        }
        if (pending == null) return;
        if (state == State.DRAINING) {
            long now = SystemClock.elapsedRealtime();
            if (lastDrainCheckAt >= 0L && now - lastDrainCheckAt < DRAIN_CHECK_MS) return;
            lastDrainCheckAt = now;
            queueAttempt("DRAIN");
        } else if (state == State.ARMED && "1".equals(bottomCrtc)) {
            queueAttempt("WATCHER");
        }
    }

    /** Called with the hardware lock held, right after a lower ON returned. */
    @Override public void onLowerPoweredOn(String reason) {
        if (pending != null) queueAttempt("LOWER_ON");
    }

    private void queueAttempt(String source) {
        if (!attemptQueued.compareAndSet(false, true)) return;
        worker.execute(() -> {
            attemptQueued.set(false);
            attempt(source);
        });
    }

    private void onRequestWritten() {
        worker.execute(() -> {
            refreshPending();
            attempt("REQUEST");
        });
    }

    private void arm() {
        if (!Boolean.TRUE.equals(fixEnabled)) return;
        if (state == State.ARMED) return;
        String mounts = io.mountInfo();
        if (mounts == null) {
            unavailable("MOUNTINFO");
            return;
        }
        boolean bind = mounted(mounts, ThorHardwareProfile.BYPASS_RAM_NODE);
        boolean sysfs = mounted(mounts, SYSFS);
        if (bind && sysfs && startWatch()) {
            // Left by an earlier daemon in this boot (live handover or restart).
            state = State.ARMED;
            Log.d(TAG, "BYPASS_INTERCEPT_REUSED real=" + parsedOrUnknown(REAL)
                    + ";request=" + parsedOrUnknown(REQUEST));
            refreshPending();
            attempt("START");
            return;
        }
        if (bind && !io.run(UMOUNT, ThorHardwareProfile.BYPASS_RAM_NODE)) {
            unavailable("STALE_BIND");
            return;
        }
        if (!io.mkdir(DIR) || !io.mkdir(SYSFS)) {
            unavailable("DIRECTORY");
            return;
        }
        if (!sysfs && !io.run(MOUNT, "-t", "sysfs", "sysfs", SYSFS)) {
            unavailable("SYSFS");
            return;
        }
        String real = BypassRamPolicy.parse(io.read(REAL));
        if (real == null) {
            unavailable("REAL_UNREADABLE");
            return;
        }
        if (!io.write(REQUEST, real)) {
            unavailable("REQUEST_FILE");
            return;
        }
        if (!io.run(MOUNT, "--bind", REQUEST, ThorHardwareProfile.BYPASS_RAM_NODE)) {
            unavailable("BIND");
            return;
        }
        String visible = BypassRamPolicy.parse(io.read(ThorHardwareProfile.BYPASS_RAM_NODE));
        String after = io.mountInfo();
        if (after == null || !mounted(after, ThorHardwareProfile.BYPASS_RAM_NODE)
                || visible == null) {
            unavailable("VERIFY");
            return;
        }
        // A framework write between the copy and the bind reached the real attribute
        // directly; keep that value as the request unless a newer one already arrived.
        String realNow = BypassRamPolicy.parse(io.read(REAL));
        if (realNow != null && !realNow.equals(real) && real.equals(visible)) {
            io.write(REQUEST, realNow);
        }
        if (!startWatch()) {
            unavailable("WATCH");
            return;
        }
        state = State.ARMED;
        Log.d(TAG, "BYPASS_INTERCEPT_READY real=" + parsedOrUnknown(REAL));
        refreshPending();
        attempt("START");
    }

    private void disarmOrDrain() {
        if (Boolean.TRUE.equals(fixEnabled)) return;
        String mounts = io.mountInfo();
        boolean bind = mounts != null && mounted(mounts, ThorHardwareProfile.BYPASS_RAM_NODE);
        boolean sysfs = mounts != null && mounted(mounts, SYSFS);
        if (bind && sysfs && startWatch()) {
            refreshPending();
            if (pending != null) {
                state = State.DRAINING;
                Log.d(TAG, "BYPASS_INTERCEPT_DRAINING value=" + pending);
                attempt("DRAIN");
                return;
            }
        }
        disarm("FIX_OFF");
    }

    private void disarm(String cause) {
        stopWatch();
        boolean ok = true;
        String mounts = io.mountInfo();
        if (mounts == null || mounted(mounts, ThorHardwareProfile.BYPASS_RAM_NODE)) {
            ok &= io.run(UMOUNT, ThorHardwareProfile.BYPASS_RAM_NODE);
        }
        if (mounts == null || mounted(mounts, SYSFS)) ok &= io.run(UMOUNT, SYSFS);
        pending = null;
        lastDeferReason = null;
        if (!ok) {
            unavailableReason = "UNMOUNT";
            state = State.UNAVAILABLE;
            Log.d(TAG, "BYPASS_INTERCEPT_UNAVAILABLE reason=UNMOUNT;cause=" + cause);
            return;
        }
        state = State.OFF;
        Log.d(TAG, "BYPASS_INTERCEPT_OFF cause=" + cause);
    }

    /** Never leaves a bind mount without a watcher and a path to the real attribute. */
    private void unavailable(String reason) {
        stopWatch();
        String mounts = io.mountInfo();
        if (mounts == null || mounted(mounts, ThorHardwareProfile.BYPASS_RAM_NODE)) {
            io.run(UMOUNT, ThorHardwareProfile.BYPASS_RAM_NODE);
        }
        if (mounts == null || mounted(mounts, SYSFS)) io.run(UMOUNT, SYSFS);
        pending = null;
        unavailableReason = reason;
        state = State.UNAVAILABLE;
        Log.d(TAG, "BYPASS_INTERCEPT_UNAVAILABLE reason=" + reason);
    }

    private void refreshPending() {
        String raw = io.read(REQUEST);
        String wanted = BypassRamPolicy.parse(raw);
        if (wanted == null) {
            String shown = sanitize(raw);
            if (!shown.equals(lastRejected)) {
                lastRejected = shown;
                Log.d(TAG, "BYPASS_REJECTED raw=" + shown);
            }
            return;
        }
        lastRejected = null;
        String real = BypassRamPolicy.parse(io.read(REAL));
        if (wanted.equals(real)) {
            pending = null;
            lastDeferReason = null;
            return;
        }
        if (!wanted.equals(pending)) {
            pending = wanted;
            pendingSince = SystemClock.elapsedRealtime();
            lastDeferReason = null;
            Log.d(TAG, "BYPASS_REQUEST value=" + wanted + ";real=" + (real == null ? "?" : real));
        }
    }

    private void attempt(String source) {
        String wanted = pending;
        if (wanted == null) {
            if (state == State.DRAINING) disarm("DRAINED");
            return;
        }
        // PowerManager is a Binder call: keep it outside the hardware lock.
        String interactive = io.interactive();
        String deferReason = null;
        boolean applied = false;
        boolean written = false;
        String after = null;
        synchronized (DisplayHardware.class) {
            String real = BypassRamPolicy.parse(io.read(REAL));
            String dpms = io.read(ThorHardwareProfile.BOTTOM_DPMS_PATH);
            boolean latched = io.lowerLatched();
            switch (BypassRamPolicy.decide(wanted, real, dpms, interactive, latched)) {
                case NOOP:
                    applied = true;
                    break;
                case APPLY:
                    written = io.write(REAL, wanted);
                    after = BypassRamPolicy.parse(io.read(REAL));
                    applied = written && wanted.equals(after);
                    break;
                default:
                    deferReason = BypassRamPolicy.deferReason(real, dpms, interactive, latched);
                    break;
            }
        }
        if (applied) {
            if (written) {
                Log.d(TAG, "BYPASS_APPLIED value=" + wanted + ";source=" + source + ";delay_ms="
                        + (SystemClock.elapsedRealtime() - pendingSince));
            }
            if (wanted.equals(pending)) {
                pending = null;
                lastDeferReason = null;
            }
        } else if (deferReason != null) {
            if (!deferReason.equals(lastDeferReason)) {
                lastDeferReason = deferReason;
                Log.d(TAG, "BYPASS_DEFERRED value=" + wanted + ";reason=" + deferReason
                        + ";source=" + source);
            }
        } else {
            Log.d(TAG, "BYPASS_WRITE_FAILED value=" + wanted + ";after="
                    + (after == null ? "?" : after));
        }
        if (state == State.DRAINING && pending == null) disarm("DRAINED");
    }

    private boolean startWatch() {
        if (watch != null) return true;
        watch = io.watch(REQUEST, this::onRequestWritten);
        return watch != null;
    }

    private void stopWatch() {
        AutoCloseable current = watch;
        watch = null;
        if (current == null) return;
        try { current.close(); }
        catch (Exception error) { Log.e(TAG, "bypass request watch close failed", error); }
    }

    private String parsedOrUnknown(String path) {
        String value = BypassRamPolicy.parse(io.read(path));
        return value == null ? "?" : value;
    }

    /** Mount point is the fifth mountinfo field. */
    static boolean mounted(String mountInfo, String mountPoint) {
        for (String line : mountInfo.split("\n")) {
            String[] fields = line.trim().split(" ");
            if (fields.length > 4 && mountPoint.equals(fields[4])) return true;
        }
        return false;
    }

    private static String sanitize(String raw) {
        if (raw == null) return "null";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < raw.length() && out.length() < 16; i++) {
            char c = raw.charAt(i);
            out.append(Character.isLetterOrDigit(c) ? c : '?');
        }
        return out.toString();
    }
}
