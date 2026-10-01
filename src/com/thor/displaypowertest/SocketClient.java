package com.thor.displaypowertest;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import android.net.LocalSocket;
import java.nio.charset.StandardCharsets;

public final class SocketClient {
    private SocketClient() {}

    public static String request(char command, int timeoutMs) throws Exception {
        try (LocalSocket socket = SecureChannel.connect(timeoutMs)) {
            socket.getOutputStream().write((byte) command);
            socket.getOutputStream().flush();
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));
            String response = reader.readLine();
            if (response == null || !response.startsWith("ok=1")) {
                throw new IllegalStateException(response == null
                        ? "No daemon acknowledgement" : response);
            }
            return response;
        }
    }
}
