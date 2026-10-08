import com.thor.displaypowertest.PassiveSleepTrial;

public final class PassiveSleepTrialTest {
    private static int count;
    private static PassiveSleepTrial.Sample s(long e,long u,long en,long ch,int v,int plug,int boot,int mask) {
        return new PassiveSleepTrial.Sample(e,u,en,ch,v,300,plug,boot,mask);
    }
    private static void test(boolean ok,String message) {
        count++;
        if (!ok) throw new AssertionError(message);
    }
    private static void state(PassiveSleepTrial.Result result,String value) {
        test(result.state.equals(value), value + " actual=" + result.state);
    }
    public static void main(String[] args) {
        PassiveSleepTrial.Sample a = s(100000,50000,2000000000,1000000,3900,0,12,1);
        PassiveSleepTrial.Sample b = s(3700000,110000,1700000000,900000,3900,0,12,1);
        test(a.encode().equals(PassiveSleepTrial.Sample.decode(a.encode()).encode()),"roundtrip");
        test(PassiveSleepTrial.Sample.decode(a.encode()+"|secret") == null,"reject unknown fields");
        test(PassiveSleepTrial.Sample.decode("PRIVATE_TOKEN") == null,"reject invalid format");
        state(PassiveSleepTrial.evaluate(a,b),"PROVISIONAL");
        PassiveSleepTrial.Result r=PassiveSleepTrial.evaluate(a,b);
        test(r.suspendedMs==3540000 && r.awakeMs==60000,"clock differences");
        test(r.method.equals("ENERGY_COUNTER"),"direct method");
        test(Math.abs(r.energyWh-.3)<1e-9 && Math.abs(r.meanW-.3)<1e-9,"nWh to Wh to W");
        test(r.reportLine().contains("suspend_clock_delta_ms=3540000"),"sanitized report");
        test(r.reportLine().contains("voltage_start_mv=3900")
                && r.reportLine().contains("temperature_start_deci_c=300"),
                "bounded endpoint conditions");
        PassiveSleepTrial.Result estimate=PassiveSleepTrial.evaluate(
                s(100000,50000,-1,1000000,4000,0,12,1),
                s(3700000,110000,-1,900000,3800,0,12,1));
        test(estimate.method.equals("CHARGE_VOLTAGE_ESTIMATE")
                && Math.abs(estimate.energyWh-.39)<1e-9,"estimated charge x voltage");
        state(PassiveSleepTrial.evaluate(a,s(3700000,110000,-1,-1,-1,0,12,1)),"ENERGY_UNAVAILABLE_OR_UNRESOLVED");
        state(PassiveSleepTrial.evaluate(a,s(3700000,110000,1700000000,900000,3900,1,12,1)),"EXTERNAL_POWER_OR_UNKNOWN");
        state(PassiveSleepTrial.evaluate(a,s(3700000,110000,1700000000,900000,3900,0,13,1)),"REBOOT_DETECTED");
        state(PassiveSleepTrial.evaluate(a,s(3700000,110000,1700000000,900000,3900,0,12,3)),"FIX_REQUEST_CHANGED");
        state(PassiveSleepTrial.evaluate(a,s(3700000,110000,2100000000L,900000,3900,0,12,1)),"COUNTER_INCREASE");
        state(PassiveSleepTrial.evaluate(a,s(101000,50050,1700000000L,900000,3900,0,12,1)),"SHORT_TRIAL");
        state(PassiveSleepTrial.evaluate(a,s(3700000,9000000,1700000000L,900000,3900,0,12,1)),"CLOCK_INVALID_OR_REBOOT");
        state(PassiveSleepTrial.evaluate(a,s(3700000,110000,1700000000,900000,3900,0,-1,1)),"BOOT_NOT_VERIFIED");
        System.out.println("PassiveSleepTrialTest passed: "+count+" assertions");
    }
}
