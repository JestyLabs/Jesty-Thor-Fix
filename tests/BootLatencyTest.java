import com.thor.displaypowertest.ProcessWait;
import com.thor.displaypowertest.Telemetry;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;

public final class BootLatencyTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    /** Exits a fixed time after construction; no real child process needed. */
    private static final class FakeProcess extends Process {
        private final long exitAtNanos;

        FakeProcess(long exitAfterMs) {
            exitAtNanos = System.nanoTime() + exitAfterMs * 1_000_000L;
        }

        @Override public OutputStream getOutputStream() { return new ByteArrayOutputStream(); }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(new byte[0]); }
        @Override public InputStream getErrorStream() { return new ByteArrayInputStream(new byte[0]); }
        @Override public int waitFor() { throw new UnsupportedOperationException(); }
        @Override public void destroy() {}

        @Override public int exitValue() {
            if (System.nanoTime() < exitAtNanos) throw new IllegalThreadStateException();
            return 0;
        }
    }

    private static void processWait() throws Exception {
        check(ProcessWait.exited(new FakeProcess(0L), 2000L), "an exited process returns at once");
        long best = Long.MAX_VALUE;
        for (int i = 0; i < 5; i++) {
            long start = System.nanoTime();
            check(ProcessWait.exited(new FakeProcess(20L), 2000L), "a short child exits");
            best = Math.min(best, (System.nanoTime() - start) / 1_000_000L);
        }
        // The inherited Process.waitFor(timeout) would take at least 100 ms here.
        check(best < 80L, "short child no longer pays a 100 ms poll quantum: " + best + " ms");
        long start = System.nanoTime();
        check(!ProcessWait.exited(new FakeProcess(10_000L), 150L), "timeout still reports failure");
        long waited = (System.nanoTime() - start) / 1_000_000L;
        check(waited >= 140L && waited < 1000L, "timeout is honoured: " + waited + " ms");
    }

    private static String[] parse(String state) throws Exception {
        return Telemetry.parseCrtcPair(new BufferedReader(new StringReader(state)));
    }

    private static void crtcPair() throws Exception {
        String both = "crtc[181]: crtc-0\n\tenable=1\n\tactive=1\n\tself_refresh_active=0\n"
                + "crtc[243]: crtc-1\n\tenable=1\n\tactive=0\n";
        String[] value = parse(both);
        check("1".equals(value[0]) && "0".equals(value[1]), "one read yields top and bottom");
        value = parse("crtc[243]: crtc-1\n\tactive=1\ncrtc[181]: crtc-0\n\tactive=0\n");
        check("0".equals(value[0]) && "1".equals(value[1]), "order of CRTC blocks is irrelevant");
        value = parse("crtc[181]: crtc-0\n\tactive=1\n");
        check("1".equals(value[0]) && "?".equals(value[1]), "missing CRTC stays unknown");
        StringBuilder far = new StringBuilder("crtc[243]: crtc-1\n");
        for (int i = 0; i < 20; i++) far.append("\tfield").append(i).append("=0\n");
        far.append("\tactive=1\n");
        value = parse(far.toString());
        check("?".equals(value[1]), "active= beyond the 14-line window is not attributed");
        value = parse("crtc[181]: crtc-0\ncrtc[999]: unrelated\n\tactive=1\n");
        check("?".equals(value[0]), "another CRTC's active flag is not attributed to top");
        value = parse("crtc[181]: crtc-0\n\tactive=unexpected\n");
        check("?".equals(value[0]), "malformed active flag stays unknown");
        check("?".equals(parse("")[0]), "empty state is unknown");
    }

    public static void main(String[] args) throws Exception {
        processWait();
        crtcPair();
        System.out.println("BootLatencyTest passed");
    }
}
