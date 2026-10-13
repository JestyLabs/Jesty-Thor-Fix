package android.content;
import java.io.File;
import java.lang.reflect.Proxy;
import android.content.pm.PackageManager;
public class Context {
    private final File cache;
    private final PackageManager manager = new PackageManager();
    public Runnable cacheHook;
    public Context(File cache) { this.cache = cache; }
    public File getCacheDir() { if(cacheHook!=null) cacheHook.run(); return cache; }
    public PackageManager getPackageManager() { return manager; }
    public String getPackageName() { return "com.thor.displaypowertest"; }
    public boolean failResources;
    public Runnable resourcesHook;
    public android.content.res.Resources getResources() {
        if (resourcesHook!=null) resourcesHook.run();
        if (failResources) throw new IllegalStateException("HOST_RESOURCES_FAILURE");
        return new android.content.res.Resources();
    }
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
