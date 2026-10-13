package com.thor.displaypowertest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/** Host characterization: retention, one-shot delivery and paired session identity. */
public final class UpdateConfirmationStoreTest {
    public static void main(String[] args) throws Exception {
        UpdateConfirmationStore<Object> store = new UpdateConfirmationStore<>();
        check(store.take() == null, "no Android confirmation before callback");
        check(store.pendingSessionId() == -1, "no active session to preserve");

        Object first = new Object();
        store.defer(first, 12);
        check(store.pendingSessionId() == 12, "queued session is not stale");
        UpdateConfirmationStore.Pending<Object> delivered = store.take();
        check(delivered != null && delivered.confirmation == first && delivered.sessionId == 12,
                "resume receives the original session/confirmation pair");
        check(store.take() == null && store.pendingSessionId() == -1,
                "delivery is consumed exactly once and releases its session");

        // Characterize current implementation, not endorse it as a future security policy:
        // when paused, the last pending callback replaces the previous one.
        Object newer = new Object();
        store.defer(first, 12);
        store.defer(newer, 17);
        check(store.pendingSessionId() == 17, "last callback replaces deferred session");
        delivered = store.take();
        check(delivered != null && delivered.confirmation == newer && delivered.sessionId == 17,
                "replacement preserves the newer callback/session pair");
        store.defer(null, -1);
        check(store.take() == null && store.pendingSessionId() == -1,
                "missing confirmation cannot be shown");

        store.defer(first, 12);
        store.defer(newer, 17);
        check(!store.discardSession(12) && store.pendingSessionId() == 17,
                "old terminal callback must preserve newer pending session");
        check(store.discardSession(17) && store.take() == null,
                "matching terminal callback cancels deferred confirmation");
        store.defer(first, 12);
        store.discardAll();
        check(store.take() == null, "starting newer attempt discards old prompt");

        // Two threads may race across lifecycle/callback boundaries; a pair may
        // never be torn, even if the current one-slot policy discards another event.
        for (int n = 0; n < 80; n++) {
            UpdateConfirmationStore<Object> concurrent = new UpdateConfirmationStore<>();
            Object a = new Object();
            Object b = new Object();
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread t1 = new Thread(() -> put(concurrent, a, 41, ready, go, failure));
            Thread t2 = new Thread(() -> put(concurrent, b, 73, ready, go, failure));
            t1.start();
            t2.start();
            ready.await();
            go.countDown();
            t1.join();
            t2.join();
            if (failure.get() != null) throw new AssertionError("callback worker failed", failure.get());
            UpdateConfirmationStore.Pending<Object> pair = concurrent.take();
            check(pair != null && ((pair.confirmation == a && pair.sessionId == 41)
                    || (pair.confirmation == b && pair.sessionId == 73)),
                    "concurrent callbacks must not tear intent/session identity");
        }
        System.out.println("UpdateConfirmationStoreTest passed");
    }

    private static void put(UpdateConfirmationStore<Object> store, Object intent, int id,
            CountDownLatch ready, CountDownLatch go, AtomicReference<Throwable> failure) {
        ready.countDown();
        try {
            go.await();
            store.defer(intent, id);
        } catch (Throwable error) {
            failure.compareAndSet(null, error);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
