package android.os;
public final class ServiceManager { public static IBinder getService(String name){
 return (code,a,b,flags) -> com.thor.displaypowertest.FixtureState.transact(a.values[0]); } }
