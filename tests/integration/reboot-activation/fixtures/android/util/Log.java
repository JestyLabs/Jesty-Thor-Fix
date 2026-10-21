package android.util;
public final class Log { public static int d(String t,String m){return 0;}
 public static int w(String t,String m){return 0;}
 public static int w(String t,String m,Throwable x){return w(t,m);}
 public static int e(String t,String m){com.thor.displaypowertest.FixtureState.errors.add(m);return 0;}
 public static int e(String t,String m,Throwable x){return e(t,m);}}
