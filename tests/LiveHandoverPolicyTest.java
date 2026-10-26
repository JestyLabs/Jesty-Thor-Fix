import com.thor.displaypowertest.LiveHandoverPolicy;

public final class LiveHandoverPolicyTest {
    private static int assertions;
    private static final String H = "ok=1;protocol=2;version=1.7.2;pid=12000;"
            + "boot_phase=READY;fix=1;watcher=RUNNING";
    private static final String Q = "ok=1;runtime_version=1.7.2;mode=0;top_crtc=1;bottom_crtc=1;"
            + "display_actions_held=0;display_effective=CONFIRMED;fix=1;"
            + "cpu_fix_desired=0;cpu_fix_phase=CONFIRMED;system_load_fix=0;"
            + "lid_guard=0;lid=open;external_display=unknown";
    private static void check(boolean ok, String message) {
        assertions++;
        if (!ok) throw new AssertionError(message);
    }
    private static int pid(String h, String q) {
        return LiveHandoverPolicy.safePid(h, q, true, false, false);
    }
    public static void main(String[] args) {
        check(pid(H,Q)==12000, "exact code80 ready+BOTH+matching CPU is eligible");
        String h85 = H.replace("1.7.2", "1.7.5");
        String q85 = Q.replace("1.7.2", "1.7.5");
        check(pid(h85,q85)==12000, "exact published code85 READY+BOTH is eligible");
        check(pid(h85,q85.replace(";runtime_version=1.7.5", ""))<0, "code85 requires Q runtime identity");
        check(pid(h85,q85+";runtime_version=1.7.5")<0, "code85 duplicate Q identity denied");
        check(pid(h85+";pid=12000",q85)<0, "code85 duplicate I identity denied");
        check(pid(h85,q85.replace("1.7.5","1.7.4"))<0, "code85 mismatched I/Q denied");
        check(pid(h85,q85.replace("mode=0","mode=1").replace("bottom_crtc=1","bottom_crtc=0"))<0,
                "code85 public stop remains BOTH-only");
        String h84 = H.replace("1.7.2", "1.7.4");
        String q84 = Q.replace("1.7.2", "1.7.4");
        check(pid(h84,q84)==12000, "published code84 ready+BOTH is eligible");
        check(pid(h84,q84.replace(";runtime_version=1.7.4", ""))<0,
                "code84 cannot borrow the code76 absent-runtime exception");
        check(pid(h84,q84+";runtime_version=1.7.4")<0, "code84 duplicate runtime denied");
        check(pid(h84+";pid=12000",q84)<0, "code84 duplicate identity denied");
        check(pid(h84,q84.replace("mode=0","mode=1"))<0, "code84 TOP remains denied");
        check(pid(h84,q84.replace("bottom_crtc=1","bottom_crtc=0"))<0,
                "code84 unconfirmed lower screen remains denied");
        String h83 = H.replace("1.7.2", "1.7.3");
        String q83 = Q.replace("1.7.2", "1.7.3");
        check(pid(h83,q83)==12000, "approved code83 ready+BOTH is eligible for the next release");
        check(pid(h83,q83.replace(";runtime_version=1.7.3", ""))<0,
                "code83 cannot borrow the code76 absent-runtime exception");
        check(pid(h83,q83+";runtime_version=1.7.3")<0, "code83 duplicate runtime denied");
        check(pid(h83+";pid=12000",q83)<0, "code83 duplicate identity denied");
        check(pid(H.replace("1.7.2","1.7.1"),Q.replace(";runtime_version=1.7.2",""))<0,
                "published code76 lacks Q runtime identity and cannot authorize live stop");
        check(pid(H.replace("1.7.2","1.7.1"),Q)<0,"I/Q version mismatch denied");
        for (String version : new String[]{"1.7.0","1.7.2-rc1","1.7.6","1.7.5-rc1","1.7.5-recovery","1.7.50","1.7.4-rc1","1.7.4-recovery","1.7.40",
                "1.7.3-recovery","1.7.2-hijacked","unknown"}) {
            check(pid(H.replace("1.7.2",version),Q.replace("1.7.2",version))<0,"unknown predecessor denied "+version);
        }
        for (String h : new String[]{
                H.replace("boot_phase=READY","boot_phase=HANDOFF_FAILED"),
                H.replace("boot_phase=READY","boot_phase=WAITING_FOR_ANDROID"),
                H.replace("boot_phase=READY","boot_phase=BOOT_SAFETY_TIMEOUT"),
                H.replace("watcher=RUNNING","watcher=STOPPED"),
                H.replace("fix=1","fix=0"),
                H.replace("pid=12000","pid=0"),
                H.replace("pid=12000","pid=1"),
                H.replace("protocol=2","protocol=1"),
                H.replace("ok=1","ok=0") }) {
            check(pid(h,Q)<0,"unhealthy or unauthenticated predecessor denied");
        }
        for (String q : new String[]{
                Q.replace("mode=0","mode=1"),
                Q.replace("mode=0","mode=2"),
                Q.replace("top_crtc=1","top_crtc=0"),
                Q.replace("bottom_crtc=1","bottom_crtc=0"),
                Q.replace("display_actions_held=0","display_actions_held=1"),
                Q.replace("display_effective=CONFIRMED","display_effective=MISMATCH"),
                Q.replace("fix=1","fix=0"),
                Q.replace("cpu_fix_desired=0","cpu_fix_desired=1"),
                Q.replace("cpu_fix_phase=CONFIRMED","cpu_fix_phase=PENDING"),
                Q.replace("system_load_fix=0","system_load_fix=1"),
                Q.replace("lid_guard=0","lid_guard=1"),
                Q.replace("lid=open","lid=closed"),
                Q.replace("external_display=unknown","external_display=1"),
                Q.replace("ok=1","ok=0") }) {
            check(pid(H,q)<0,"unsafe device snapshot denied");
        }
        check(pid(null,Q)<0 && pid(H,null)<0,"missing responses refused");
        check(LiveHandoverPolicy.safePid(H,
                Q.replace("cpu_fix_desired=0","cpu_fix_desired=1")
                .replace("system_load_fix=0","system_load_fix=1"),true,true,false)==12000,
                "CPU Fix ON already confirmed remains eligible");
        check(LiveHandoverPolicy.safePid(H,
                Q.replace("lid_guard=0","lid_guard=1")
                .replace("external_display=unknown","external_display=0"),true,false,true)==12000,
                "Guard ON and confirmed no dock remain eligible");
        // Every I/Q authorization field must be unambiguous. Duplicate keys
        // must not let a healthy first value conceal an unsafe later value.
        for (String suffix : new String[]{
                ";ok=0", ";protocol=1", ";version=1.7.0", ";pid=1",
                ";boot_phase=HANDOFF_FAILED", ";fix=0", ";watcher=STOPPED",
                ";version=1.7.2" }) {
            check(pid(H + suffix, Q) < 0,
                    "duplicate predecessor health field denied: " + suffix);
        }
        for (String suffix : new String[]{
                ";ok=0", ";runtime_version=1.7.1", ";mode=1",
                ";top_crtc=0", ";bottom_crtc=0",
                ";display_actions_held=1", ";display_effective=MISMATCH",
                ";fix=0", ";cpu_fix_desired=1", ";cpu_fix_phase=PENDING",
                ";system_load_fix=1", ";lid_guard=1", ";lid=closed",
                ";external_display=1", ";mode=0" }) {
            check(pid(H, Q + suffix) < 0,
                    "duplicate predecessor snapshot field denied: " + suffix);
        }
        System.out.println("LiveHandoverPolicyTest passed: "+assertions+" fail-closed checks.");
    }
}
