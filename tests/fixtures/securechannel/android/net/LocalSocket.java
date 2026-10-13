package android.net;
import java.io.FileDescriptor;
import java.io.IOException;
import java.util.ArrayDeque;

/** Host-only planned sockets; no Android or Unix socket is opened. */
public final class LocalSocket implements AutoCloseable {
    public static final class Plan {
        public final Integer uid;
        public boolean connectFailure, timeoutFailure, credentialFailure;
        public Plan(Integer uid) { this.uid = uid; }
    }
    private static final ArrayDeque<Plan> plans = new ArrayDeque<>();
    public static LocalSocket latest;
    public static void enqueue(Plan plan) { plans.addLast(plan); }
    public static void reset() { plans.clear(); latest = null; }
    private final Plan plan;
    public int timeout = -1, closes, connects;
    public LocalSocket() {
        plan = plans.isEmpty() ? new Plan(null) : plans.removeFirst();
        latest = this;
    }
    public void connect(LocalSocketAddress address) throws IOException {
        connects++;
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
    public void bind(LocalSocketAddress address) { throw new AssertionError("No bind in host test"); }
    public FileDescriptor getFileDescriptor() { throw new AssertionError("No descriptor in host test"); }
    @Override public void close() { closes++; }
}
