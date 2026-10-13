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
        System.out.println("ReplacementSessionPolicyTest passed (" + checks + " checks)");
    }
}
