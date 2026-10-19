package android.content;
import java.io.File;
import java.lang.reflect.Proxy;
import android.content.pm.PackageManager;
public class Context {
    private final File cache;
    private final PackageManager manager = new PackageManager();
    public Context(File cache) { this.cache = cache; }
    public File getCacheDir() { return cache; }
    public PackageManager getPackageManager() { return manager; }
    public String getPackageName() { return "com.thor.displaypowertest"; }
    public android.content.res.Resources getResources() { throw new IllegalStateException("HOST_UI_CHECKPOINT"); }
    public SharedPreferences getSharedPreferences(String name, int mode) {
        return (SharedPreferences) Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),
            new Class<?>[]{SharedPreferences.class}, (proxy, method, args) -> {
                if (method.getName().equals("getBoolean")) return false;
                if (method.getName().equals("getLong")) return 0L;
                if (method.getName().equals("getInt")) return 0;
                if (method.getName().equals("getFloat")) return 0f;
                return null;
            });
    }
}
