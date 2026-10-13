package android.net;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/** Host-only socket fixture: no kernel peer identity or Android runtime. */
public class LocalSocket implements AutoCloseable {
    public final boolean trusted;
    public final AtomicInteger reads = new AtomicInteger();
    public final AtomicInteger closed = new AtomicInteger();
    public volatile int timeoutMs = -1;
    public volatile boolean failInput, failOutput, failClose;
    private final InputStream input;
    private final ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();

    public LocalSocket(boolean trusted, int... commandBytes) {
        this.trusted = trusted;
        byte[] data = new byte[commandBytes.length];
        for (int i = 0; i < data.length; i++) data[i] = (byte) commandBytes[i];
        final ByteArrayInputStream bytes = new ByteArrayInputStream(data);
        input = new InputStream() {
            @Override public int read() throws IOException {
                reads.incrementAndGet();
                if (failInput) throw new IOException("synthetic read error");
                return bytes.read();
            }
        };
    }
    public void setSoTimeout(int ms) { timeoutMs = ms; }
    public InputStream getInputStream() { return input; }
    public OutputStream getOutputStream() throws IOException {
        if (failOutput) throw new IOException("synthetic write error");
        return outputBytes;
    }
    public synchronized String response() {
        return new String(outputBytes.toByteArray(), StandardCharsets.UTF_8);
    }
    @Override public void close() throws IOException {
        closed.incrementAndGet();
        if (failClose) throw new IOException("synthetic close error");
    }
}
