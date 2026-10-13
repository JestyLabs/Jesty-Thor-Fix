package com.thor.displaypowertest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/** Host-only command spy; never invokes real display/CPU/Lid Guard actions. */
public final class DaemonCommandHandler {
    public final AtomicInteger calls = new AtomicInteger();
    public final AtomicInteger active = new AtomicInteger();
    public final AtomicInteger maxActive = new AtomicInteger();
    public final CountDownLatch blockedStarted = new CountDownLatch(2);
    public final CountDownLatch unblock = new CountDownLatch(1);
    public String handle(char command) {
        calls.incrementAndGet();
        if (command == 'X') throw new IllegalStateException("synthetic handler error");
        if (command == 'B') {
            int running = active.incrementAndGet();
            maxActive.accumulateAndGet(running, Math::max);
            blockedStarted.countDown();
            try {
                unblock.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "ok=0;error=INTERRUPTED";
            } finally {
                active.decrementAndGet();
            }
        }
        return command == '?' ? "ok=0;error=UNKNOWN_COMMAND" : "ok=1;echo=" + command;
    }
}
