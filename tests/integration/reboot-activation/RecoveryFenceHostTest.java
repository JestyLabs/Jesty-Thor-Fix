package com.thor.displaypowertest;

/** Real durable activation/fence logic; storage and kernel boot observations are synthetic. */
public final class RecoveryFenceHostTest {
    private static final String KEY = "activation_v3_recovery_pending";
    private static final String NEXT = "00000000-0000-0000-0000-000000000002";
    private static int checks;
    private static void check(boolean value, String why) {
        checks++;
        if (!value) throw new AssertionError(why);
    }
    private static RebootActivation.Ticket ticket() {
        return RebootActivation.ticket(new AutoService(), FixturePreferences.INSTANCE);
    }
    private static RebootActivation.Ticket authorized() {
        FixtureState.reset("1.7.5");
        check(!ticket().isAuthorized(), "new package needs independent boot");
        FixtureState.boot = NEXT;
        RebootActivation.Ticket result = ticket();
        check(result.isAuthorized(), "independent boot permits normal start");
        return result;
    }
    public static void main(String[] args) {
        FixtureState.reset("1.7.5");
        RebootActivation.Ticket unprepared = ticket();
        check(!unprepared.beginRecovery(), "unauthorized activation cannot create recovery permission");
        check(!FixtureState.durable.containsKey(KEY), "unauthorized recovery writes no pending fence");
        RebootActivation.Ticket t = authorized();
        check(!t.completeRecoveryExit(), "receipt cannot clear an absent pending fence");
        check(t.beginRecovery(), "durable fence can precede COMMIT");
        check(NEXT.equals(FixtureState.durable.get(KEY)), "pending kernel persisted before signal");
        check(!ticket().isAuthorized(), "same boot pending blocks subsequent ticket");
        check(t.completeRecoveryExit(), "clean receipt permits durable fence removal");
        check(ticket().isAuthorized(), "confirmed clean exit restores prior authorization");

        t = authorized();
        FixtureState.commitFailure = FixtureState.commitUpdatesMemory = true;
        check(!t.beginRecovery(), "failed pending write cannot authorize signal");
        check(!ticket().isAuthorized(), "failed pending write remains held in process");

        t = authorized();
        check(t.beginRecovery(), "prepare clear-failure case");
        FixtureState.commitFailure = FixtureState.commitUpdatesMemory = true;
        check(!t.completeRecoveryExit(), "failed clear cannot authorize successor");
        check("".equals(FixtureState.saved.get(KEY)), "fixture reproduces commit(false) cache removal");
        check(!ticket().isAuthorized(), "unconfirmed cache clear remains held");
        FixtureState.commitFailure = FixtureState.commitUpdatesMemory = false;
        FixtureState.saved = new java.util.HashMap<>(FixtureState.durable);
        FixtureState.clearRecoveryProcessCache();
        check(!ticket().isAuthorized(), "durable pending survives app process restart");
        FixtureState.updateTime++;
        check(!ticket().isAuthorized(), "package stamp change cannot clear pending same boot");
        FixtureState.boot = "00000000-0000-0000-0000-000000000003";
        check(ticket().isAuthorized(), "distinct valid boot permits normal recovery");

        authorized();
        FixtureState.saved.put(KEY, "malformed");
        check(ticket().evidence() == RebootActivation.Evidence.STORAGE_UNAVAILABLE,
                "malformed fence retains storage-specific guidance");
        check(ticket().pendingMessage().contains("storage"), "storage message remains specific");
        FixtureState.boot = "00000000-0000-0000-0000-000000000003";
        check(!ticket().isAuthorized(), "reboot cannot make malformed storage into proof");

        t = authorized();
        FixtureState.bootReadThrows = true;
        check(!t.beginRecovery(), "missing fresh boot observation cannot authorize signal");
        System.out.println("RecoveryFenceHostTest passed: " + checks + " checks; storage/boot MODELLED");
    }
}
