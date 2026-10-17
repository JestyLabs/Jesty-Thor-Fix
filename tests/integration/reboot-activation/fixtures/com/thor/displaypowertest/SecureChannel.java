package com.thor.displaypowertest;
public final class SecureChannel {public static final int PROTOCOL=2;
 public static String path(){return "fixture-no-socket";}public static String stampedBootId(){return "";}
 public static String currentBootId(){if(FixtureState.bootReadThrows)throw new SecurityException("fixture");return FixtureState.boot;}}
