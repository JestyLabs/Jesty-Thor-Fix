package com.thor.displaypowertest;
import java.util.*;

/** Actual renderer and helper, simulated widgets/settings; no visual Android acceptance. */
public final class RebootDashboardHostTest {
    private static int checks;
    private static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
    private static Map<String,String> sample(){
        Map<String,String> q=new HashMap<>();q.put("runtime_version",DaemonIdentity.RUNTIME_ID);
        q.put("boot_phase","READY");q.put("display_actions_held","0");q.put("watcher_health","RUNNING");
        q.put("fix","1");q.put("display_effective","CONFIRMED");q.put("mode","0");q.put("top_crtc","1");q.put("bottom_crtc","1");return q;
    }
    private static DashboardRenderer renderer(DashboardLayout v,int[] requests){
        return new DashboardRenderer(v,new DashboardRenderer.Host(){
            public void onDisplayVisual(DashboardStateModel.Visual visual){}
            public void requestDaemonReplacement(){requests[0]++;}},
            RebootActivation.ticket(new AutoService(),FixturePreferences.INSTANCE));
    }
    public static void main(String[] args){
        FixtureState.reset("1.7.2");DashboardLayout views=new DashboardLayout();
        DashboardSettingsController settings=new DashboardSettingsController();int[] requests={0};
        DashboardRenderer ui=renderer(views,requests);ui.render(sample(),settings);
        check(views.stateText.text.equals("SERVICE TRANSFER PENDING")&&settings.pending,"current telemetry cannot bypass same-boot baseline");
        check(views.stateDetail.text.contains("contact Jesty"),"successful baseline preparation visible");
        check(views.stateDetail.text.equals("Restart the Thor once to finish updating the background service. "
                + "Still pending after that? Save diagnostics and contact Jesty."),
                "ordinary update guidance matches the bounded restart path");
        Map<String,String> beforeBoot=sample();beforeBoot.put("runtime_version","1.7.1");ui.render(beforeBoot,settings);
        check(views.stateText.text.equals("SERVICE TRANSFER PENDING")&&settings.pending,"old runtime cannot skip the first required reboot");
        check(views.displayValue.text.equals("ACTIVATION PENDING"),"prepared baseline is not yet a post-boot mismatch");
        FixtureState.boot="00000000-0000-0000-0000-000000000002";ui=renderer(views,requests);ui.render(sample(),settings);
        check(views.stateText.text.equals("ACTIVE")&&!settings.pending,"new boot and current READY can be active without broadcast");
        for(String old:new String[]{"1.7.1","1.7.2-rc1","1.7.2-recovery","unknown"}){
            Map<String,String> q=sample();q.put("runtime_version",old);ui.render(q,settings);
            check(views.stateText.text.equals("ACTIVATION BLOCKED")&&settings.pending,"old identity blocks activation after verified reboot");
            check(views.displayValue.text.equals("SERVICE IDENTITY MISMATCH"),"post-boot mismatch is explicit");
            check(views.stateDetail.text.contains("already been verified")
                    &&views.stateDetail.text.contains("Do not keep restarting"),"verified boot does not request another reboot");
            check(views.stateDetail.text.contains("Save diagnostics")
                    &&!views.stateDetail.text.contains("external recovery"),"support guidance does not promise an available recovery artifact");
        }
        check(requests[0]==0,"incompatible identity never requests live replacement");
        Map<String,String> q=sample();q.remove("runtime_version");ui.render(q,settings);
        check(views.stateText.text.equals("ACTIVATION BLOCKED")&&settings.pending,"missing runtime identity after reboot is blocked");
        ui.renderUnavailable(false);check(views.stateText.text.equals("ACTIVATION BLOCKED"),"timeout preserves known mismatch");
        check(views.stateDetail.text.contains("Do not keep restarting"),"timeout does not reintroduce reboot loop guidance");
        ui.renderUnavailable(true);check(views.stateText.text.equals("ACTIVATION BLOCKED"),"in-flight timeout cannot mask known mismatch");
        q=sample();q.put("runtime_version","1.7.1");settings.inFlight=true;ui.render(q,settings);
        check(views.stateText.text.equals("ACTIVATION BLOCKED")&&settings.pending,"command cannot mask mismatch");
        settings.inFlight=false;ui.render(sample(),settings);
        check(views.stateText.text.equals("ACTIVE")&&!settings.pending,"current READY clears the prior identity block");
        check(views.stateDetail.visibility==android.view.View.GONE,"resolved mismatch guidance is hidden");
        settings.inFlight=false;q=sample();q.put("boot_phase","WAITING_FOR_ANDROID");q.put("display_actions_held","1");ui.render(q,settings);
        check(views.stateText.text.equals("ACTIVATION PENDING")&&settings.pending,"STARTING not ACTIVE");
        q=sample();q.put("watcher_health","FAILED");ui.render(q,settings);
        check(!views.stateText.text.equals("ACTIVE")&&settings.pending,"failed watcher not ready");
        q=sample();q.put("boot_phase","HANDOFF_FAILED");q.put("display_actions_held","1");ui.render(q,settings);
        check(requests[0]==1&&settings.pending,"same-version held recovery retained");
        FixtureState.reset(DaemonIdentity.RUNTIME_ID);RebootActivation.ticket(new AutoService(),FixturePreferences.INSTANCE);
        FixtureState.boot="00000000-0000-0000-0000-000000000002";
        RebootActivation.Ticket recovery=RebootActivation.ticket(new AutoService(),FixturePreferences.INSTANCE);
        check(recovery.beginRecovery(),"prepare unconfirmed recovery fence");
        requests[0]=0;ui=renderer(views,requests);ui.render(sample(),settings);
        check(views.stateText.text.equals("SERVICE RESTART REQUIRED")&&settings.pending,
                "unconfirmed recovery has a distinct restart heading");
        check(views.stateDetail.text.contains("restart could not be confirmed")
                &&views.stateDetail.text.contains("Restart the Thor once"),
                "unconfirmed recovery explains the required action");
        check(!views.stateDetail.text.contains("updating")&&requests[0]==0,
                "recovery fence never promises an update or requests another helper");
        ui.renderUnavailable(false);
        check(views.stateText.text.equals("SERVICE RESTART REQUIRED"),"unavailable telemetry preserves recovery guidance");
        FixtureState.reset(null);FixtureState.commitFailure=true;ui=renderer(views,requests);ui.render(sample(),settings);
        check(views.stateText.text.equals("ACTIVATION BLOCKED")&&settings.pending,"storage failure clearly blocked");
        check(views.stateDetail.text.contains("Do not keep restarting")&&views.stateDetail.text.contains("external recovery"),"bounded storage recovery instructions");
        FixtureState.reset(null);FixtureState.boot="";ui=renderer(views,requests);ui.renderUnavailable(false);
        check(views.stateText.text.equals("ACTIVATION BLOCKED"),"missing boot ID not a reboot loop");
        check(views.stateDetail.text.contains("do not uninstall"),"preserve data in recovery guidance");
        System.out.println("RebootDashboardHostTest passed: "+checks+" assertions; widgets/settings simulated, no Android UI execution.");
    }
}
