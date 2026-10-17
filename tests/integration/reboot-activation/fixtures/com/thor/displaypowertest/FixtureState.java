package com.thor.displaypowertest;
import java.util.*;import java.util.concurrent.*;
public final class FixtureState {
 public static String version,phase,protocol,watcher,mode,top,bottom,pid,afterLaunchPhase;
 public static int probes,kills,launches,disappearAfter,commits;public static boolean stopEffective,bridgeAccept,lastHeld;
 public static String appVersion,boot;public static long appCode,updateTime;
 public static boolean fromBoot,packageFailure,commitFailure,commitThrows,corruptRead,legacyPresent,bootReadThrows,commitUpdatesMemory;
 public static Map<String,String> saved,durable;public static CountDownLatch finished;public static List<String> errors;
 public static void reset(String v){version=v;phase="READY";protocol="2";watcher="RUNNING";mode="0";top="1";bottom="1";
 pid="12000";afterLaunchPhase="READY";probes=kills=launches=disappearAfter=commits=0;stopEffective=bridgeAccept=true;lastHeld=false;
 appVersion="1.7.3";appCode=83;updateTime=123;boot="00000000-0000-0000-0000-000000000001";
 fromBoot=packageFailure=commitFailure=commitThrows=corruptRead=legacyPresent=bootReadThrows=commitUpdatesMemory=false;
 saved=new HashMap<>();durable=new HashMap<>();
 finished=new CountDownLatch(1);errors=new ArrayList<>();}
 public static String health(){return "ok=1;protocol="+protocol+";version="+version+";pid="+pid+";boot_phase="+phase+";fix=1;watcher="+watcher;}
 public static String snapshot(){return "ok=1;mode="+mode+";top_crtc="+top+";bottom_crtc="+bottom+";display_actions_held=0;display_effective=CONFIRMED;fix=1;cpu_fix_desired=0;cpu_fix_phase=CONFIRMED;system_load_fix=0;lid_guard=0;lid=open;external_display=unknown";}
 public static boolean transact(String cmd){if(cmd.startsWith("P=")){kills++;if(!bridgeAccept)return false;if(stopEffective)version=null;return true;}
 if(cmd.equals("fixture-launch")){launches++;version="1.7.3";phase=afterLaunchPhase;return true;}throw new AssertionError("unexpected command");}}
