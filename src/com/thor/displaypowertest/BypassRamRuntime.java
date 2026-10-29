package com.thor.displaypowertest;

import android.os.FileObserver;
import android.system.Os;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Device side of BypassRamInterceptor in the root daemon. */
final class BypassRamRuntime implements BypassRamInterceptor.Io {
    private static final long COMMAND_TIMEOUT_MS = 3000L;

    private BypassRamRuntime() {}

    /** Called once by the daemon after the exclusive lock and before the watcher starts. */
    static void install() {
        ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "thor-bypass-ram");
            thread.setDaemon(true);
            return thread;
        });
        DisplayHardware.setLowerPanelListener(
                new BypassRamInterceptor(new BypassRamRuntime(), worker));
    }

    @Override public boolean run(String... argv) {
        Process process = null;
        try {
            process = new ProcessBuilder(argv).redirectErrorStream(true).start();
            return ProcessWait.exited(process, COMMAND_TIMEOUT_MS) && process.exitValue() == 0;
        } catch (Throwable error) {
            return false;
        } finally {
            if (process != null) process.destroy();
        }
    }

    @Override public String read(String path) {
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String line = reader.readLine();
            return line == null ? null : line.trim();
        } catch (Throwable error) {
            return null;
        }
    }

    @Override public boolean write(String path, String value) {
        try (FileOutputStream out = new FileOutputStream(path)) {
            out.write((value + "\n").getBytes(StandardCharsets.US_ASCII));
            return true;
        } catch (Throwable error) {
            return false;
        }
    }

    @Override public boolean mkdir(String path) {
        File dir = new File(path);
        if (!dir.isDirectory() && !dir.mkdir()) return false;
        try {
            Os.chmod(path, 0700);
            return true;
        } catch (Throwable error) {
            return false;
        }
    }

    @Override public String mountInfo() {
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/self/mountinfo"))) {
            String line;
            while ((line = reader.readLine()) != null) out.append(line).append('\n');
            return out.toString();
        } catch (Throwable error) {
            return null;
        }
    }

    @Override public String interactive() {
        return DisplayActionCoordinator.readInteractive();
    }

    @Override public boolean lowerLatched() {
        return DisplayActionCoordinator.isLowerFailureLatched();
    }

    @Override public AutoCloseable watch(String path, Runnable onWrite) {
        try {
            FileObserver observer = new FileObserver(path,
                    FileObserver.CLOSE_WRITE | FileObserver.MODIFY) {
                @Override public void onEvent(int event, String name) {
                    onWrite.run();
                }
            };
            observer.startWatching();
            return observer::stopWatching;
        } catch (Throwable error) {
            return null;
        }
    }
}
