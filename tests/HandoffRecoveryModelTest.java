import com.thor.displaypowertest.HandoffRecoveryModel;
import com.thor.displaypowertest.HandoffRecoveryModel.Action;

public final class HandoffRecoveryModelTest {
    public static void main(String[] args) {
        int composer = HandoffRecoveryModel.EXIT_COMPOSER_NOT_RESTARTED;
        for (boolean boot : new boolean[] {false, true}) {
            for (boolean held : new boolean[] {false, true}) {
                for (boolean watcher : new boolean[] {false, true}) {
                    check(HandoffRecoveryModel.afterHelperExit(0, boot, held, watcher)
                            == Action.NONE, "a successful helper hands over");
                }
            }
        }
        check(HandoffRecoveryModel.afterHelperExit(composer, true, true, true)
                == Action.RECOVER_IN_PLACE,
                "boot daemon recovers in place when the compositor never restarted");
        check(HandoffRecoveryModel.afterHelperExit(composer, false, false, true)
                == Action.RELEASE_WAKE_LOCK, "runtime toggle only ends the transition");
        check(HandoffRecoveryModel.afterHelperExit(composer, true, false, true)
                == Action.RELEASE_WAKE_LOCK, "no boot hold left to clear");
        check(HandoffRecoveryModel.afterHelperExit(composer, true, true, false)
                == Action.HOLD_FOR_REPLACEMENT, "a dead watcher is never trusted in place");
        check(HandoffRecoveryModel.afterHelperExit(composer, false, false, false)
                == Action.HOLD_FOR_REPLACEMENT, "a dead watcher at runtime is held too");
        int[] restarted = {
            HandoffRecoveryModel.EXIT_PACKAGE_PATH,
            HandoffRecoveryModel.EXIT_DAEMON_IDENTITY,
            HandoffRecoveryModel.EXIT_KILL_FAILED,
            HandoffRecoveryModel.EXIT_OLD_DAEMON_ALIVE,
            1, 2, 127, 143, -1
        };
        for (int exit : restarted) {
            for (boolean boot : new boolean[] {false, true}) {
                for (boolean held : new boolean[] {false, true}) {
                    for (boolean watcher : new boolean[] {false, true}) {
                        check(HandoffRecoveryModel.afterHelperExit(exit, boot, held, watcher)
                                == Action.HOLD_FOR_REPLACEMENT,
                                "exit " + exit + " may follow a framework restart: hold");
                    }
                }
            }
        }
        check(HandoffRecoveryModel.composerNotRestarted(composer), "composer abort is identified");
        for (int exit : new int[] {0, 1, 2, HandoffRecoveryModel.EXIT_PACKAGE_PATH,
                HandoffRecoveryModel.EXIT_OLD_DAEMON_ALIVE}) {
            check(!HandoffRecoveryModel.composerNotRestarted(exit),
                    "exit " + exit + " does not prove the old compositor kept running");
        }
        int[] codes = {
            HandoffRecoveryModel.EXIT_COMPOSER_NOT_RESTARTED,
            HandoffRecoveryModel.EXIT_PACKAGE_PATH,
            HandoffRecoveryModel.EXIT_DAEMON_IDENTITY,
            HandoffRecoveryModel.EXIT_KILL_FAILED,
            HandoffRecoveryModel.EXIT_OLD_DAEMON_ALIVE
        };
        for (int i = 0; i < codes.length; i++) {
            check(codes[i] > 2 && codes[i] < 126, "abort codes avoid shell status values");
            for (int j = i + 1; j < codes.length; j++) {
                check(codes[i] != codes[j], "abort codes are distinct");
            }
        }
        check("COMPOSER_NOT_RESTARTED".equals(HandoffRecoveryModel.reason(10)), "reason text");
        check("HELPER_EXIT_1".equals(HandoffRecoveryModel.reason(1)), "unknown exit reason");
        check("HELPER_EXIT_NEGATIVE".equals(HandoffRecoveryModel.reason(-3)), "negative exit");
        System.out.println("HandoffRecoveryModelTest passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
