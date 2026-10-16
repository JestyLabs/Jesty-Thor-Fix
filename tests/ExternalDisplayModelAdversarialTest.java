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
        // Five Codex R3 malformed closed-entry reproductions.
        expect(null, viewports(TOP, BOTTOM,
                "DisplayViewport{type=EXTERNAL, valid=true, displayId=7}"),
                "missing isActive is not evidence of no dock");
        expect(null, viewports(TOP, BOTTOM,
                "DisplayViewport{type=EXTERNAL, valid=unknown, isActive=true, displayId=7}"),
                "unknown validity cannot be ignored");
        expect(null, viewports(TOP, BOTTOM,
                "DisplayViewport{type=EXTERNAL, valid=true, isActive=unknown, displayId=7}"),
                "unknown active state cannot be ignored");
        expect(null, viewports(TOP, BOTTOM,
                "DisplayViewport{type=EXTERNAL, valid=true, isActive=true, displayId=4, displayId=-7}"),
                "malformed duplicate ID is unknown");
        expect(null, viewports(TOP, BOTTOM, "DisplayViewport{garbage}"),
                "malformed closed viewport is unknown");
        expect(null, viewports(TOP, BOTTOM,
                "DisplayViewport{type=EXTERNAL, valid=true, isActive=true, displayId=4, displayId=7}"),
                "duplicate valid IDs remain rejected");
        expect(Boolean.FALSE, viewports(TOP, BOTTOM,
                "DisplayViewport{type=EXTERNAL, valid=true, isActive=false, displayId=7}"),
                "valid inactive external remains ignored");
        expect(Boolean.FALSE, viewports(TOP, BOTTOM,
                "DisplayViewport{type=EXTERNAL, valid=false, isActive=true, displayId=7}"),
                "known invalid external remains ignored");
        // Android's DisplayViewport may include Rect(...) with internal commas.
        // Those are metadata values, not separate malformed viewport fields.
        String topWithRect = "DisplayViewport{type=INTERNAL, valid=true, isActive=true, "
                + "displayId=0, logicalFrame=Rect(0, 0 - 1920, 1080), "
                + "physicalFrame=Rect(0, 0 - 1920, 1080)}";
        String bottomWithRect = "DisplayViewport{type=EXTERNAL, valid=true, isActive=true, "
                + "displayId=4, logicalFrame=Rect(0, 0 - 1920, 1080)}";
        String dockWithRect = "DisplayViewport{type=EXTERNAL, valid=true, isActive=true, "
                + "displayId=7, uniqueId='local:hdmi,port', "
                + "physicalFrame=Rect(0, 0 - 1280, 720)}";
        expect(Boolean.FALSE, viewports(topWithRect, bottomWithRect),
                "commas inside Rect values cannot invalidate known integrated screens");
        expect(Boolean.TRUE, viewports(topWithRect, bottomWithRect, dockWithRect),
                "external dock with nested comma metadata must be recognized");
        expect(Boolean.FALSE, viewports(topWithRect, bottomWithRect,
                "DisplayViewport{type=EXTERNAL, valid=false, isActive=true, "
                + "displayId=7, physicalFrame=Rect(0, 0 - 1280, 720)}"),
                "known invalid dock metadata remains ignored");
        expect(null, viewports(topWithRect, bottomWithRect,
                "DisplayViewport{type=EXTERNAL, valid=true, isActive=true, "
                + "displayId=7, physicalFrame=Rect(0, 0 - 1280, 720}"),
                "unclosed nested metadata cannot authorize a no-dock decision");
        System.out.println("ExternalDisplayModelAdversarialTest passed (" + assertions
                + " fail-safe input checks)");
    }
}
