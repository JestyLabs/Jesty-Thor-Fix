import com.thor.displaypowertest.HallNodeModel;

public final class HallNodeModelTest {
    public static void main(String[] args) {
        check(HallNodeModel.choose(new String[] {"gpio-keys", "hall_switch"},
                new String[] {"0", "0"}) == 1, "the named Thor device wins, even without caps");
        check(HallNodeModel.choose(new String[] {"lid", "hall_switch"},
                new String[] {"1", "1"}) == 1, "the named device wins over another SW_LID");
        check(HallNodeModel.choose(new String[] {"hall_switch", "hall_switch"},
                new String[] {"1", "1"}) == -1, "two named devices are ambiguous");
        check(HallNodeModel.choose(new String[] {"gpio-keys", "lid"},
                new String[] {"0", "1"}) == 1, "a single SW_LID device is the fallback");
        check(HallNodeModel.choose(new String[] {"a", "b"},
                new String[] {"1", "21"}) == -1, "two SW_LID devices are ambiguous");
        check(HallNodeModel.choose(new String[] {"a", "b"},
                new String[] {"2", ""}) == -1, "SW_TABLET_MODE alone is not a lid");
        check(HallNodeModel.choose(new String[0], new String[0]) == -1, "no devices");
        check(HallNodeModel.choose(new String[] {"a"}, new String[0]) == -1, "mismatched arrays");
        check(HallNodeModel.choose(null, null) == -1, "null input");

        check(HallNodeModel.hasSwLid("1"), "bit 0");
        check(HallNodeModel.hasSwLid("4001"), "bit 0 with other switches");
        check(HallNodeModel.hasSwLid("10 1"), "bit 0 is in the last word");
        check(!HallNodeModel.hasSwLid("1 0"), "a high word does not contain bit 0");
        check(!HallNodeModel.hasSwLid("0"), "no switches");
        check(!HallNodeModel.hasSwLid(""), "empty");
        check(!HallNodeModel.hasSwLid("z1"), "malformed");
        check(!HallNodeModel.hasSwLid(null), "null");
        System.out.println("HallNodeModelTest passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
