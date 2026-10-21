package com.thor.displaypowertest;
public final class SocketClient {public static String request(char c,int timeout) throws Exception {
 if(c=='I'&&FixtureState.beforeProbe!=null){Runnable hook=FixtureState.beforeProbe;FixtureState.beforeProbe=null;hook.run();}
 if(FixtureState.scriptedReplies!=null){FixtureState.requests.append(c);
 int at=FixtureState.replyIndex++;
 if(at>=FixtureState.scriptedReplies.length)throw new AssertionError("unexpected scripted request");
 String reply=FixtureState.scriptedReplies[at];
 if(reply==null)throw new Exception("synthetic endpoint unavailable");return reply;}
 if(c=='I') {FixtureState.probes++;if(FixtureState.disappearAfter>0 &&
 FixtureState.probes>FixtureState.disappearAfter) {FixtureState.version=null;FixtureState.disappearAfter=0;}}
 if(FixtureState.version==null)throw new Exception("fixture absent");
 return c=='Q'?FixtureState.snapshot():FixtureState.health();}}
