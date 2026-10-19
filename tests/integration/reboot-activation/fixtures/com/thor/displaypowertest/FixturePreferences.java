package com.thor.displaypowertest;
import android.content.SharedPreferences;import java.util.*;
public final class FixturePreferences implements SharedPreferences {
 public static final FixturePreferences INSTANCE=new FixturePreferences();
 public boolean getBoolean(String k,boolean d){return k.equals("daemon_protocol_unix_v2")?true:d;}
 public int getInt(String k,int d){return d;}
 public String getString(String k,String d){if(FixtureState.corruptRead)throw new ClassCastException("fixture");return FixtureState.saved.containsKey(k)?FixtureState.saved.get(k):d;}
 public Editor edit(){return new Editor(){Map<String,String> staged=new HashMap<>();
 public Editor putBoolean(String k,boolean v){return this;}public Editor putString(String k,String v){staged.put(k,v);return this;}
 public boolean commit(){FixtureState.commits++;if(FixtureState.commitThrows)throw new IllegalStateException("fixture");
 if(FixtureState.commitFailure){if(FixtureState.commitUpdatesMemory)FixtureState.saved.putAll(staged);return false;}
 FixtureState.saved.putAll(staged);FixtureState.durable.putAll(staged);return true;}};}}
