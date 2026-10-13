package com.thor.displaypowertest;

/** Tests an actual session owner, not a policy mock. */
public final class UpdateSessionOwnershipTest {
    private static int checks;
    public static void main(String[] args) {
        UpdateSessionOwnership owner = new UpdateSessionOwnership();
        check(!owner.ownsSession(-1) && !owner.finishSession(9), "unknown status ignored");
        long first = owner.beginTransfer();
        check(first > 0 && owner.transferInProgress(), "first transfer reserved");
        check(owner.beginTransfer() == -1L, "cross-Activity transfer overlap rejected");
        check(!owner.ownsSession(9), "precommit/download phase does not own stale id");
        check(owner.registerSession(first, 9), "Android session attached to active transfer");
        check(!owner.registerSession(first, 10), "duplicate session binding forbidden");
        check(owner.ownsSession(9), "legitimate callback is recognized");
        check(!owner.finishSession(11), "unrelated callback cannot invalidate active session");
        check(owner.ownsSession(9), "unrelated callback preserves current session");
        check(owner.finishSession(9), "matching terminal callback completes session");
        check(!owner.finishSession(9), "duplicate terminal callback cannot repeat cleanup");
        owner.finishTransfer(first);
        long second = owner.beginTransfer();
        check(second > first && owner.currentSessionId() == -1, "new attempt resets old ownership");
        check(!owner.finishSession(9), "old abort during new download cannot clear new download");
        check(!owner.registerSession(first, 10), "old worker cannot attach a session to the new transfer");
        check(owner.registerSession(second, 11), "new Android session is attached");
        check(!owner.finishSession(9) && owner.ownsSession(11), "late session-9 callback cannot damage session 11");
        owner.revokeSession(first, 11);
        check(owner.ownsSession(11), "old worker cannot revoke the next session");
        owner.revokeSession(second, 11);
        check(!owner.ownsSession(11), "failed noncommitted session no longer owns callbacks");
        owner.finishTransfer(first);
        check(owner.transferInProgress(), "old worker finish cannot unlock new transfer");
        owner.finishTransfer(second);
        long third = owner.beginTransfer();
        check(owner.registerSession(third, 19), "next attempt can begin");
        owner.finishTransfer(third);
        check(owner.finishSession(19), "valid terminal callback after worker completion is recognized");
        check(!owner.finishSession(19), "post-terminal duplicate stays harmless");
        check(!new UpdateSessionOwnership().finishSession(19), "process restart must not claim unknown session");
        System.out.println("UpdateSessionOwnershipTest passed (" + checks + " checks)");
    }
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
