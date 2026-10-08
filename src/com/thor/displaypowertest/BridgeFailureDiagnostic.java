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
        if (error instanceof InvocationTargetException
                && ((InvocationTargetException) error).getTargetException() != null) {
            cause = ((InvocationTargetException) error).getTargetException();
        }
        if (cause instanceof SecurityException) return "SECURITY_DENIED";
        if (cause instanceof ReflectiveOperationException
                || error instanceof ReflectiveOperationException) return "REFLECTION_FAILED";
        if (cause instanceof LinkageError) return "CLASS_LINKAGE_FAILED";
        if (cause instanceof RuntimeException) return "RUNTIME_FAILURE";
        return "OTHER_FAILURE";
    }
}
