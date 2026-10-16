package com.thor.displaypowertest;
public final class SocketClient {public static String request(char c,int timeout) throws Exception {
 if(c=='I') {FixtureState.probes++;if(FixtureState.disappearAfter>0 &&
 FixtureState.probes>FixtureState.disappearAfter) {FixtureState.version=null;FixtureState.disappearAfter=0;}}
 if(FixtureState.version==null)throw new Exception("fixture absent");
 return c=='Q'?FixtureState.snapshot():FixtureState.health();}}
