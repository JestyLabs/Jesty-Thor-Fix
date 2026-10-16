package android.content;public class Intent {
 public long getLongExtra(String k,long d){return d;}
 public boolean getBooleanExtra(String k,boolean d){return "from_boot".equals(k)?com.thor.displaypowertest.FixtureState.fromBoot:d;}}
