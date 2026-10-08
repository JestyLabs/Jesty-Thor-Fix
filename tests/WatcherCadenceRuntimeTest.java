import com.thor.displaypowertest.WatcherCadence;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Tests the real monitor bridge; Android clock/log are host-only fixtures. */
public final class WatcherCadenceRuntimeTest {
    public static void main(String[] args) throws Exception {
        WatcherCadence.beginSample();
        Thread waiter = new Thread(() -> {
            try { WatcherCadence.awaitNextSample(); }
            catch (InterruptedException e) { throw new AssertionError(e); }
        });
        waiter.start();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (waiter.getState() != Thread.State.TIMED_WAITING && waiter.isAlive()
                && System.nanoTime() < deadline) Thread.yield();
        if (waiter.getState() != Thread.State.TIMED_WAITING)
            throw new AssertionError("idle wait was not observable");
        WatcherCadence.onDisplayEvent();
        waiter.join(2000L);
        if (waiter.isAlive()) throw new AssertionError("callback did not release monitor wait");

        long reading = WatcherCadence.beginDrmRead(-1L, false, false, false);
        CountDownLatch eventSent = new CountDownLatch(1);
        Thread callback = new Thread(() -> {
            WatcherCadence.onDisplayEvent();
            eventSent.countDown();
        });
        callback.start();
        if (!eventSent.await(2L, TimeUnit.SECONDS)) throw new AssertionError("callback deadlock");
        WatcherCadence.completeDrmRead(reading);
        long following = WatcherCadence.beginDrmRead(android.os.SystemClock.elapsedRealtime(),
                false, false, false);
        if (following <= reading) throw new AssertionError("concurrent event was consumed by older I/O");
        callback.join();
        WatcherCadence.completeDrmRead(following);
        if (WatcherCadence.beginDrmRead(android.os.SystemClock.elapsedRealtime(),
                false, false, false) != -1L) throw new AssertionError("event not consumed");
        System.out.println("WatcherCadenceRuntimeTest passed (monitor wake and concurrent I/O generation)");
    }
}
