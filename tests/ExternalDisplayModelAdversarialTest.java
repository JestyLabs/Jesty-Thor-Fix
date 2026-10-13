import com.thor.displaypowertest.ExternalDisplayModel;

/** No device or dumpsys process: malformed viewport text must fail closed. */
public final class ExternalDisplayModelAdversarialTest {
    private static int assertions;
    private static final String TOP = "DisplayViewport{type=INTERNAL, valid=true, "
            + "isActive=true, displayId=0, uniqueId='local:top'}";
    private static final String BOTTOM = "DisplayViewport{type=EXTERNAL, valid=true, "
            + "isActive=true, displayId=4, uniqueId='local:bottom'}";
    private static String viewports(String... entries) {
        StringBuilder s = new StringBuilder("mViewports=[");
        for (String entry : entries) {
            if (s.length() > "mViewports=[".length()) s.append(", ");
            s.append(entry);
        }
        return s.append(']').toString();
    }
    private static void expect(Boolean expected, String actual, String reason) {
        assertions++;
        Boolean result = ExternalDisplayModel.hasExtraExternalViewport(actual);
        if (expected == null ? result != null : !expected.equals(result)) {
            throw new AssertionError(reason + ": expected=" + expected + " actual=" + result);
        }
    }
    public static void main(String[] args) {
        String dock = "DisplayViewport{type=EXTERNAL, valid=true, "
                + "isActive=true, displayId=7, uniqueId='local:dock'}";
        expect(Boolean.FALSE, viewports(TOP, BOTTOM), "integrated lower panel is not external dock");
        expect(Boolean.TRUE, viewports(TOP, BOTTOM, dock), "third external viewport excludes Guard");
        expect(Boolean.TRUE, viewports(dock, TOP, BOTTOM), "order independent");
        expect(Boolean.FALSE, viewports(TOP, BOTTOM, "DisplayViewport{type=EXTERNAL, "
                + "valid=true, isActive=false, displayId=7}"), "inactive dock does not block Guard");
        expect(Boolean.FALSE, viewports(TOP, BOTTOM, "DisplayViewport{type=EXTERNAL, "
                + "valid=false, isActive=true, displayId=7}"), "invalid dock does not block Guard");

        expect(null, null, "null dumpsys output");
        expect(null, "", "empty dumpsys output");
        expect(null, "something unrelated", "no viewport marker");
        expect(null, "mViewports=[]", "no evidence is not proof of zero external displays");
        expect(null, "mViewports=corrupt", "malformed viewport header");
        expect(null, "mViewports=[DisplayViewport{type=EXTERNAL", "truncated viewport");
        expect(null, viewports(TOP, BOTTOM) + ", DisplayViewport{type=EXTERNAL",
                "trailing malformed entry must not be silently ignored");
        expect(null, viewports(TOP, BOTTOM,
                "DisplayViewport{type=EXTERNAL, valid=true, isActive=true}"),
                "missing active external displayId is unknown");
        expect(null, viewports(TOP, BOTTOM,
                "DisplayViewport{type=EXTERNAL, valid=true, isActive=true, displayId=-7}"),
                "negative external displayId is unknown");
        expect(null, viewports(TOP, BOTTOM,
                "DisplayViewport{type=EXTERNAL, valid=true, isActive=true, displayId=4294967296}"),
                "integer overflow is unknown, not an exception");
        expect(null, viewports(TOP, BOTTOM,
                "DisplayViewport{type=EXTERNAL, valid=true, isActive=true, displayId=4,"
                        + " displayId=7}"), "conflicting active IDs cannot establish no dock");
        expect(Boolean.TRUE, viewports(TOP, BOTTOM, dock.replace("displayId=7","displayId=8")),
                "another valid active external ID blocks Guard");
        System.out.println("ExternalDisplayModelAdversarialTest passed (" + assertions
                + " fail-safe input checks)");
    }
}
