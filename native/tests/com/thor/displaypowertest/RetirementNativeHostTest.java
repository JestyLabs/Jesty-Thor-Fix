package com.thor.displaypowertest;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Exercises actual JNI guards without opening sockets or signalling processes. */
public final class RetirementNativeHostTest {
    private interface Checked { void run() throws Exception; }
    private static int checks;

    private static void denied(String text, Checked action) throws Exception {
        try {
            action.run();
            throw new AssertionError("Expected IOException: " + text);
        } catch (IOException expected) {
            if (!expected.getMessage().contains(text)) {
                throw new AssertionError("Expected guard '" + text + "', got " + expected, expected);
            }
            checks++;
        }
    }

    private static void api(String name, Class<?> result, Class<?>... arguments)
            throws Exception {
        Method method = RetirementNative.class.getDeclaredMethod(name, arguments);
        int modifiers = method.getModifiers();
        if (!Modifier.isStatic(modifiers) || !Modifier.isNative(modifiers)
                || Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers)
                || Modifier.isPrivate(modifiers) || method.getReturnType() != result
                || method.getExceptionTypes().length != 1
                || method.getExceptionTypes()[0] != IOException.class) {
            throw new AssertionError("Native API contract: " + name);
        }
        checks++;
    }

    public static void main(String[] args) throws Exception {
        if ("missing-library".equals(args[0])) {
            denied("native library unavailable", RetirementNative::load);
        } else if ("invalid-helper".equals(args[0])) {
            denied("one installed APK", RetirementNative::loadHelper);
        } else if ("relative-helper".equals(args[0])) {
            denied("not an installed APK file", RetirementNative::loadHelper);
        } else {
            api("applicationUid", int.class);
            api("listen", int.class, String.class);
            api("accept", int.class, int.class, int.class, int.class);
            api("connect", int.class, String.class, int.class, int.class);
            api("peerPid", int.class, int.class);
            api("peerUid", int.class, int.class);
            api("sendFd", void.class, int.class, int.class, int.class);
            api("receiveFd", int.class, int.class, int.class);
            api("request", String.class, int.class, char.class, int.class);
            api("writeLine", void.class, int.class, String.class, int.class);
            api("readLine", String.class, int.class, int.class, int.class);
            api("bindProcess", int.class, int.class);
            api("requireSameProcess", void.class, int.class, int.class, int.class);
            api("signalAndAwaitExit", void.class, int.class, int.class, int.class);
            api("close", void.class, int.class);
            api("cleanupServer", void.class, int.class, String.class);
            RetirementNative.load();
            RetirementNative.load();
            denied("UID discovery unsupported", RetirementNative::applicationUid);
            denied("credentials unsupported", () -> RetirementNative.peerPid(-1));
            denied("credentials unsupported", () -> RetirementNative.peerUid(-1));
            denied("credentials unsupported", () -> RetirementNative.accept(-1, 0, 10));
            denied("credentials unsupported", () -> RetirementNative.connect("unused", 1, 10));
            denied("descriptors unsupported", () -> RetirementNative.bindProcess(-1));
            denied("descriptors unsupported", () -> RetirementNative.requireSameProcess(-1, -1, 123));
            denied("signalling unsupported", () -> RetirementNative.signalAndAwaitExit(-1, -1, 10));
            denied("Invalid descriptor", () -> RetirementNative.close(-1));
            denied("No owned listener", () -> RetirementNative.cleanupServer(-1, "unused"));
            denied("private retire-", () -> RetirementNative.listen("relative"));
            denied("Invalid endpoint nonce", () -> RetirementNative.listen(
                    "/nonexistent/retire-0000000000000000000000000000000g"));
            denied("Null string", () -> RetirementNative.listen(null));
            denied("Null string", () -> RetirementNative.writeLine(-1, null, 10));
            denied("Only printable ASCII", () -> RetirementNative.writeLine(-1, "A\nB", 10));
            denied("Only printable ASCII", () -> RetirementNative.writeLine(-1, "A\rB", 10));
            denied("Only printable ASCII", () -> RetirementNative.writeLine(-1, "A\u0000B", 10));
            denied("Only printable ASCII", () -> RetirementNative.writeLine(-1, "\u0080", 10));
            denied("String exceeds", () -> RetirementNative.writeLine(-1, "x".repeat(16385), 10));
            denied("Invalid line limit", () -> RetirementNative.readLine(-1, 0, 10));
            denied("Invalid line limit", () -> RetirementNative.readLine(-1, 16385, 10));
            denied("Timeout must be positive", () -> RetirementNative.readLine(-1, 1, 0));
            denied("Timeout must be positive", () -> RetirementNative.writeLine(-1, "I", 0));
            denied("Timeout must be positive", () -> RetirementNative.sendFd(-1, -1, 0));
            denied("Timeout must be positive", () -> RetirementNative.receiveFd(-1, 0));
            denied("12000ms bound", () -> RetirementNative.readLine(-1, 1, 12001));
            denied("12000ms bound", () -> RetirementNative.writeLine(-1, "I", 12001));
            denied("12000ms bound", () -> RetirementNative.accept(-1, 0, 12001));
            denied("12000ms bound", () -> RetirementNative.connect("unused", 1, 12001));
            denied("12000ms bound", () -> RetirementNative.sendFd(-1, -1, 12001));
            denied("12000ms bound", () -> RetirementNative.receiveFd(-1, 12001));
            denied("Expected Unix stream", () -> RetirementNative.sendFd(-1, -1, 10));
            denied("Expected Unix stream", () -> RetirementNative.receiveFd(-1, 10));
            denied("Only I or Q", () -> RetirementNative.request(-1, 'X', 10));
            denied("Only I or Q", () -> RetirementNative.request(-1, '\n', 10));
            denied("Only I or Q", () -> RetirementNative.request(-1, '\0', 10));
            denied("Timeout must be positive", () -> RetirementNative.request(-1, 'I', 0));
            denied("12000ms bound", () -> RetirementNative.request(-1, 'Q', 12001));
            denied("Expected Unix stream", () -> RetirementNative.request(-1, 'I', 10));
        }
        System.out.println("PASS actual JNI HOST guards: " + checks + " (" + args[0] + ")");
    }
}
