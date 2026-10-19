#!/usr/bin/env python3
"""JDK17 compilation ONLY of owner drivers, real producers/server/controllers.

Reuses existing inert Android interfaces and adds compile-only missing SDK
shapes. Four UI construction/settings types are inert here; the actual renderer
and the complete retirement route/JNI declarations/R entry are NOT substituted.
The materialized Android source set still contains the actual UI classes.
No fixture main, Android/native method, socket, Binder or root process is run.
"""
from pathlib import Path
import hashlib
import importlib.util
import re
import shutil
import subprocess
import uuid


HERE = Path(__file__).resolve().parent
WORK = HERE.parents[2]
BUNDLE = WORK.parent
PACKAGE = Path("org/example/rfixture")
UI_SHAPES = {"DashboardLayout.java", "DashboardViews.java",
             "DashboardSettingsController.java", "DashboardStyle.java"}


def redact(text):
    text = text.replace(str(BUNDLE), "[BUNDLE_ROOT]")
    text = re.sub(r"\.owner-retirement-host-[0-9a-f]{32}", "[HOST_TEMP]", text)
    return re.sub(r"(?:/Users/|/home/|[A-Za-z]:[\\/]Users[\\/])[^\s'\";]+",
                  "[HOST_TEMP]", text)


def append_member(source, member):
    position = source.rfind("}")
    return source[:position] + member + source[position:]


def write(root, name, source):
    target = root / name
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(source.replace("com.thor.displaypowertest", "org.example.rfixture"),
                      encoding="utf-8")


def interfaces(root):
    secure = WORK / "tests/fixtures/securechannel"
    activation = WORK / "tests/integration/reboot-activation/fixtures"
    for path in sorted((secure / "android").rglob("*.java")):
        source = path.read_text()
        if path.name == "Credentials.java":
            source = append_member(source, " public int getPid(){throw new AssertionError();} ")
        if path.name == "LocalServerSocket.java":
            source = append_member(source,
                    " public LocalSocket accept() throws java.io.IOException {throw new AssertionError();} ")
        if path.name == "LocalSocket.java":
            source = source.replace("void close() {", "void close() throws IOException {")
        write(root, path.relative_to(secure), source)
    for name in ("android/graphics/Color.java", "android/os/Build.java",
                 "android/os/IBinder.java", "android/os/Parcel.java",
                 "android/view/View.java", "android/widget/TextView.java",
                 "android/content/SharedPreferences.java"):
        source = (activation / name).read_text()
        if name.endswith("/TextView.java"):
            source = append_member(source, " public CharSequence getText(){return text;} ")
        if name.endswith("/SharedPreferences.java"):
            source = source.replace("Editor edit();", "Editor edit(); java.util.Map<String,?> getAll();")
        write(root, Path(name), source)
    for name in ("DashboardLayout", "DashboardSettingsController", "DashboardStyle"):
        source = (activation / "com/thor/displaypowertest" / (name + ".java")).read_text()
        if name == "DashboardLayout":
            source = append_member(source, """
 interface TopActions {void onUpdate();void onSupport();void onGithub();
 void onGithubLongPress();void onDiagnostics();}
 final android.view.View root=new android.view.View();
 DashboardLayout(DashboardViews views,TopActions actions){}
""")
        if name == "DashboardSettingsController":
            source = append_member(source, """
 DashboardSettingsController(android.app.Activity activity,
 android.content.SharedPreferences preferences,DashboardLayout layout,Feedback feedback){}
 void bind(){} void shutdown(){}
""")
        write(root, PACKAGE / (name + ".java"), source)
    additions = {
        "android/content/Context.java": """
package android.content;
public class Context {
 public static final int MODE_PRIVATE=0;
 public android.content.pm.PackageManager getPackageManager(){throw new AssertionError();}
 public String getPackageName(){throw new AssertionError();}
 public java.io.File getFilesDir(){throw new AssertionError();}
 public SharedPreferences getSharedPreferences(String name,int mode){throw new AssertionError();}
 public ComponentName startService(Intent intent){throw new AssertionError();}
}""",
        "android/content/ComponentName.java": "package android.content; public final class ComponentName {}",
        "android/content/Intent.java": """
package android.content;
public class Intent {
 public static final int FLAG_ACTIVITY_NEW_TASK=0x10000000;
 public Intent(){} public Intent(Context context,Class<?> type){}
 public Intent addFlags(int flags){throw new AssertionError();}
 public long getLongExtra(String key,long fallback){throw new AssertionError();}
 public boolean getBooleanExtra(String key,boolean fallback){throw new AssertionError();}
}""",
        "android/content/pm/PackageInfo.java": """
package android.content.pm;
public class PackageInfo {
 public String versionName; public int versionCode; public long lastUpdateTime;
 public long getLongVersionCode(){throw new AssertionError();}
}""",
        "android/content/pm/PackageManager.java": """
package android.content.pm;
public class PackageManager {
 public PackageInfo getPackageInfo(String name,int flags) throws Exception {throw new AssertionError();}
}""",
        "android/app/Activity.java": """
package android.app;
public class Activity extends android.content.Context {
 public static final int RESULT_OK=-1, RESULT_CANCELED=0;
 public void onCreate(android.os.Bundle state){} protected void onDestroy(){}
 public void setContentView(android.view.View root){throw new AssertionError();}
 public void finish(){throw new AssertionError();}
}""",
        "android/app/Instrumentation.java": """
package android.app;
public class Instrumentation {
 public void onCreate(android.os.Bundle args){} public void onStart(){}
 public void start(){throw new AssertionError();}
 public android.content.Context getTargetContext(){throw new AssertionError();}
 public Activity startActivitySync(android.content.Intent intent){throw new AssertionError();}
 public void runOnMainSync(Runnable action){throw new AssertionError();}
 public void finish(int result,android.os.Bundle data){throw new AssertionError();}
}""",
        "android/app/Service.java": """
package android.app;
public class Service extends android.content.Context {
 public static final int START_NOT_STICKY=2;
 public void onCreate(){} public android.os.IBinder onBind(android.content.Intent intent){return null;}
 public int onStartCommand(android.content.Intent intent,int flags,int id){throw new AssertionError();}
 public void stopSelf(){throw new AssertionError();}
}""",
        "android/os/Bundle.java": """
package android.os;
public class Bundle {
 public String getString(String key){throw new AssertionError();}
 public String getString(String key,String fallback){throw new AssertionError();}
 public void putString(String key,String value){throw new AssertionError();}
}""",
        "android/os/Process.java": """
package android.os;
public final class Process {
 public static int myUid(){throw new AssertionError();}
 public static int myPid(){throw new AssertionError();}
}""",
        "android/os/SystemClock.java": """
package android.os;
public final class SystemClock {
 public static long elapsedRealtime(){throw new AssertionError();}
 public static void sleep(long ms){throw new AssertionError();}
}""",
        "android/util/Log.java": """
package android.util;
public final class Log {
 public static int d(String tag,String message){throw new AssertionError();}
 public static int w(String tag,String message){throw new AssertionError();}
 public static int w(String tag,String message,Throwable error){throw new AssertionError();}
 public static int e(String tag,String message){throw new AssertionError();}
 public static int e(String tag,String message,Throwable error){throw new AssertionError();}
}""",
        "org/example/rfixture/DashboardViews.java": """
package org.example.rfixture;
final class DashboardViews { DashboardViews(android.app.Activity activity){} }""",
    }
    for name, source in additions.items():
        write(root, Path(name), source)


def main():
    if not shutil.which("javac"):
        print("NOT RUN: existing JDK17 unavailable; no installation attempted")
        return 2
    spec = importlib.util.spec_from_file_location("owner_materializer", HERE / "materialize.py")
    materializer = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(materializer)
    scratch = WORK / (".owner-retirement-host-" + uuid.uuid4().hex)
    scratch.mkdir()

    def sanitize(text):
        return redact(text.replace(str(scratch), "[HOST_TEMP]"))

    def execute(command):
        print("COMMAND " + sanitize(" ".join(str(part) for part in command)), flush=True)
        result = subprocess.run(command, text=True, stdout=subprocess.PIPE,
                                stderr=subprocess.STDOUT, timeout=120)
        print(sanitize(result.stdout), end="" if result.stdout.endswith("\n") else "\n", flush=True)
        print("EXIT " + str(result.returncode), flush=True)
        if result.returncode:
            raise RuntimeError("SOURCE COMPILATION FAILED (not a negative control)")
        return result.stdout

    try:
        version = execute(["javac", "-version"])
        if not version.startswith("javac 17."):
            print("NOT RUN: existing JDK17 required")
            return 2
        stub_directory = scratch / "inert-interfaces"
        interfaces(stub_directory)
        stubs = sorted(stub_directory.rglob("*.java"))
        for version in ("1.7.1", "1.7.2", "1.7.3"):
            target = scratch / ("sources-" + version)
            provenance = materializer.materialize(version, target)
            outputs = [entry["output"] for entry in provenance["files"]]
            if len(outputs) != len(set(outputs)):
                raise RuntimeError("Duplicate materialized output paths")
            for kind in (("predecessor", "candidate") if version == "1.7.1" else ("predecessor",)):
                product_sources = sorted((target / kind / "src").rglob("*.java"))
                if kind == "candidate":
                    product_sources = [p for p in product_sources if p.name not in UI_SHAPES]
                selected_stubs = stubs if kind == "candidate" else [
                    p for p in stubs if p.relative_to(stub_directory).parts[0] == "android"]
                sources = [*selected_stubs, *product_sources]
                argfile = scratch / (kind + "-" + version + ".args")
                argfile.write_text("".join('"' + p.as_posix() + '"\n'
                                           for p in sources), encoding="utf-8")
                execute(["javac", "-encoding", "UTF-8", "-source", "8", "-target", "8",
                         "-proc:none", "-d", str(scratch / (kind + "-" + version)),
                         "@" + str(argfile)])
                digest = hashlib.sha256()
                for source in sources:
                    digest.update(source.relative_to(scratch).as_posix().encode("utf-8") + b"\0")
                    digest.update(source.read_bytes())
                label = "predecessor " + version if kind == "predecessor" else (
                    "candidate paired_predecessor=" + version)
                print("PASS COMPILE_ONLY " + label + " default D; "
                      + "source_set_sha256=" + digest.hexdigest(), flush=True)
    except (RuntimeError, OSError, subprocess.TimeoutExpired) as error:
        print("FAIL: " + sanitize(str(error)), flush=True)
        return 1
    finally:
        shutil.rmtree(scratch)
    print("PASS: actual owner drivers/D/R, server/producers and complete Java retirement route "
          "compile against inert Android/UI interfaces; no class executed; compilation files removed")
    print("NOT RUN: actual Android UI/SDK build, APK, native compilation/loading, Binder/root/"
          "proc guard, signal/exit, READY, device")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, RuntimeError) as error:
        print("FAIL: " + redact(str(error)))
        raise SystemExit(1)
