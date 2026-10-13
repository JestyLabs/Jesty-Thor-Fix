package android.util;
public class Log {
    public static final java.util.List<Throwable> errors=new java.util.ArrayList<>();
    public static final java.util.List<String> messages=new java.util.ArrayList<>();
    public static int w(String tag,String message) { messages.add(message); return 0; }
    public static int w(String tag,String message,Throwable error) {messages.add(message);errors.add(error);return 0;}
    public static int e(String tag,String message,Throwable error) {return w(tag,message,error);}
    public static int d(String tag,String message) {return 0;}
    public static void reset() {errors.clear();messages.clear();}
}
