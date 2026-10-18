package com.thor.displaypowertest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Actual service/helper with adapters: absence of broadcasts is modeled, not Android delivery. */
public final class RebootActivationHostTest {
    private static int cases, checks;
    private static final String A="00000000-0000-0000-0000-000000000001";
    private static final String B="00000000-0000-0000-0000-000000000002";
    private static final String C="00000000-0000-0000-0000-000000000003";
    private static final String KEY="activation_v3_baseline";
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static RebootActivation.Ticket ticket(){return RebootActivation.ticket(new AutoService(),FixturePreferences.INSTANCE);}
    private static void prepareThenReboot(){
        check(!ticket().isAuthorized(),"preparation cannot authorize its own kernel boot");
        FixtureState.boot=B;check(ticket().isAuthorized(),"observed new boot authorizes without broadcast");
    }
    private static void attempt(){
        FixtureState.finished=new CountDownLatch(1);FixtureState.kills=FixtureState.launches=FixtureState.probes=0;
    }
    private static void run(String name,int kills,int launches,boolean authorized)throws Exception{
        attempt();AutoService service=new AutoService();service.onCreate();
        service.onStartCommand(new android.content.Intent(),0,1);
        check(FixtureState.finished.await(5,TimeUnit.SECONDS),name+": worker done");
        check(FixtureState.kills==kills,name+": stop submissions");
        check(FixtureState.launches==launches,name+": launches");
        check(ticket().isAuthorized()==authorized,name+": kernel evidence");
        cases++;System.out.println("PASS|"+name+"|kills="+kills+"|launches="+launches);
    }
    public static void main(String[] args)throws Exception{
        FixtureState.reset(null);run("first-open-prepares-only",0,0,false);
        check(FixtureState.probes>0,"only read-only predecessor probing precedes reboot evidence");
        check(FixtureState.saved.containsKey(KEY),"baseline persisted on first open");
        String prepared=FixtureState.saved.get(KEY);int commits=FixtureState.commits;
        run("repeated-open-does-not-rebase",0,0,false);
        check(prepared.equals(FixtureState.saved.get(KEY))&&commits==FixtureState.commits,"stable baseline, no repeated writes");
        FixtureState.fromBoot=true;run("same-kernel-boot-broadcast-rejected",0,0,false);
        FixtureState.boot=B;FixtureState.fromBoot=false;run("new-kernel-no-broadcast-foreground-start",0,1,true);
        check(FixtureState.lastHeld,"foreground fallback launches through boot hold");
        run("current-ready-app-reopen",0,0,true);
        FixtureState.boot=C;FixtureState.version=null;run("later-kernel-no-broadcast",0,1,true);
        check(prepared.equals(FixtureState.saved.get(KEY)),"verified boots do not replace original baseline");

        FixtureState.reset("1.7.3");run("current-version-without-proof-not-authorized",0,0,false);
        FixtureState.reset(null);FixtureState.fromBoot=true;run("first-observation-is-boot-not-authority",0,0,false);
        FixtureState.boot=B;run("second-boot-receiver-can-start",0,1,true);

        // No app execution/broadcast is modeled before this first manual open.
        FixtureState.reset(null);FixtureState.boot=B;run("never-opened-stopped-first-open-after-reboot",0,0,false);
        FixtureState.boot=C;run("clean-install-prepared-open-then-reboot",0,1,true);
        FixtureState.reset(null);ticket();FixtureState.fromBoot=false;
        run("force-stop-same-kernel-does-not-authorize",0,0,false);
        FixtureState.boot=B;run("force-stop-reboot-manual-open-no-broadcast",0,1,true);
        run("force-stop-after-activation-retains-evidence",0,0,true);
        FixtureState.saved.clear();run("data-clear-requires-new-preparation",0,0,false);
        FixtureState.boot=C;FixtureState.version=null;run("data-clear-recovery-open-then-reboot",0,1,true);

        for(String old:new String[]{"1.5.3","1.7.0","1.7.1-rc4","1.7.1","1.7.2-rc1","1.7.2-recovery","unknown"}){
            FixtureState.reset(old);prepareThenReboot();run("no-live-upgrade-"+old,0,0,true);
        }
        FixtureState.reset("1.7.2-rc1");ticket();FixtureState.fromBoot=true;
        run("same-kernel-broadcast-with-old-daemon",0,0,false);
        check(FixtureState.kills==0&&FixtureState.launches==0,"unverified broadcast cannot stop or launch a daemon");
        // Production AutoService and PServer, with simulated bridge, storage and socket.
        FixtureState.reset("1.7.2");run("code80-same-kernel-handover",1,1,true);
        check(FixtureState.saved.containsKey("activation_v3_live_handover"),
                "live proof is durable only after new runtime identity");
        run("live-proven-repeat-open-no-kill",0,0,true);
        FixtureState.reset("1.7.1");run("code76-missing-Q-version-requires-reboot",0,0,false);
        FixtureState.reset("1.7.2");FixtureState.mode="1";
        run("top-mode-fails-closed",0,0,false);
        FixtureState.mode="0";run("later-both-retry-activates",1,1,true);
        FixtureState.reset("1.7.2");FixtureState.bottom="0";
        run("dark-bottom-must-not-be-killed",0,0,false);
        FixtureState.reset("1.7.2");FixtureState.watcher="FAILED";
        run("unhealthy-predecessor-is-preserved",0,0,false);
        FixtureState.reset("1.7.2");FixtureState.phase="HANDOFF_FAILED";
        run("held-predecessor-not-killed",0,0,false);
        FixtureState.reset("1.7.2");FixtureState.stopEffective=false;
        run("bridge-kill-unconfirmed-no-second-daemon",1,0,false);
        FixtureState.reset("1.7.2");FixtureState.bridgeAccept=false;
        run("bridge-reject-no-second-daemon",1,0,false);
        FixtureState.reset("unknown");run("unrecognized-predecessor-not-killed",0,0,false);
        FixtureState.reset("1.7.2");FixtureState.commitFailure=true;
        run("failed-baseline-write-blocks-handover",0,0,false);
        FixtureState.reset(null);prepareThenReboot();FixtureState.appCode++;
        run("changed-code-invalidates-earlier-baseline",0,0,false);
        FixtureState.reset(null);prepareThenReboot();FixtureState.updateTime++;
        run("changed-install-stamp-invalidates-baseline",0,0,false);
        FixtureState.reset(null);FixtureState.saved.put("activation_boot_package","1.7.2:80:123");
        FixtureState.saved.put("activation_boot_id",A);FixtureState.boot=B;
        run("v2-receiver-marker-is-not-v3-baseline",0,0,false);
        FixtureState.reset(null);ticket();FixtureState.boot=B;
        FixtureState.saved.put(KEY,FixtureState.saved.get(KEY).replace(A,C));
        run("checksum-corruption-rebaselines-without-authorizing",0,0,false);
        FixtureState.reset(null);FixtureState.saved.put(KEY,"malformed");FixtureState.boot=B;
        run("malformed-record-needs-new-baseline",0,0,false);
        FixtureState.reset(null);FixtureState.boot="";FixtureState.fromBoot=true;
        run("missing-boot-evidence",0,0,false);
        check(ticket().pendingMessage().contains("Do not keep restarting"),"missing identity has bounded recovery guidance");
        FixtureState.boot=A;run("boot-evidence-returns-prepares-only",0,0,false);
        FixtureState.boot=B;run("boot-evidence-recovery-after-new-boot",0,1,true);
        FixtureState.reset(null);FixtureState.packageFailure=true;run("package-metadata-failure",0,0,false);
        FixtureState.reset(null);FixtureState.bootReadThrows=true;run("boot-read-security-failure",0,0,false);
        FixtureState.reset(null);FixtureState.appCode=0;run("invalid-version-code",0,0,false);
        FixtureState.reset(null);FixtureState.updateTime=0;run("missing-install-metadata",0,0,false);
        FixtureState.reset(null);FixtureState.appVersion="other";run("manifest-runtime-mismatch",0,0,false);
        FixtureState.reset(null);FixtureState.commitFailure=true;run("baseline-write-fails",0,0,false);
        check(ticket().pendingMessage().contains("Do not keep restarting"),"storage error does not suggest repeated reboots");
        FixtureState.commitFailure=false;run("storage-restored-reopen-prepares-only",0,0,false);
        FixtureState.boot=B;run("storage-recovery-after-new-boot",0,1,true);
        FixtureState.reset(null);FixtureState.commitThrows=true;run("baseline-write-throws",0,0,false);
        FixtureState.reset(null);FixtureState.commitFailure=true;FixtureState.commitUpdatesMemory=true;
        run("failed-commit-cached-in-memory-still-pending",0,0,false);
        check(ticket().evidence()==RebootActivation.Evidence.STORAGE_UNAVAILABLE,"cached failed commit is not prepared evidence");
        check(!FixtureState.saved.isEmpty()&&FixtureState.durable.isEmpty(),"fixture distinguishes cache from durable storage");
        FixtureState.commitFailure=false;FixtureState.commitUpdatesMemory=false;FixtureState.boot=B;
        FixtureState.saved=new java.util.HashMap<>(FixtureState.durable);
        run("failed-commit-not-durable-after-boot-reprepares",0,0,false);
        FixtureState.boot=C;FixtureState.saved=new java.util.HashMap<>(FixtureState.durable);
        run("durable-repreparation-then-new-boot",0,1,true);
        FixtureState.reset(null);FixtureState.commitFailure=true;FixtureState.commitUpdatesMemory=true;ticket();
        FixtureState.commitFailure=false;FixtureState.commitUpdatesMemory=false;
        run("cached-failed-write-retried-after-storage-restored",0,0,false);
        check(!FixtureState.durable.isEmpty(),"reopen confirms durable baseline rather than trusting failed cache");
        FixtureState.boot=B;FixtureState.saved=new java.util.HashMap<>(FixtureState.durable);
        run("retried-cache-write-then-new-boot",0,1,true);
        FixtureState.reset(null);FixtureState.corruptRead=true;run("baseline-read-throws",0,0,false);
        check(FixtureState.commits==0,"failed read never overwrites unknown baseline");
        FixtureState.reset(null);prepareThenReboot();FixtureState.commitFailure=true;
        run("already-persisted-proof-needs-no-new-write",0,1,true);
        FixtureState.reset(null);prepareThenReboot();FixtureState.legacyPresent=true;
        run("legacy-occupant-no-stop",0,0,true);
        FixtureState.reset("1.7.3");prepareThenReboot();FixtureState.phase="HANDOFF_FAILED";
        run("same-version-held-recovery-retained",1,1,true);
        check(FixtureState.lastHeld,"same-version recovery remains held");
        FixtureState.reset("1.7.3");prepareThenReboot();FixtureState.phase="HANDOFF_FAILED";FixtureState.stopEffective=false;
        run("same-version-stop-no-effect-no-duplicate",1,0,true);
        FixtureState.reset(null);prepareThenReboot();FixtureState.afterLaunchPhase="WAITING_FOR_ANDROID";
        run("starting-not-ready",0,1,true);
        check(FixtureState.phase.equals("WAITING_FOR_ANDROID"),"STARTING is not relabeled READY");
        FixtureState.reset(null);
        Thread first=new Thread(()->ticket());Thread second=new Thread(()->ticket());
        first.start();second.start();first.join();second.join();
        check(FixtureState.commits==1,"concurrent foreground/service preparation writes one baseline");
        run("concurrent-preparation-still-needs-reboot",0,0,false);
        System.out.println("RebootActivationHostTest passed: "+cases+" scenarios, "+checks
            +" assertions; Android stopped state, force-stop, boot IDs and delivery are modeled, not executed.");
    }
}
