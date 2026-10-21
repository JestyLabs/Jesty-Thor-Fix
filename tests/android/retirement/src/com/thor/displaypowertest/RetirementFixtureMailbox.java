package com.thor.displaypowertest;

import android.os.SystemClock;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Emulator fixture only: two fixed operations, observed by an external owner.
 * This is not a vendor Binder implementation or a production fallback.
 */
final class RetirementFixtureMailbox {
    private static int sequence;
    private RetirementFixtureMailbox() {}

    static boolean submitHelper(String command) {
        Matcher match = Pattern.compile(" app_process / R ([0-9a-f]{32}) 100( recovery)? ").matcher(command);
        if (!match.find()) throw new AssertionError("Fixed helper command required");
        String nonce = match.group(1);
        boolean recovery = match.group(2) != null;
        if (!LiveRetirement.helperCommand(nonce, true, false, false, recovery).equals(command)) {
            throw new AssertionError("No arbitrary command is permitted in the fixture");
        }
        return request((recovery ? "RECOVERY " : "HELPER ") + nonce);
    }

    static boolean launchSuccessor() { return request("SUCCESSOR"); }

    private static boolean request(String value) {
        try {
            if (!SecureChannel.path().startsWith(RetirementFixtureHardware.DIRECTORY + "/")) {
                throw new AssertionError("Disposable fixture path required");
            }
            int number = ++sequence;
            if (number > 2) throw new AssertionError("Only helper then successor is allowed");
            File request = new File(RetirementFixtureHardware.DIRECTORY, "owner-request-" + number);
            File ack = new File(RetirementFixtureHardware.DIRECTORY, "owner-ack-" + number);
            if (ack.exists() || !request.createNewFile()) throw new AssertionError("Stale fixture request");
            try (FileOutputStream stream = new FileOutputStream(request)) {
                stream.write((value + "\n").getBytes(StandardCharsets.US_ASCII));
            }
            long until = SystemClock.elapsedRealtime() + 5000L;
            while (!ack.isFile() && SystemClock.elapsedRealtime() < until) SystemClock.sleep(10L);
            if (!ack.isFile()) return false;
            String accepted = new String(Files.readAllBytes(ack.toPath()), StandardCharsets.US_ASCII);
            return (value + "\n").equals(accepted);
        } catch (java.io.IOException unavailable) { return false; }
    }
}
