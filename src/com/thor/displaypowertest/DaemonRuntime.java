package com.thor.displaypowertest;

import android.net.LocalServerSocket;
import android.os.SystemClock;
import android.util.Log;

/**
 * Composition root of the root daemon. It builds the daemon's objects with
 * explicit constructor dependencies and starts them in the former D.main
 * order.
 *
 * BootSafety, DaemonState, DisplayActionCoordinator, LidGuard and
 * WatcherSupervisor stay static: the smali watcher, display callback and wake
 * repair call them directly (see "Why Java and smali are both present").
 */
public final class DaemonRuntime {
    private final DaemonArgs args;
    private final SystemProbe probe = new SystemProbe();
    private final BootTrace trace;
    private final BootSession session;
    private final BootCoordinator boot;
    private final CpuFixController cpuFix;
    private final DaemonCommandHandler commands;

    public DaemonRuntime(DaemonArgs args) {
        this.args = args;
        int pid = android.os.Process.myPid();
        trace = new BootTrace(SecureChannel.currentBootId(), pid);
        session = new BootSession(args.holdRequested || !probe.bootCompleted(),
                args.phaseStartedAt >= 0L ? args.phaseStartedAt : SystemClock.elapsedRealtime(),
                args.cpuFixDesired, args.lidGuardDesired);
        boot = new BootCoordinator(session, probe, trace);
        cpuFix = new CpuFixController(probe, trace, new TransitionWakeLock(), session, boot);
        boot.attach(cpuFix);
        commands = new DaemonCommandHandler(cpuFix, trace, pid);
    }

    public void run() throws Exception {
        boolean coordinating = session.active();
        if (coordinating) {
            String[] launch = DaemonLaunchScript.traceFields(System.getenv("JT"));
            trace.mark("DAEMON_MAIN", "launch=" + args.launchKind()
                    + ";receiver_ms=" + launch[0]
                    + ";service_ms=" + launch[1]
                    + ";socket=" + launch[2]
                    + ";launch_wait_ms=" + launch[3]
                    + ";process_start_ms=" + probe.processStartMs());
        }
        BootSafety.begin(coordinating);
        String earlyAttempt = cpuFix.adoptEarlyAttempt();
        if (!earlyAttempt.startsWith("ok=1")) {
            Log.e("ThorDisplayDaemon", "early CPU attempt rejected: " + earlyAttempt);
        }
        String earlyHook = EarlyCpuBootHookManager.reconcile(session.cpuFixDesired());
        if (coordinating) {
            trace.mark("EARLY_CPU_HOOK_RECONCILE",
                    "result=" + earlyHook.replace(';', ','));
        }
        if (!earlyHook.startsWith("ok=1")) {
            Log.e("ThorDisplayDaemon", "early CPU hook reconcile failed: " + earlyHook);
        }
        DaemonState.setEnabled(args.displayFixEnabled);
        if (!coordinating) LidGuard.setEnabled(session.lidGuardDesired());
        LocalServerSocket listener = SecureChannel.listen();
        if (coordinating) trace.mark("LISTEN_OK", null);
        WatcherSupervisor.start();
        DisplayEventManager.register();
        if (coordinating) trace.mark("SERVICES_REGISTERED", null);
        if (!coordinating) cpuFix.resumePersistedAttempt();
        DaemonIpcServer server = new DaemonIpcServer(listener, commands);
        if (coordinating) {
            final boolean afterComposerRestart = args.afterComposerRestart;
            new Thread(() -> boot.run(afterComposerRestart), "thor-boot-coordinator").start();
        }
        if (args.afterComposerRestart) {
            commands.traceFirstIdentityQuery();
            new Thread(new AndroidRecoveryTrace(probe, trace),
                    "thor-android-recovery-trace").start();
        }
        Log.d("ThorDisplayDaemon", "READY " + DaemonIdentity.VERSION
                + " enabled=" + args.displayFixEnabled + " bootHold=" + coordinating);
        server.serveForever();
    }
}
