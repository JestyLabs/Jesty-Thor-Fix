package com.thor.displaypowertest;

import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Privileged Hall switch watcher. Unknown device state always permits normal wake. */
final class LidProtectionRuntime {
    private static final String TAG = "ThorLidGuard";
    private static final LidGuardModel model = new LidGuardModel();
    private static final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor();
    private static volatile boolean enabled;
    /** Invalidates queued Guard sleeps across OFF/ON transitions. */
    private static volatile long activationEpoch;
    private static volatile boolean watcherStarted;
    private static volatile String state = "OFF";
    private static volatile String lastAction = "NONE";
    private static volatile Boolean externalDisplay;
    private static volatile long externalCheckedAt;
    private static volatile boolean externalCheckPending;
    private static volatile long lastWakeScheduledAt;
    private static volatile boolean deferredRepair;

    private LidProtectionRuntime() {}

    public static synchronized boolean setEnabled(boolean value) {
        if (!value) {
            enabled = false;
            activationEpoch++;
            model.invalidateObservation();
            externalDisplay = null;
            externalCheckPending = false;
            externalCheckedAt = 0L;
            lastWakeScheduledAt = 0L;
            state = "OFF";
            // A previously deferred normal wake repair must not disappear when
            // the optional Guard is turned off mid-transition.
            resumeWakeRepair();
            return true;
        }
        File node = findHallNode();
        if (node == null || !node.canRead()) {
            enabled = false;
            activationEpoch++;
            model.invalidateObservation();
            externalDisplay = null;
            externalCheckPending = false;
            externalCheckedAt = 0L;
            state = "HALL UNAVAILABLE";
            resumeWakeRepair();
            return false;
        }
        activationEpoch++;
        externalDisplay = null;
        externalCheckPending = false;
        externalCheckedAt = 0L;
        enabled = true;
        int initialLid = readCurrentLid(node);
        if (initialLid >= 0) model.onSwitch(initialLid, SystemClock.elapsedRealtime());
        else model.invalidateObservation(); // Never reuse a stale CLOSED lid snapshot.
        state = initialLid == 0 ? "OPEN" : initialLid == 1
                ? "ACTIVE" : "WAITING FOR LID EVENT";
        refreshExternalDisplay();
        if (!watcherStarted) {
            watcherStarted = true;
            new Thread(() -> watch(node), "thor-hall-switch").start();
        }
        if (initialLid == 1 && !BootSafety.isHeld()) {
            final long epoch = activationEpoch;
            scheduler.schedule(() -> trySleep("sleep_after_close", false, epoch),
                    1500L, TimeUnit.MILLISECONDS);
        }
        return true;
    }

    public static boolean isEnabled() { return enabled; }
    public static String lid() { return model.lid().name().toLowerCase(); }
    public static String state() { return state.replace(' ', '_'); }
    public static int blockedWakes() { return model.blockedWakes(); }
    public static boolean isSleepDecisionCurrent(long epoch) {
        return model.isSleepDecisionCurrent(epoch);
    }
    public static String lastAction() { return lastAction; }
    public static String externalDisplay() {
        // Telemetry keeps the same key and cached value, but an OFF guard must
        // never start an external-display dumpsys from a passive Q request.
        if (enabled) refreshExternalDisplay();
        return externalDisplayCached();
    }
    static String externalDisplayCached() {
        Boolean external = externalDisplay;
        return external == null ? "unknown" : external ? "1" : "0";
    }

    private static synchronized void refreshExternalDisplay() {
        long now = SystemClock.elapsedRealtime();
        if (!enabled || externalCheckPending || now - externalCheckedAt < 30000L) return;
        externalCheckPending = true;
        final long probeEpoch = activationEpoch;
        new Thread(() -> {
            Boolean result = externalDisplayConnected();
            synchronized (LidProtectionRuntime.class) {
                // An earlier ON-session probe may return after OFF or ON->OFF->ON.
                // Only the current activation is allowed to update the cache.
                if (!enabled || probeEpoch != activationEpoch) return;
                externalDisplay = result;
                externalCheckedAt = SystemClock.elapsedRealtime();
                externalCheckPending = false;
            }
        }, "thor-external-display-probe").start();
    }

    /** Called from display callback. True means the normal wake repair is deferred. */
    public static synchronized boolean onWakeEvent() {
        if (!enabled || BootSafety.isHeld() || !model.maySchedule()
                || !Boolean.FALSE.equals(externalDisplay)) return false;
        if (!Boolean.TRUE.equals(interactive())) return false;
        long now = SystemClock.elapsedRealtime();
        if (now - lastWakeScheduledAt < 500L) return deferredRepair;
        lastWakeScheduledAt = now;
        deferredRepair = true;
        DisplayActionCoordinator.cancelWakeRepair();
        final long epoch = activationEpoch;
        scheduler.schedule(() -> trySleep("sleep_after_closed_wake", true, epoch),
                500L, TimeUnit.MILLISECONDS);
        return true;
    }

    private static File findHallNode() {
        File[] entries = new File("/sys/class/input").listFiles();
        if (entries == null) return null;
        java.util.List<File> events = new java.util.ArrayList<>();
        for (File entry : entries) {
            if (entry.getName().startsWith("event")) events.add(entry);
        }
        String[] names = new String[events.size()];
        String[] switches = new String[events.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = readLine(new File(events.get(i), "device/name"));
            switches[i] = readLine(new File(events.get(i), "device/capabilities/sw"));
        }
        int chosen = HallNodeModel.choose(names, switches);
        if (chosen < 0) return null;
        if (!ThorHardwareProfile.HALL_DEVICE_NAME.equals(names[chosen])) {
            // Not reached on the tested Thor, which exposes the named device.
            Log.w(TAG, "named Hall device absent; using the only SW_LID device "
                    + events.get(chosen).getName());
        }
        return new File("/dev/input/" + events.get(chosen).getName());
    }

    /** Snapshot the current switch bit before the first event arrives. */
    private static int readCurrentLid(File node) {
        Process process = null;
        try {
            process = new ProcessBuilder("/system/bin/getevent", "-S", node.getPath()).start();
            if (!ProcessWait.exited(process, 2000L) || process.exitValue() != 0) return -1;
            String value = new BufferedReader(new InputStreamReader(
                    process.getInputStream())).readLine();
            if (value == null || !value.trim().matches("(?i)[0-9a-f]{1,8}")) return -1;
            return Integer.parseInt(value.trim(), 16) & 1;
        } catch (Throwable ignored) { return -1; }
        finally { if (process != null) process.destroy(); }
    }

    private static void watch(File node) {
        int size = android.os.Process.is64Bit() ? 24 : 16;
        byte[] event = new byte[size];
        try (FileInputStream input = new FileInputStream(node)) {
            while (true) {
                int read = 0;
                while (read < size) {
                    int count = input.read(event, read, size - read);
                    if (count < 0) throw new IllegalStateException("Hall event stream closed");
                    read += count;
                }
                int value = LidGuardModel.swLidValue(event, size == 24);
                if (value < 0) continue;
                // Serialize the enabled check and model write against OFF/ON.
                // Without this monitor a Hall read could restore CLOSED
                // immediately after OFF invalidated the model observation.
                synchronized (LidProtectionRuntime.class) {
                    if (!enabled) continue;
                    model.onSwitch(value, SystemClock.elapsedRealtime());
                    state = value == 1 ? "ACTIVE" : "OPEN";
                    if (value == 1 && !BootSafety.isHeld()) {
                        final long epoch = activationEpoch;
                        scheduler.schedule(() -> trySleep("sleep_after_close", false, epoch),
                                1500L, TimeUnit.MILLISECONDS);
                    }
                    if (value == 0) {
                        lastAction = "lid_open";
                        resumeWakeRepair();
                    }
                }
            }
        } catch (Throwable error) {
            synchronized (LidProtectionRuntime.class) {
                enabled = false;
                activationEpoch++;
                model.invalidateObservation();
                externalDisplay = null;
                externalCheckPending = false;
                externalCheckedAt = 0L;
                state = "HALL UNAVAILABLE";
                watcherStarted = false;
                resumeWakeRepair();
            }
            Log.e(TAG, "Hall watcher stopped", error);
        }
    }

    private static void trySleep(String reason, boolean blockedWake, long epoch) {
        if (!enabled || epoch != activationEpoch || BootSafety.isHeld()) return;
        // The external-display probe can block for up to two seconds.
        // Read interactivity *after* that probe and sample decision time then,
        // so a stale pre-probe AWAKE observation cannot authorize sleep.
        // This does not make the Hall event -> input command atomic.
        Boolean external = externalDisplayConnected();
        Boolean interactive = interactive();
        long now = SystemClock.elapsedRealtime();
        synchronized (LidProtectionRuntime.class) {
            // A slow synchronous probe must also be discarded after OFF/ON.
            if (!enabled || epoch != activationEpoch) return;
            externalDisplay = external;
        }
        // Never use a queued decision from an earlier ON session.
        if (!enabled || epoch != activationEpoch || BootSafety.isHeld()) return;
        // Evaluate allow/paused/epoch in one model monitor acquisition. The
        // potentially slow hardware request runs OUTSIDE that monitor, so Hall
        // events can still update the model while the command is in flight.
        LidGuardModel.SleepDecision decision = model.evaluateSleep(
                now, Boolean.TRUE.equals(interactive),
                Boolean.TRUE.equals(external), interactive != null && external != null);
        if (!decision.allowed) {
            if (decision.paused) state = "PAUSED WAKE LOOP";
            resumeWakeRepair();
            return;
        }
        boolean slept = false;
        try {
            // A lid-open event or a disabled Guard invalidates the decision.
            // requestGuardSleep() independently checks the current lid and
            // enabled state under the shared display-hardware lock.
            // Do not split an epoch pre-check from a later model mutation:
            // that would be a genuine TOCTOU window. requestGuardSleep()
            // checks the current lid/enable state under the hardware lock;
            // recordSleepIfCurrent() atomically validates the same epoch when
            // updating the model after the command returns.
            if (!enabled || epoch != activationEpoch) return;
            if (!DisplayActionCoordinator.requestGuardSleep(decision.lidEpoch)) {
                state = "SLEEP FAILED";
                lastAction = "sleep_command_failed";
                return;
            }
            if (!model.recordSleepIfCurrent(decision.lidEpoch, now, blockedWake)) {
                // A concurrent Hall transition occurred during the command.
                // Never record an attempt against the newly opened lid.
                state = "LID CHANGED";
                lastAction = "lid_changed_during_sleep";
                return;
            }
            deferredRepair = false;
            slept = true;
            state = "ACTIVE";
            lastAction = reason;
            Log.d(TAG, reason);
        } catch (Throwable error) {
            state = "SLEEP FAILED";
            lastAction = "sleep_command_failed";
            Log.e(TAG, "Sleep command failed", error);
        } finally {
            if (!slept) resumeWakeRepair();
        }
    }

    private static synchronized void resumeWakeRepair() {
        if (!deferredRepair) return;
        deferredRepair = false;
        // The normal repair is also required when the Guard was turned OFF,
        // failed to initialize or lost its Hall reader after deferring it.
        if (!BootSafety.isHeld() && DaemonState.isEnabled()
                && "1".equals(DaemonState.getMode())) {
            DisplayActionCoordinator.scheduleWakeRepair();
        }
    }

    private static Boolean interactive() {
        try {
            Class<?> manager = Class.forName("android.os.ServiceManager");
            Method getService = manager.getDeclaredMethod("getService", String.class);
            IBinder binder = (IBinder) getService.invoke(null, "power");
            Class<?> stub = Class.forName("android.os.IPowerManager$Stub");
            Object service = stub.getDeclaredMethod("asInterface", IBinder.class)
                    .invoke(null, binder);
            return (Boolean) service.getClass().getMethod("isInteractive").invoke(service);
        } catch (Throwable error) { return null; }
    }

    private static Boolean externalDisplayConnected() {
        Process process = null;
        try {
            process = new ProcessBuilder("/system/bin/dumpsys", "display").start();
            final Process running = process;
            final java.util.concurrent.atomic.AtomicReference<Boolean> external =
                    new java.util.concurrent.atomic.AtomicReference<>();
            Thread reader = new Thread(() -> {
                try (BufferedReader output = new BufferedReader(
                        new InputStreamReader(running.getInputStream()))) {
                    String line;
                    while ((line = output.readLine()) != null) {
                        if (line.contains("mViewports=")) {
                            external.set(ExternalDisplayModel.hasExtraExternalViewport(line));
                        }
                    }
                } catch (Throwable ignored) {
                    external.set(null);
                }
            }, "thor-display-probe-output");
            reader.setDaemon(true);
            reader.start();
            if (!ProcessWait.exited(process, 2000L) || process.exitValue() != 0) return null;
            reader.join(200L);
            return reader.isAlive() ? null : external.get();
        } catch (Throwable error) { return null; }
        finally { if (process != null) process.destroyForcibly(); }
    }

    private static String readLine(File path) {
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            return reader.readLine().trim();
        } catch (Throwable ignored) { return ""; }
    }
}
