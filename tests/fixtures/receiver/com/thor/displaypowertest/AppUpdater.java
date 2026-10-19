package com.thor.displaypowertest;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

/** Host-only spy: exercises the real receiver, with explicit session ownership. */
final class AppUpdater {
    static int confirmations, abandonCalls, clearCalls, lastSessionId;
    static int ownedSession = -1;
    static Intent lastConfirmation;

    static void reset() {
        confirmations = abandonCalls = clearCalls = 0;
        ownedSession = lastSessionId = -1;
        lastConfirmation = null;
    }
    static void trackSession(int sessionId) { ownedSession = sessionId; }
    static boolean isActiveInstallSession(int sessionId) {
        return sessionId >= 0 && ownedSession == sessionId;
    }
    static void deliverConfirmation(Context context, Intent intent, int sessionId) {
        if (!isActiveInstallSession(sessionId)) return;
        confirmations++;
        lastConfirmation = intent;
        lastSessionId = sessionId;
    }
    static void finishInstallSession(Context context, int sessionId, boolean success,
            String message) {
        if (!isActiveInstallSession(sessionId)) return;
        ownedSession = -1;
        lastConfirmation = null; // Characterizes the real store's targeted invalidation.
        if (!success) {
            abandonSession(context, sessionId);
            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
        }
        clearDownloads(context);
    }
    static void abandonSession(Context context, int sessionId) {
        abandonCalls++;
        lastSessionId = sessionId;
    }
    static void clearDownloads(Context context) { clearCalls++; }
}
