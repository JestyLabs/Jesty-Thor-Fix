package com.thor.displaypowertest;
public final class SecureChannel {public static final int PROTOCOL=2;
 // An existing directory stands in for a present socket inode.
 public static String path(){return FixtureState.socketPresent?"tests/integration/reboot-activation":"fixture-no-socket";}
 public static String stampedBootId(){return FixtureState.stampedBoot;}
 public static String currentBootId(){if(FixtureState.bootReadThrows)throw new SecurityException("fixture");return FixtureState.boot;}}
