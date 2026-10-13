package com.thor.displaypowertest;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/** Pure host contract; not proof of native PackageInstaller state. */
public final class ReplacementSessionPolicyTest {
    private static int checks;
    private static void check(boolean yes, String why) {
        checks++;
        if (!yes) throw new AssertionError(why);
    }
    public static void main(String[] args) {
        AtomicInteger count = new AtomicInteger();
        check(ReplacementSessionPolicy.canReplace(-1, id -> count.incrementAndGet()),
                "no native session to abandon");
        check(count.get() == 0, "no unnecessary Android call");
        check(ReplacementSessionPolicy.canReplace(9, id -> {
            count.incrementAndGet();
            check(id == 9, "correct session identity");
        }), "completed abandon permits next attempt");
        check(count.get() == 1, "one native operation");
        check(!ReplacementSessionPolicy.canReplace(9, id -> {
            throw new IOException("fake failure");
        }), "checked error blocks replacement");
        check(!ReplacementSessionPolicy.canReplace(9, id -> {
            throw new SecurityException("fake Android permission failure");
        }), "permission failure blocks replacement");
        check(!ReplacementSessionPolicy.canReplace(9, id -> {
            throw new IllegalStateException("fake native exception");
        }), "runtime exception blocks replacement");
        check(ReplacementSessionPolicy.canReplace(11, id -> {
            check(id == 11, "retry session ID");
        }), "later retry allowed on completed abandon");
        java.util.ArrayList<Integer> active = new java.util.ArrayList<>();
        ReplacementSessionPolicy.NativeSessions nativeSessions =
                new ReplacementSessionPolicy.NativeSessions() {
                    public int[] ids() {
                        int[] ids = new int[active.size()];
                        for (int i = 0; i < ids.length; i++) ids[i] = active.get(i);
                        return ids;
                    }
                    public void abandon(int id) { active.remove(Integer.valueOf(id)); }
                };
        check(ReplacementSessionPolicy.canBeginTransfer(nativeSessions),
                "empty native-session inventory permits first transfer");
        active.add(9);
        check(ReplacementSessionPolicy.canBeginTransfer(nativeSessions) && active.isEmpty(),
                "previous native session abandoned and absence confirmed");
        active.add(9);
        active.add(11);
        check(ReplacementSessionPolicy.canBeginTransfer(nativeSessions) && active.isEmpty(),
                "stale sessions from process restart all reconciled");
        active.add(9);
        check(!ReplacementSessionPolicy.canBeginTransfer(new ReplacementSessionPolicy.NativeSessions() {
            public int[] ids() { return new int[] {9}; }
            public void abandon(int id) { /* synthetic platform silently keeps session */ }
        }), "silent abandoned-session retention fails closed");
        check(!ReplacementSessionPolicy.canBeginTransfer(new ReplacementSessionPolicy.NativeSessions() {
            public int[] ids() { return new int[] {9}; }
            public void abandon(int id) throws IOException { throw new IOException("synthetic abandon failed"); }
        }), "native abandon error fails closed");
        check(!ReplacementSessionPolicy.canBeginTransfer(new ReplacementSessionPolicy.NativeSessions() {
            public int[] ids() throws IOException { throw new IOException("synthetic inventory denied"); }
            public void abandon(int id) { }
        }), "unable to enumerate native sessions fails closed");
        check(!ReplacementSessionPolicy.canBeginTransfer(new ReplacementSessionPolicy.NativeSessions() {
            public int[] ids() { return null; }
            public void abandon(int id) { }
        }), "null native session list fails closed");
        active.clear();
        System.out.println("ReplacementSessionPolicyTest passed (" + checks + " checks)");
    }
}
