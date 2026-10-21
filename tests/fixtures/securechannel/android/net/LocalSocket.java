package android.net;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;

/** Host-only planned sockets; no Android or Unix socket is opened. */
public final class LocalSocket implements AutoCloseable {
    public static final class Plan {
        public final Integer uid;
        public boolean connectFailure, timeoutFailure, credentialFailure;
        public IOException connectError;
        public String response = "";
        public boolean readFailure, writeFailure;
        public Plan(Integer uid) { this.uid = uid; }
    }
    private static final ArrayDeque<Plan> plans = new ArrayDeque<>();
    public static LocalSocket latest;
    public static void enqueue(Plan plan) { plans.addLast(plan); }
    public static void reset() { plans.clear(); latest = null; }
    private final Plan plan;
    public int timeout = -1, closes, connects;
    public int reads, writes, flushes;
    public final ByteArrayOutputStream sent = new ByteArrayOutputStream();
    public LocalSocket() {
        plan = plans.isEmpty() ? new Plan(null) : plans.removeFirst();
        latest = this;
    }
    public void connect(LocalSocketAddress address) throws IOException {
        connects++;
        if (plan.connectError != null) throw plan.connectError;
        if (plan.connectFailure) throw new IOException("synthetic connect failure");
    }
    public void setSoTimeout(int value) {
        if (plan.timeoutFailure) throw new IllegalStateException("synthetic timeout failure");
        timeout = value;
    }
    public Credentials getPeerCredentials() {
        if (plan.credentialFailure) throw new IllegalStateException("synthetic peer failure");
        return plan.uid == null ? null : new Credentials(plan.uid);
    }
    public InputStream getInputStream() {
        final ByteArrayInputStream input = new ByteArrayInputStream(plan.response.getBytes(StandardCharsets.UTF_8));
        return new InputStream() {
            @Override public int read() throws IOException {
                reads++;
                if (plan.readFailure) throw new SocketTimeoutException("synthetic read timeout");
                return input.read();
            }
            @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                // Deliberately fragmented transport, including multibyte UTF-8.
                if (length == 0) return 0;
                int value = read();
                if (value < 0) return -1;
                bytes[offset] = (byte) value;
                return 1;
            }
        };
    }
    public OutputStream getOutputStream() {
        return new OutputStream() {
            @Override public void write(int value) throws IOException {
                writes++;
                if (plan.writeFailure) throw new IOException("synthetic write failure");
                sent.write(value);
            }
            @Override public void flush() { flushes++; }
        };
    }
    public void bind(LocalSocketAddress address) { throw new AssertionError("No bind in host test"); }
    public FileDescriptor getFileDescriptor() { throw new AssertionError("No descriptor in host test"); }
    @Override public void close() { closes++; }
}
