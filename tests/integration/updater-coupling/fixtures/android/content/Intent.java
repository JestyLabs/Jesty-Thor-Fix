package android.content;
import android.os.Parcelable;
import android.os.Parcel;
import java.util.HashMap;
public class Intent implements Parcelable {
    public static final String EXTRA_INTENT = "android.intent.extra.INTENT";
    public static final int FLAG_ACTIVITY_NEW_TASK=0x10000000;
    private String action;
    private int flags;
    private final HashMap<String,Object> extras=new HashMap<>();
    public Intent() { }
    public Intent(Context context, Class<?> component) { }
    public Intent setAction(String value) { action=value; return this; }
    public String getAction() { return action; }
    public Intent putExtra(String key,int value) { extras.put(key,value); return this; }
    public Intent putExtra(String key,Parcelable value) { extras.put(key,value); return this; }
    public Intent putExtra(String key,String value) { extras.put(key,value); return this; }
    public int getIntExtra(String key,int fallback) { Object v=extras.get(key);return v instanceof Integer?(Integer)v:fallback; }
    public String getStringExtra(String key) { return (String)extras.get(key); }
    public Parcelable getParcelableExtra(String key) { return (Parcelable)extras.get(key); }
    public Intent addFlags(int value) { flags|=value; return this; }
    public int getFlags() { return flags; }
    public int describeContents() { return 0; }
    public void writeToParcel(Parcel parcel,int value) { }
}
