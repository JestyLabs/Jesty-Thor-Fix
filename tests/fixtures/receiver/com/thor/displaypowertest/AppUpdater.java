package com.thor.displaypowertest;
import android.content.Context;
import android.content.Intent;
/** Host-only spy: compiles the real UpdateInstallReceiver, not the Android updater implementation. */
final class AppUpdater {
    static int confirmations, abandonCalls, clearCalls, lastSessionId;
    static Intent lastConfirmation;
    static void reset() {
        confirmations = abandonCalls = clearCalls = 0;
        lastSessionId = -1;
        lastConfirmation = null;
    }
    static void deliverConfirmation(Context context, Intent intent, int sessionId) {
        confirmations++;
        lastConfirmation = intent;
        lastSessionId = sessionId;
    }
    static void abandonSession(Context context, int sessionId) {
        abandonCalls++;
        lastSessionId = sessionId;
    }
    static void clearDownloads(Context context) { clearCalls++; }
}
