import com.thor.displaypowertest.BottomIdleRestoreQueue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Deterministic HOST interleavings; no OS, vendor, input or display classes. */
public final class BottomIdleRestoreQueueTest {
    private static void check(boolean condition, String why) {
        if (!condition) throw new AssertionError(why);
    }
    private static boolean await(CountDownLatch latch) {
        try { return latch.await(5, TimeUnit.SECONDS); }
        catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError("unexpected interrupt", error);
        }
    }
    public static void main(String[] args) {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch lastDone = new CountDownLatch(1);
        CountDownLatch newDone = new CountDownLatch(1);
        AtomicInteger concurrent = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        AtomicReference<Throwable> errors = new AtomicReference<>();
        List<Long> seen = Collections.synchronizedList(new ArrayList<Long>());
        BottomIdleRestoreQueue queue = new BottomIdleRestoreQueue(lease -> {
            int active = concurrent.incrementAndGet();
            peak.accumulateAndGet(active, Math::max);
            seen.add(lease);
            if (lease == 1L) {
                started.countDown();
                if (!await(release)) throw new AssertionError("first stalled restore");
            }
            if (lease == 3L) lastDone.countDown();
            if (lease == 4L) newDone.countDown();
            concurrent.decrementAndGet();
        }, errors::set);
        check(!queue.request(-1L), "invalid lease must never create worker");
        check(queue.request(1L), "first restore queued");
        check(await(started), "first callback entered");
        check(queue.request(2L) && queue.request(3L),
                "newer touch coalesces while first composer call is held");
        check(seen.equals(Arrays.asList(1L)), "blocked worker must not run second task");
        release.countDown();
        check(await(lastDone), "latest restore eventually runs");
        check(peak.get() == 1, "no parallel restoration threads");
        check(seen.equals(Arrays.asList(1L, 3L)), "stale intermediate restore omitted");
        check(queue.request(4L), "queue can accept a new restore after blocking");
        check(await(newDone), "later restore completes");
        check(seen.equals(Arrays.asList(1L, 3L, 4L)), "exact lease ordering");
        check(errors.get() == null, "restore callback must not throw: " + errors.get());
        System.out.println("BottomIdleRestoreQueueTest passed: single worker, latest lease coalesced");
    }
}
