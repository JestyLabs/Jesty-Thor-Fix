package android.app;import android.content.*;
public class Service extends Context {public static final int MODE_PRIVATE=0,START_NOT_STICKY=2;
 public void onCreate(){} public android.os.IBinder onBind(Intent i){return null;}
 public int onStartCommand(Intent i,int f,int s){return 2;}
 public java.io.File getFilesDir(){return new java.io.File("fixture-no-device-files");}
 public SharedPreferences getSharedPreferences(String n,int m){return com.thor.displaypowertest.FixturePreferences.INSTANCE;}
 public void stopSelf(){com.thor.displaypowertest.FixtureState.finished.countDown();}}
