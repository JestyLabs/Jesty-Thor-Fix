package com.thor.displaypowertest;

import java.io.File;
import java.io.IOException;

/** Small fail-closed boundary; callers retain policy and descriptor ownership. */
final class RetirementNative {
    private static boolean loaded;

    private RetirementNative() {}

    static synchronized void load() throws IOException {
        if (loaded) return;
        try {
            System.loadLibrary("retirement");
            loaded = true;
        } catch (LinkageError | SecurityException failure) {
            throw new IOException("Retirement native library unavailable", failure);
        }
    }

    static synchronized void loadHelper() throws IOException {
        if (loaded) return;
        String classpath = System.getenv("CLASSPATH");
        if (classpath == null || classpath.indexOf(File.pathSeparatorChar) >= 0) {
            throw new IOException("Helper requires one installed APK in CLASSPATH");
        }
        File apk = new File(classpath);
        if (!apk.isAbsolute() || !apk.isFile() || !apk.getName().endsWith(".apk")) {
            throw new IOException("Helper CLASSPATH is not an installed APK file");
        }
        File parent = apk.getCanonicalFile().getParentFile();
        if (parent == null) throw new IOException("Missing installed APK directory");
        final boolean is64;
        final String[] abis;
        try {
            Object bitness = Class.forName("android.os.Process").getMethod("is64Bit").invoke(null);
            if (!(bitness instanceof Boolean)) throw new IOException("Invalid helper runtime bitness");
            is64 = ((Boolean) bitness).booleanValue();
            Object supported = Class.forName("android.os.Build").getField(is64
                    ? "SUPPORTED_64_BIT_ABIS" : "SUPPORTED_32_BIT_ABIS").get(null);
            if (!(supported instanceof String[])) throw new IOException("Invalid helper runtime ABIs");
            abis = (String[]) supported;
        } catch (ReflectiveOperationException | LinkageError | SecurityException failure) {
            throw new IOException("Cannot determine helper runtime ABI", failure);
        }
        IOException last = null;
        if (abis != null) {
            for (String abi : abis) {
                String directory = null;
                if (is64 && "arm64-v8a".equals(abi)) directory = "arm64";
                if (is64 && "x86_64".equals(abi)) directory = "x86_64";
                if (!is64 && "armeabi-v7a".equals(abi)) directory = "arm";
                if (!is64 && "x86".equals(abi)) directory = "x86";
                if (directory == null) continue;
                File library = new File(parent, "lib/" + directory + "/libretirement.so");
                if (!library.isFile()) continue;
                String canonical = library.getCanonicalPath();
                if (!canonical.startsWith(parent.getPath() + File.separator)) {
                    throw new IOException("Helper library escapes installed APK directory");
                }
                try {
                    System.load(canonical);
                    loaded = true;
                    return;
                } catch (LinkageError | SecurityException failure) {
                    last = new IOException("Installed retirement library cannot load", failure);
                }
            }
        }
        throw last != null ? last : new IOException("No installed retirement library for runtime ABI");
    }

    static native int applicationUid() throws IOException;
    static native int listen(String path) throws IOException;
    static native int accept(int listener, int expectedUid, int timeoutMs) throws IOException;
    static native int connect(String path, int expectedUid, int timeoutMs) throws IOException;
    static native int peerPid(int fd) throws IOException;
    static native int peerUid(int fd) throws IOException;
    static native void sendFd(int control, int socket, int timeoutMs) throws IOException;
    static native int receiveFd(int control, int timeoutMs) throws IOException;
    static native String request(int socket, char command, int timeoutMs) throws IOException;
    static native void writeLine(int fd, String line, int timeoutMs) throws IOException;
    static native String readLine(int fd, int limit, int timeoutMs) throws IOException;
    static native int bindProcess(int connectedSocket) throws IOException;
    static native void requireSameProcess(int pidfd, int socket, int expectedPid) throws IOException;
    static native void signalAndAwaitExit(int pidfd, int control, int timeoutMs) throws IOException;
    static native void close(int fd) throws IOException;

    /** Matching path consumes the owned listener even on cleanup error; do not close first. */
    static native void cleanupServer(int listener, String path) throws IOException;
}
