package android.os;
public final class Parcel { public String[] values;
 public static Parcel obtain(){return new Parcel();}
 public void writeStringArray(String[] a){values=a;} public void recycle(){} }
