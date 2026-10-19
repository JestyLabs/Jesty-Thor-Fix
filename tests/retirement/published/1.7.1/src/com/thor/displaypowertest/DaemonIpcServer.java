package com.thor.displaypowertest;

import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Private Unix-socket listener: two workers, four queued connections, a
 * 1.5-second read timeout and the app UID check before any byte is read.
 */
public final class DaemonIpcServer {
    private final LocalServerSocket server;
    private final DaemonCommandHandler commands;
    private final ThreadPoolExecutor connections = new ThreadPoolExecutor(2, 2, 0L,
            TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(4));

    public DaemonIpcServer(LocalServerSocket server, DaemonCommandHandler commands) {
        this.server = server;
        this.commands = commands;
    }

    /** Accepts until the process ends; a full queue closes the new connection. */
    public void serveForever() throws IOException {
        while (true) {
            LocalSocket socket = server.accept();
            try {
                connections.execute(() -> serve(socket));
            } catch (RejectedExecutionException busy) {
                socket.close();
            }
        }
    }

    private void serve(LocalSocket socket) {
        try (LocalSocket client = socket) {
            if (!SecureChannel.isTrustedApp(client)) {
                Log.w("ThorDisplayDaemon", "rejected untrusted local client");
                return;
            }
            client.setSoTimeout(1500);
            int command = client.getInputStream().read();
            String response = command < 0 ? "ok=0;error=EMPTY_COMMAND"
                    : commands.handle((char) command);
            OutputStream output = client.getOutputStream();
            output.write((response + "\n").getBytes(StandardCharsets.UTF_8));
            output.flush();
        } catch (Throwable error) {
            Log.e("ThorDisplayDaemon", "command failed", error);
        }
    }
}
