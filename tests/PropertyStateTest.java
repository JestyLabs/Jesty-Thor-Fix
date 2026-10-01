import com.thor.displaypowertest.PropertyState;

public final class PropertyStateTest {
    public static void main(String[] args) {
        check("0", PropertyState.binary("0\n", 0));
        check("1", PropertyState.binary(" 1 ", 0));
        check("?", PropertyState.binary("", 0));
        check("?", PropertyState.binary(null, 0));
        check("?", PropertyState.binary("unexpected", 0));
        check("?", PropertyState.binary("1", 1));
        check(PropertyState.UNSET, PropertyState.observed(
                PropertyState.MISSING_MARKER, 0));
        check(PropertyState.UNSET, PropertyState.observed("", 0));
        check("?", PropertyState.observed(null, 0));
        check("?", PropertyState.observed(PropertyState.MISSING_MARKER, 1));
        check("?", PropertyState.observed("unexpected", 0));
        check("1", PropertyState.observed("1", 0));
        System.out.println("PropertyStateTest passed");
    }

    private static void check(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }
}
