package android.net;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/** Host-only deterministic accept queue. */
public class LocalServerSocket {
    private final BlockingQueue<Object> queue = new LinkedBlockingQueue<>();
    private static final Object STOP = new Object();
    public final AtomicInteger accepted = new AtomicInteger();
    public void offer(LocalSocket socket) { queue.add(socket); }
    public void stop() { queue.add(STOP); }
    public LocalSocket accept() throws IOException {
        try {
            Object item = queue.take();
            if (item == STOP) throw new IOException("synthetic server shutdown");
            accepted.incrementAndGet();
            return (LocalSocket) item;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("accept interrupted", interrupted);
        }
    }
}
