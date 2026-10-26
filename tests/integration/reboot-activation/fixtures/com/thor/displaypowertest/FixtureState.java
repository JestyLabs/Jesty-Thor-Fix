package com.thor.displaypowertest;
import java.util.*;import java.util.concurrent.*;
public final class FixtureState {
 public static String version,phase,protocol,watcher,mode,top,bottom,pid,afterLaunchPhase,snapshotSuffix;
 public static int probes,kills,launches,disappearAfter,commits;public static boolean stopEffective,bridgeAccept,lastHeld;
 public static String appVersion,boot;public static long appCode,updateTime;
 public static boolean fromBoot,packageFailure,commitFailure,commitThrows,corruptRead,legacyPresent,bootReadThrows,commitUpdatesMemory;
 public static Map<String,String> saved,durable;public static CountDownLatch finished;public static List<String> errors;
 public static String[] scriptedReplies;public static int replyIndex;public static StringBuilder requests;
 public static String phaseMs,fix;public static Runnable beforeProbe,beforeCommit;
 public static List<String> events;public static int helperSubmissions;public static boolean liveCommitFailure;
 public static void reset(String v){version=v;phase="READY";protocol="2";watcher="RUNNING";mode="0";top="1";bottom="1";
 pid="12000";afterLaunchPhase="READY";probes=kills=launches=disappearAfter=commits=0;stopEffective=bridgeAccept=true;lastHeld=false;
 appVersion=DaemonIdentity.RUNTIME_ID;appCode=86;updateTime=123;boot="00000000-0000-0000-0000-000000000001";
 fromBoot=packageFailure=commitFailure=commitThrows=corruptRead=legacyPresent=bootReadThrows=commitUpdatesMemory=false;
 saved=new HashMap<>();durable=new HashMap<>();
 scriptedReplies=null;replyIndex=0;requests=new StringBuilder();helperSubmissions=0;liveCommitFailure=false;snapshotSuffix="";
 phaseMs="90000";fix="1";beforeProbe=beforeCommit=null;
 events=Collections.synchronizedList(new ArrayList<String>());RetirementNative.reset();clearRecoveryProcessCache();
 finished=new CountDownLatch(1);errors=new ArrayList<>();}
 public static void clearRecoveryProcessCache(){try{java.lang.reflect.Field field=RebootActivation.class.getDeclaredField("unconfirmedRecoveryBoot");field.setAccessible(true);field.set(null,null);}catch(Exception error){throw new AssertionError(error);}}
 public static String health(){return "ok=1;protocol="+protocol+";version="+version+";pid="+pid+";boot_phase="+phase+";phase_ms="+phaseMs+";fix="+fix+";watcher="+watcher;}
 // Published code76 did not emit Q.runtime_version; never invent identity evidence.
 public static String snapshot(){return "ok=1"+("1.7.1".equals(version) ? "" : ";runtime_version=" + version)+";mode="+mode+";top_crtc="+top+";bottom_crtc="+bottom+";display_actions_held=0;display_effective=CONFIRMED;display_action_status=CONFIRMED;display_generation=1;repair_result=NONE;fix=1;cpu_fix_desired=0;cpu_fix_phase=CONFIRMED;system_load_fix=0;lid_guard=0;lid=open;external_display=unknown"+snapshotSuffix;}
 public static boolean transact(String cmd){
 if(cmd.contains(" app_process / R ")){helperSubmissions++;events.add("BRIDGE_SUBMIT");
 if(!bridgeAccept)return false;events.add("BRIDGE_ACK");RetirementNative.submit(cmd);return true;}
 if(cmd.startsWith("P=")){kills++;if(!bridgeAccept)return false;if(stopEffective)version=null;return true;}
 if(cmd.equals("fixture-launch")){events.add("LAUNCH");launches++;version=DaemonIdentity.RUNTIME_ID;phase=afterLaunchPhase;return true;}throw new AssertionError("unexpected command");}}
