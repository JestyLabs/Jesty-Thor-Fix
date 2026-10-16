import com.thor.displaypowertest.RebootActivationPolicy;
import com.thor.displaypowertest.RebootActivationPolicy.State;
import java.util.HashMap;
import java.util.Map;

public final class RebootActivationPolicyTest {
    private static int checks;
    private static final String A = "abcdef01-0000-0000-0000-000000000001";
    private static final String B = "abcdef01-0000-0000-0000-000000000002";
    public static void main(String[] args) {
        check(!RebootActivationPolicy.authorized("target:80:123", "target:80:123", A, A), "same kernel cannot authorize");
        check(!RebootActivationPolicy.authorized("target", "target", A.toUpperCase(), A), "UUID casing is not a reboot");
        check(RebootActivationPolicy.authorized("target:80:123", "target:80:123", B, A), "different kernel after baseline");
        for (String stamp : new String[]{null, "", "target:78:123", "target:80:124", "other:80:123"}) {
            check(!RebootActivationPolicy.authorized("target:80:123", stamp, B, A), "different installation");
        }
        for (String bad : new String[]{null, "", "invalid", "00000000-0000-0000-0000-000000000000"}) {
            check(!RebootActivationPolicy.authorized("target", "target", B, bad), "bad baseline boot");
            check(!RebootActivationPolicy.authorized("target", "target", bad, A), "bad current boot");
        }
        String record=RebootActivationPolicy.baselineRecord("target:80:123",A);
        check(A.equals(RebootActivationPolicy.baselineBoot(record,"target:80:123")),"record round trip");
        for(String bad:new String[]{null,"",record+"|extra",record.replace("v3|","v2|"),record.replace(A,B),
                record.substring(0,record.length()-1),"v3|target:80:123|"+A+"|"}) {
            check(RebootActivationPolicy.baselineBoot(bad,"target:80:123")==null,"malformed/checksum mismatch");
        }
        check(RebootActivationPolicy.baselineBoot(record,"target:80:124")==null,"different installation cannot reuse baseline");
        check(RebootActivationPolicy.baselineRecord("bad|stamp",A).isEmpty(),"delimiter rejected");
        Map<String,String> q = ready();
        check(RebootActivationPolicy.telemetry(q, "target") == State.READY, "exact READY");
        for (String old : new String[]{"1.7.1", "1.7.2-rc1", "1.7.2-recovery", "1.7.0", "target-unknown"}) {
            q = ready(); q.put("runtime_version", old);
            check(RebootActivationPolicy.telemetry(q, "target") == State.RESTART_REQUIRED, "old version not new ACTIVE");
        }
        q = ready(); q.remove("runtime_version");
        check(RebootActivationPolicy.telemetry(q, "target") == State.RESTART_REQUIRED, "old snapshot lacks identity");
        q = ready(); q.put("boot_phase", "WAITING_FOR_ANDROID"); q.put("display_actions_held", "1");
        check(RebootActivationPolicy.telemetry(q, "target") == State.PENDING, "STARTING not READY");
        q = ready(); q.put("display_actions_held", "1");
        check(RebootActivationPolicy.telemetry(q, "target") == State.PENDING, "held READY not active");
        q = ready(); q.put("watcher_health", "FAILED");
        check(RebootActivationPolicy.telemetry(q, "target") == State.UNAVAILABLE, "failed watcher not active");
        check(RebootActivationPolicy.telemetry(null, "target") == State.UNAVAILABLE, "missing sample");
        check(RebootActivationPolicy.restartMessage().contains("once after installation"), "first-open preparation guidance");
        check(RebootActivationPolicy.restartMessage().contains("do not uninstall"), "preserve app data");
        System.out.println("RebootActivationPolicyTest passed: " + checks + " assertions");
    }
    private static Map<String,String> ready() {
        Map<String,String> q = new HashMap<>();
        q.put("runtime_version", "target"); q.put("boot_phase", "READY");
        q.put("watcher_health", "RUNNING"); q.put("display_actions_held", "0"); return q;
    }
    private static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }
}
