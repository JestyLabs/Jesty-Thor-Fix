package com.thor.displaypowertest;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public final class SocketClient {
    private SocketClient() {}

    public static String request(char command, int timeoutMs) throws Exception {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress("127.0.0.1", 3804), timeoutMs);
        socket.setSoTimeout(timeoutMs);
        socket.getOutputStream().write((byte) command);
        socket.getOutputStream().flush();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                socket.getInputStream(), StandardCharsets.UTF_8));
        String response = reader.readLine();
        socket.close();
        if (response == null || !response.startsWith("ok=1")) {
            throw new IllegalStateException(response == null ? "No daemon acknowledgement" : response);
        }
        return response;
    }
}
