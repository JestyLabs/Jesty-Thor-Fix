package com.thor.displaypowertest;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Real supervisor, real JVM threads; Settings watcher is an inert endpoint. */
public final class WatcherSupervisorLifecycleHostTest {
    private static int checks;
    private static void check(boolean value, String why) {
        checks++;
        if (!value) throw new AssertionError(why);
    }
    private static Thread supervisor() throws Exception {
        Constructor<WatcherSupervisor> constructor = WatcherSupervisor.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        Thread thread = new Thread(constructor.newInstance(), "fixture-supervisor");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }
    private static void cleanup(Thread supervisor) throws Exception {
        DaemonWatchThread.release.countDown();
        supervisor.interrupt();
        supervisor.join(2000L);
        check(!supervisor.isAlive(), "WL cleanup supervisor terminated");
        List<DaemonWatchThread> watchers;
        synchronized (DaemonWatchThread.all) { watchers = new ArrayList<DaemonWatchThread>(DaemonWatchThread.all); }
        for (Thread watcher : watchers) {
            watcher.join(2000L);
            check(!watcher.isAlive(), "WL cleanup watcher terminated");
        }
    }
    private static void joinedReplacement() throws Exception {
        DaemonWatchThread.reset(true);
        Thread supervisor = supervisor();
        try {
            check(DaemonWatchThread.first.await(3L, TimeUnit.SECONDS), "WL first watcher entered");
            // The real 1s first retry backoff is unchanged. If join is removed,
            // a replacement appears while the first watcher is still blocked.
            check(!DaemonWatchThread.second.await(1500L, TimeUnit.MILLISECONDS), "WL-join replacement before exit");
            check(DaemonWatchThread.created.get() == 1, "WL exactly one blocked watcher");
            DaemonWatchThread.release.countDown();
            check(DaemonWatchThread.second.await(3L, TimeUnit.SECONDS), "WL replacement after exit");
            check(DaemonWatchThread.maxLive.get() == 1, "WL no overlapping watchers");
        } finally { cleanup(supervisor); }
        System.out.println("PASS WL-join; unchanged supervisor start/join/backoff, real JVM threads");
    }
    private static void boundedFailures() throws Exception {
        DaemonWatchThread.reset(false);
        Thread supervisor = supervisor();
        try {
            supervisor.join(8000L);
            check(DaemonWatchThread.created.get() == 3, "WL-fast-failures count");
            check(!supervisor.isAlive(), "WL fast failure loop terminated");
            check("FAILED".equals(WatcherSupervisor.health()), "WL fast failure terminal health");
            check(DaemonWatchThread.maxLive.get() == 1, "WL fast failures serialized");
        } finally { cleanup(supervisor); }
        System.out.println("PASS WL-fast-failures; three rapid exits, terminal FAILED");
    }
    private static void staleHealth() {
        android.os.SystemClock.setElapsedRealtime(100000L);
        WatcherSupervisor.noteSample();
        check("RUNNING".equals(WatcherSupervisor.health()), "WL initial health");
        android.os.SystemClock.setElapsedRealtime(105000L);
        check("RUNNING".equals(WatcherSupervisor.health()), "WL health at threshold");
        android.os.SystemClock.setElapsedRealtime(105001L);
        check("STALLED".equals(WatcherSupervisor.health()), "WL health beyond threshold");
        WatcherSupervisor.noteSample();
        check("RUNNING".equals(WatcherSupervisor.health()), "WL new observation restores health");
        check(105001L == WatcherSupervisor.lastSampleAt(), "WL sample timestamp");
        check(WatcherCadence.samples == 2, "WL cadence receives samples");
        System.out.println("PASS WL-stale-health; real supervisor, controlled clock");
    }
    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            if ("join".equals(args[0])) joinedReplacement(); else boundedFailures();
            return;
        }
        staleHealth();
        joinedReplacement();
        boundedFailures();
        System.out.println("WatcherSupervisorLifecycleHostTest passed: 3 cases, " + checks
                + " checks; daemon process restart/native lock/Android watcher NOT RUN");
    }
}

final class WatcherCadence {
    static int samples;
    static void noteSample() { samples++; }
}

/** Does not read Settings, invoke coordinator or touch any hardware. */
final class DaemonWatchThread extends Thread {
    static final AtomicInteger created = new AtomicInteger();
    static final AtomicInteger live = new AtomicInteger();
    static final AtomicInteger maxLive = new AtomicInteger();
    static final List<DaemonWatchThread> all = new ArrayList<DaemonWatchThread>();
    static CountDownLatch first;
    static CountDownLatch second;
    static CountDownLatch release;
    static boolean blockFirst;
    final int ordinal;
    static void reset(boolean block) {
        created.set(0); live.set(0); maxLive.set(0);
        synchronized (all) { all.clear(); }
        first = new CountDownLatch(1);
        second = new CountDownLatch(1);
        release = new CountDownLatch(1);
        blockFirst = block;
    }
    DaemonWatchThread() {
        ordinal = created.incrementAndGet();
        setDaemon(true);
        synchronized (all) { all.add(this); }
    }
    @Override public void run() {
        int active = live.incrementAndGet();
        maxLive.accumulateAndGet(active, Math::max);
        try {
            if (ordinal == 1) {
                first.countDown();
                if (blockFirst && !release.await(5L, TimeUnit.SECONDS))
                    throw new AssertionError("fixture watcher release timeout");
            } else if (ordinal == 2) { second.countDown(); }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        } finally { live.decrementAndGet(); }
    }
}
