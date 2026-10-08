package com.thor.displaypowertest;

import java.lang.reflect.InvocationTargetException;

/**
 * Sanitized diagnostic codes for the vendor bridge. No Android API, shell
 * commands, user data, exception messages, or retry decisions live here.
 */
public final class BridgeFailureDiagnostic {
    private BridgeFailureDiagnostic() {}

    public static String failureReason(Throwable error) {
        if (error == null) return "UNKNOWN";
        Throwable cause = error;
        for (int depth = 0; depth < 8 && cause instanceof InvocationTargetException; depth++) {
            Throwable target = ((InvocationTargetException) cause).getTargetException();
            if (target == null || target == cause) break;
            cause = target;
        }
        if (cause instanceof SecurityException) return "SECURITY_DENIED";
        if (cause instanceof ReflectiveOperationException) return "REFLECTION_FAILED";
        if (cause instanceof LinkageError) return "CLASS_LINKAGE_FAILED";
        if (cause instanceof RuntimeException) return "RUNTIME_FAILURE";
        return "OTHER_FAILURE";
    }
}
