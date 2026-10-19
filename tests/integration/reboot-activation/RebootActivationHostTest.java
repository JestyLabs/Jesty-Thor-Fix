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
        RetirementNative.awaitHelper();
        check(FixtureState.kills==kills,name+": stop submissions");
        check(FixtureState.launches==launches,name+": launches");
        check(ticket().isAuthorized()==authorized,name+": kernel evidence");
        cases++;System.out.println("PASS|"+name+"|kills="+kills+"|launches="+launches);
    }
    public static void main(String[] args)throws Exception{
        FixtureState.reset(null);run("first-open-prepares-only",0,0,false);
        check(FixtureState.helperSubmissions>0&&RetirementNative.signalCalls==0,
                "only bounded predecessor preparation precedes reboot evidence");
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

        FixtureState.reset("1.7.4");run("current-version-without-proof-not-authorized",0,0,false);
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
            FixtureState.reset(old);prepareThenReboot();run("post-reboot-old-runtime-not-activated-"+old,0,0,true);
        }
        FixtureState.reset("1.7.2-rc1");ticket();FixtureState.fromBoot=true;
        run("same-kernel-broadcast-with-old-daemon",0,0,false);
        check(FixtureState.kills==0&&FixtureState.launches==0,"unverified broadcast cannot stop or launch a daemon");
        // Real service/PServer/app+root retirement; native effects and historical
        // replies are synthetic, not published-producer or Android proof.
        FixtureState.reset("1.7.2");run("code80-same-kernel-handover",1,1,true);
        verifiedExitBeforeLaunch("code80");
        FixtureState.reset("1.7.3");run("code83-same-kernel-handover",1,1,true);
        verifiedExitBeforeLaunch("code83");
        check(FixtureState.saved.containsKey("activation_v3_live_handover"),
                "live proof is durable only after new runtime identity");
        run("live-proven-repeat-open-no-kill",0,0,true);
        FixtureState.reset("1.7.1");run("code76-bound-native-handover",1,1,true);
        verifiedExitBeforeLaunch("code76");
        FixtureState.reset("1.7.1");FixtureState.snapshotSuffix=";runtime_version=";
        run("code76-present-empty-Q-version-is-not-absence",0,0,false);
        for(String old:new String[]{"1.7.1","1.7.2","1.7.3"}){
            FixtureState.reset(old);RetirementNative.available=false;
            run("native-unavailable-reboot-fallback-"+old,0,0,false);
            check(FixtureState.helperSubmissions==0,"unsupported native never submits helper");
            check(ticket().evidence()==RebootActivation.Evidence.RESTART_REQUIRED,
                    "unsupported support retains normal reboot fallback");
        }
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
        run("descriptor-signal-no-effect-no-second-daemon",1,0,false);
        check(!FixtureState.events.contains("EXIT_CONFIRMED"),"signal submission alone is not exit");
        FixtureState.reset("1.7.2");RetirementNative.bridgeAckOnly=true;
        run("bridge-ACK-without-helper-not-exit",0,0,false);
        check(FixtureState.helperSubmissions==1,"bridge ACK was actually submitted");
        FixtureState.reset("1.7.2");FixtureState.bridgeAccept=false;
        run("bridge-reject-no-second-daemon",0,0,false);
        for(String fault:new String[]{"stale","malformed","missing","oversized"}){
            FixtureState.reset("1.7.2");RetirementNative.receiptFault=fault;
            run("receipt-"+fault+"-no-permission",1,0,false);
            check(FixtureState.events.contains("EXIT_CONFIRMED"),"receipt fault follows modelled exit");
        }
        FixtureState.reset("1.7.2");RetirementNative.processCloseFailure=true;
        run("process-descriptor-close-failure-no-receipt-or-launch",1,0,false);
        check(!FixtureState.events.contains("EXIT_RECEIPT"),"failed descriptor close cannot issue receipt");
        FixtureState.reset("1.7.2");RetirementNative.cancelBeforeCommit=true;
        run("cancel-before-commit-no-stop-or-launch",0,0,false);
        check(!FixtureState.events.contains("COMMIT"),"cancel before commit never authorizes signal");
        FixtureState.reset("1.7.2");RetirementNative.cancelAfterCommit=true;
        run("cancel-after-exit-no-launch-or-permission",1,0,false);
        FixtureState.reset("1.7.2");RetirementNative.changedPeerAt=2;
        run("changed-peer-no-stop-or-launch",0,0,false);
        FixtureState.reset("1.7.2");RetirementNative.daemonUid=11000;
        run("nonroot-old-peer-no-stop-or-launch",0,0,false);
        FixtureState.reset("1.7.2");RetirementNative.helperUid=11000;
        run("nonroot-helper-no-stop-or-launch",0,0,false);
        FixtureState.reset("1.7.2");RetirementNative.appUid=11001;
        run("wrong-app-peer-no-stop-or-launch",0,0,false);
        FixtureState.reset("unknown");run("unrecognized-predecessor-not-killed",0,0,false);
        FixtureState.reset("1.7.2");FixtureState.commitFailure=true;
        run("failed-baseline-write-blocks-handover",0,0,false);
        FixtureState.reset("1.7.2");FixtureState.liveCommitFailure=true;
        run("failed-durable-live-marker-remains-conservative",1,1,false);
        check(!FixtureState.durable.containsKey("activation_v3_live_handover"),
                "failed live marker is not durable permission");
        check(!dashboardState().equals("ACTIVE"),"failed live marker never makes dashboard ACTIVE");
        FixtureState.reset("1.7.1");FixtureState.afterLaunchPhase="WAITING_FOR_ANDROID";
        run("live-starting-permission-is-not-ready",1,1,true);
        verifiedExitBeforeLaunch("code76-starting");
        check(FixtureState.durable.containsKey("activation_v3_live_handover"),
                "authenticated STARTING may persist activation permission");
        check(dashboardState().equals("ACTIVATION PENDING"),"live STARTING dashboard not ACTIVE");
        FixtureState.phase="READY";
        check(PServer.probe(true)==DaemonLaunchModel.Probe.HEALTHY,
                "eventual READY traverses authenticated host transport boundary");
        check(dashboardState().equals("ACTIVE"),"eventual same-identity READY dashboard ACTIVE");
        FixtureState.version="1.7.1";
        check(!dashboardState().equals("ACTIVE"),"old identity never ACTIVE despite saved live permission");
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
        FixtureState.reset("1.7.4");prepareThenReboot();FixtureState.phase="HANDOFF_FAILED";
        run("same-version-held-recovery-retained",1,1,true);
        check(FixtureState.lastHeld,"same-version recovery remains held");
        FixtureState.reset("1.7.4");prepareThenReboot();FixtureState.phase="HANDOFF_FAILED";FixtureState.stopEffective=false;
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
    private static void verifiedExitBeforeLaunch(String id){
        java.util.List<String> events=FixtureState.events;
        check(events.indexOf("BIND_PROCESS")<events.indexOf("REQUEST_I"),id+": bind before I");
        check(events.indexOf("COMMIT")>events.indexOf("REQUEST_Q"),id+": requests before commit");
        check(events.indexOf("EXIT_CONFIRMED")>events.indexOf("SIGNAL"),id+": confirmed exit after signal");
        check(events.indexOf("EXIT_RECEIPT")>events.indexOf("EXIT_CONFIRMED"),id+": receipt after exit");
        check(events.indexOf("PROCESS_DESCRIPTOR_CLOSED")>events.indexOf("EXIT_CONFIRMED")
                &&events.indexOf("EXIT_RECEIPT")>events.indexOf("PROCESS_DESCRIPTOR_CLOSED"),
                id+": exited process descriptor closed before receipt");
        check(events.indexOf("LAUNCH")>events.indexOf("EXIT_RECEIPT"),id+": exit before successor launch");
        check(java.util.Collections.frequency(events,"REQUEST_I")==2
                &&java.util.Collections.frequency(events,"REQUEST_Q")==2,
                id+": both retained IQ rounds actually consumed");
        check(RetirementNative.bindCalls==1&&RetirementNative.transfers==4,
                id+": one retained descriptor and four app-admitted sockets");
        check(FixtureState.lastHeld,id+": successor launched held");
    }
    private static String dashboardState()throws Exception{
        String reply=SocketClient.request('Q',2500);
        java.util.Map<String,String> values=new java.util.HashMap<>();
        for(String field:reply.split(";")){
            int at=field.indexOf('=');if(at>0)values.put(field.substring(0,at),field.substring(at+1));
        }
        values.put("boot_phase",FixtureState.phase);values.put("watcher_health",FixtureState.watcher);
        values.put("display_actions_held","READY".equals(FixtureState.phase)?"0":"1");
        DashboardLayout layout=new DashboardLayout();
        DashboardRenderer renderer=new DashboardRenderer(layout,new DashboardRenderer.Host(){
            public void onDisplayVisual(DashboardStateModel.Visual visual){}
            public void requestDaemonReplacement(){throw new AssertionError("unexpected dashboard replacement");}
        },ticket());
        renderer.render(values,new DashboardSettingsController());
        return layout.stateText.text;
    }
}
