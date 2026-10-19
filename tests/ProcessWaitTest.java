import com.thor.displaypowertest.ProcessWait;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Host-only timing and interruption characterization of the production poller.
 * FakeProcess never launches a subprocess or touches the Thor.
 */
public final class ProcessWaitTest {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        FakeProcess alreadyExited = new FakeProcess(true);
        check(ProcessWait.exited(alreadyExited, 0L),
                "already exited process succeeds at the zero-timeout boundary");
        check(ProcessWait.exited(alreadyExited, -1L),
                "already exited process is observed even when the budget has elapsed");
        check(alreadyExited.polls.get() == 2,
                "already exited process is checked once per call, without waiting");

        FakeProcess neverExited = new FakeProcess(false);
        long startNs = System.nanoTime();
        check(!ProcessWait.exited(neverExited, 0L),
                "zero timeout does not block on a running process");
        check(!ProcessWait.exited(neverExited, -50L),
                "negative timeout does not become an indefinite wait");
        check(elapsedMs(startNs) < 1000L,
                "zero or negative timeout returns promptly");
        check(neverExited.polls.get() == 2,
                "expired budgets do not spin on a running process");

        FakeProcess completes = new FakeProcess(false);
        Thread exitSoon = new Thread(() -> {
            try { Thread.sleep(30L); } catch (InterruptedException e) { return; }
            completes.markExited();
        }, "fake-child-completion");
        exitSoon.start();
        check(ProcessWait.exited(completes, 3000L),
                "process exit observed before a generous deadline");
        exitSoon.join(1000L);
        check(!exitSoon.isAlive(), "completion thread joins promptly");
        check(completes.polls.get() > 1,
                "poller observes running and completed states");

        FakeProcess timedOut = new FakeProcess(false);
        check(!ProcessWait.exited(timedOut, 20L),
                "non-exiting child eventually reaches its configured deadline");
        check(timedOut.polls.get() >= 1,
                "poller checks child process status");

        FakeProcess interrupted = new FakeProcess(false);
        AtomicBoolean interruptedDelivered = new AtomicBoolean(false);
        AtomicReference<Throwable> unexpected = new AtomicReference<>();
        Thread waiter = new Thread(() -> {
            try {
                ProcessWait.exited(interrupted, 5000L);
            } catch (InterruptedException expected) {
                interruptedDelivered.set(true);
            } catch (Throwable error) {
                unexpected.set(error);
            }
        }, "fake-child-interrupt");
        waiter.start();
        long maxUntil = System.nanoTime() + 1000_000_000L;
        while (interrupted.polls.get() == 0 && System.nanoTime() < maxUntil) {
            Thread.yield();
        }
        check(interrupted.polls.get() > 0,
                "waiter reached process status check before interruption");
        waiter.interrupt();
        waiter.join(1500L);
        check(!waiter.isAlive() && interruptedDelivered.get(),
                "interruption propagates and stops the wait promptly");
        check(unexpected.get() == null,
                "interruption creates no other worker failures");

        System.out.println("ProcessWaitTest passed (" + assertions + " host checks)");
    }

    private static long elapsedMs(long startNs) {
        return (System.nanoTime() - startNs) / 1_000_000L;
    }

    private static void check(boolean value, String label) {
        assertions++;
        if (!value) throw new AssertionError(label);
    }

    private static final class FakeProcess extends Process {
        private final AtomicBoolean exited = new AtomicBoolean();
        private final AtomicInteger polls = new AtomicInteger();
        private FakeProcess(boolean exitedInitially) { exited.set(exitedInitially); }
        private void markExited() { exited.set(true); }
        @Override public OutputStream getOutputStream() { return new ByteArrayOutputStream(); }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(new byte[0]); }
        @Override public InputStream getErrorStream() { return new ByteArrayInputStream(new byte[0]); }
        @Override public int waitFor() throws InterruptedException {
            while (!exited.get()) Thread.sleep(5L);
            return 0;
        }
        @Override public int exitValue() {
            polls.incrementAndGet();
            if (!exited.get()) throw new IllegalThreadStateException("fake child still running");
            return 0;
        }
        @Override public void destroy() { markExited(); }
    }
}
