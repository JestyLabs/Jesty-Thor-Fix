package com.thor.displaypowertest;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Credentials;
import android.net.LocalSocket;
import android.os.Bundle;
import android.os.SystemClock;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

/**
 * OWNER ONLY / NOT RUN here. Real app/JNI/helper/receipt and published server;
 * panel, CPU, watcher and successor boot-phase transition are synthetic.
 * No arbitrary command/PID, installer, reboot, hardware write or root fallback.
 */
public final class RetirementFixtureInstrumentation extends Instrumentation {
    private Bundle arguments;
    private RetirementFixtureActivity activity;
    private String lastPeerPid;

    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        arguments = args == null ? new Bundle() : args;
        start();
    }

    private static void require(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        int resultCode = Activity.RESULT_OK;
        try {
            Context context = getTargetContext();
            require(RetirementFixtureHardware.DISPOSABLE.equals(context.getPackageName()),
                    "Refuse a non-disposable target package");
            require("true".equals(arguments.getString("owner_approved_disposable")),
                    "Owner must separately approve the exact disposable candidate/trial");
            String version = arguments.getString("predecessor", "");
            require("1.7.1".equals(version) || "1.7.2".equals(version)
                    || "1.7.3".equals(version) || "1.7.4".equals(version), "Exact predecessor required");
            context.getFilesDir();
            SharedPreferences preferences = context.getSharedPreferences("state", Context.MODE_PRIVATE);
            String stage = arguments.getString("stage", "");
            if ("prepare".equals(stage)) {
                prepare(context, preferences, version);
                result.putString("outcome", "PREPARED_NOT_ACTIVATED");
            } else {
                require("activate".equals(stage), "Choose prepare or activate");
                activate(context, preferences, version, result);
            }
            result.putString("hardware", "MODELLED_NO_PHYSICAL_ACCEPTANCE");
            result.putString("exit_observation", "PRODUCT_DESCRIPTOR_RECEIPT_NOT_INDEPENDENT_SUPERVISOR");
            result.putString("acceptance", "OWNER_SUPERVISOR_AND_CLEANUP_VALIDATION_REQUIRED");
        } catch (Throwable failure) {
            result.putString("outcome", "FAIL");
            result.putString("failure", failure.getClass().getSimpleName() + ": " + failure.getMessage());
            resultCode = Activity.RESULT_CANCELED;
        } finally {
            try {
                if (activity != null) runOnMainSync(() -> activity.finish());
            } catch (Throwable cleanupFailure) {
                result.putString("outcome", "FAIL");
                result.putString("failure", "Activity cleanup failed");
                resultCode = Activity.RESULT_CANCELED;
            }
        }
        finish(resultCode, result);
    }

    private void prepare(Context context, SharedPreferences preferences, String version)
            throws Exception {
        require(preferences.getAll().isEmpty(), "Use fresh disposable preferences; never clear real app data");
        require(!RetirementFixtureHardware.ARMED.exists()
                && !RetirementFixtureHardware.READY.exists()
                && !RetirementFixtureHardware.STOP.exists(), "Refuse stale fixture markers");
        require(preferences.edit().putBoolean("fix_enabled", true)
                .putBoolean("dashboard_cpu_fix_enabled", false)
                .putBoolean("lid_guard_enabled", false)
                .putBoolean("daemon_protocol_unix_v2", true).commit(), "Durable fixture preferences");
        require(RebootActivation.ticket(context, preferences).evidence()
                == RebootActivation.Evidence.RESTART_REQUIRED, "Preparation cannot authorize same boot");
        marker(RetirementFixtureHardware.ARMED, version);
    }

    private void activate(Context context, SharedPreferences preferences, String version,
            Bundle result) throws Exception {
        String armed = new String(Files.readAllBytes(RetirementFixtureHardware.ARMED.toPath()),
                StandardCharsets.UTF_8);
        require(version.equals(armed), "Prepared predecessor must match this exact trial");
        require(!RetirementFixtureHardware.READY.exists() && !RetirementFixtureHardware.STOP.exists(),
                "Refuse replayed READY/STOP markers");
        RebootActivation.Ticket ticket = RebootActivation.ticket(context, preferences);
        require(ticket.evidence() == RebootActivation.Evidence.RESTART_REQUIRED,
                "Native trial must not bypass retirement with reboot/live authorization");
        Map<String, String> health = authenticatedIdentity();
        require(version.equals(health.get("version")), "Real frozen predecessor must already be listening");
        String oldPid = health.get("pid");
        Map<String, String> oldSnapshot = TelemetryValues.parse(SocketClient.request('Q', 2500));
        require("1.7.1".equals(version) ? !oldSnapshot.containsKey("runtime_version")
                : version.equals(oldSnapshot.get("runtime_version")), "Exact actual published Q format");
        activity = (RetirementFixtureActivity) startActivitySync(new Intent(context,
                RetirementFixtureActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        require(!"ACTIVE".equals(render(oldSnapshot)), "Old identity must not be ACTIVE");
        String route = arguments.getString("route", "service");
        require("service".equals(route) || "adapter".equals(route) || "transport".equals(route),
                "Fixed service, adapter or emulator transport route only");
        boolean transport = "transport".equals(route);
        if (transport) result.putString("vendor_bridge", "NOT_EXECUTED_EXTERNAL_FIXED_SUPERVISOR");
        String expectation = arguments.getString("expect", "success");
        require("success".equals(expectation) || "unavailable".equals(expectation),
                "Choose success or unavailable support");
        boolean unavailable = "unavailable".equals(expectation);
        if (unavailable) {
            boolean denied = false;
            try { RetirementNative.load(); }
            catch (java.io.IOException expected) { denied = true; }
            require(denied, "Unavailable variant must actually omit/deny the native library");
        }
        if ("adapter".equals(route) || transport) {
            int receipt;
            if (transport) {
                try {
                    receipt = LiveRetirement.retire(true, false, false, RetirementFixtureMailbox::submitHelper);
                } catch (java.io.IOException | LinkageError rejected) {
                    result.putString("retirement_error", rejected.toString());
                    receipt = -1;
                }
            } else receipt = PServer.stopLivePredecessorIfSafe(true, false, false);
            if (!unavailable) {
                require(receipt > 100 && Integer.toString(receipt).equals(oldPid),
                        "Actual descriptor-confirmed product receipt for the authenticated old peer");
                result.putString("retirement_receipt", "FRESH_AUTHENTICATED_EXIT");
                require(transport ? RetirementFixtureMailbox.launchSuccessor()
                        : PServer.startDaemon(true, true, false, false), "Held successor launch");
            } else require(receipt < 0, "Unavailable native cannot return a retirement receipt");
        } else {
            context.startService(new Intent(context, AutoService.class));
        }
        if (unavailable) {
            SystemClock.sleep(1000L);
            Map<String, String> unchanged = authenticatedIdentity();
            require(version.equals(unchanged.get("version")) && oldPid.equals(unchanged.get("pid")),
                    "Unavailable support preserves the same old root peer");
            require(!RebootActivation.ticket(context, preferences).isAuthorized(),
                    "Unavailable support retains reboot fallback, not live permission");
            require(!"ACTIVE".equals(render(oldSnapshot)), "Unavailable route cannot show ACTIVE");
            result.putString("outcome", "PASS_CLOSED_NATIVE_UNAVAILABLE");
            return;
        }
        Map<String, String> starting = awaitCurrent(true);
        require(!oldPid.equals(lastPeerPid), "Successor must be a new authenticated peer");
        require("1".equals(starting.get("display_actions_held")), "Successor starts held");
        require(!"ACTIVE".equals(render(starting)), "STARTING never means dashboard ACTIVE");
        if ("adapter".equals(route) || transport) {
            require(PServer.probe(true) == DaemonLaunchModel.Probe.STARTING,
                    "Authenticated same-intent STARTING before durable permission");
            require(ticket.confirmLiveHandover(), "Actual durable live marker");
        }
        long deadline = SystemClock.elapsedRealtime() + 5000L;
        while (!RebootActivation.ticket(context, preferences).isAuthorized()
                && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50L);
        require(RebootActivation.ticket(context, preferences).isAuthorized(),
                "Service/adapter persisted live permission after authenticated STARTING");
        require("ACTIVATION PENDING".equals(render(starting)), "Durable permission is still not READY");
        marker(RetirementFixtureHardware.READY, "ready");
        Map<String, String> ready = awaitCurrent(false);
        require(PServer.healthy(true), "Authenticated final READY health");
        require("ACTIVE".equals(render(ready)), "Actual renderer accepts eventual current READY");
        require(!"ACTIVE".equals(render(oldSnapshot)), "Old identity never ACTIVE even after live permission");
        require("ACTIVE".equals(render(ready)), "Current READY restores ACTIVE");
        marker(RetirementFixtureHardware.STOP, "stop-own-disposable-successor");
        deadline = SystemClock.elapsedRealtime() + 5000L;
        while (PServer.reachable() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50L);
        require(!PServer.reachable(), "Disposable successor listener must stop before functional result");
        result.putString("cleanup", "LISTENER_ABSENT_OWNER_MUST_CONFIRM_PROCESS_EXIT");
        result.putString("outcome", "PASS_TO_READY_NOT_PHYSICAL_ACCEPTANCE");
        result.putString("route", route);
    }

    private Map<String, String> awaitCurrent(boolean starting) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 25000L;
        while (SystemClock.elapsedRealtime() < deadline) {
            try {
                Map<String, String> identity = authenticatedIdentity();
                Map<String, String> snapshot = TelemetryValues.parse(SocketClient.request('Q', 2500));
                if (!DaemonIdentity.RUNTIME_ID.equals(identity.get("version"))) {
                    require(!"ACTIVE".equals(render(snapshot)), "Old peer became ACTIVE while waiting");
                } else {
                    require(DaemonIdentity.RUNTIME_ID.equals(snapshot.get("runtime_version")),
                            "Current authenticated I/Q identity agreement");
                    String phase = starting ? "WAITING_FOR_ANDROID" : "READY";
                    if (phase.equals(identity.get("boot_phase")) && phase.equals(snapshot.get("boot_phase"))) {
                        lastPeerPid = identity.get("pid");
                        return snapshot;
                    }
                }
            } catch (java.io.IOException | IllegalStateException transientGap) {
                // Actual retirement/held launch may briefly have no listener.
            }
            SystemClock.sleep(50L);
        }
        throw new AssertionError("No authenticated " + (starting ? "STARTING" : "READY") + " successor");
    }

    private Map<String, String> authenticatedIdentity() throws Exception {
        try (LocalSocket socket = SecureChannel.connect(700)) {
            Credentials peer = socket.getPeerCredentials();
            require(peer != null && peer.getUid() == 0 && peer.getPid() > 100, "Kernel root peer credentials");
            socket.getOutputStream().write((byte) 'I');
            socket.getOutputStream().flush();
            Map<String, String> identity = TelemetryValues.parse(new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)).readLine());
            require("1".equals(identity.get("ok")) && Integer.toString(peer.getPid()).equals(identity.get("pid")),
                    "I.pid must equal the kernel-authenticated socket peer");
            return identity;
        }
    }

    private String render(Map<String, String> values) {
        final String[] text = new String[1];
        runOnMainSync(() -> text[0] = activity.render(values));
        return text[0];
    }

    private static void marker(File file, String value) throws Exception {
        require(file.createNewFile(), "Do not overwrite a stale fixture marker");
        try (FileOutputStream stream = new FileOutputStream(file)) {
            stream.write(value.getBytes(StandardCharsets.UTF_8));
            stream.getFD().sync();
        }
    }
}
