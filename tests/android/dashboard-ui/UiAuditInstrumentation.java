package org.jestylabs.thorfix.uiaudit;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;
import android.widget.ScrollView;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Disposable instrumentation of the real dashboard; never activate a hardware control. */
public final class UiAuditInstrumentation extends Instrumentation {
    private String prefix;
    private File output;
    private Activity activity;
    private final JSONArray captures = new JSONArray();
    private final JSONArray navigation = new JSONArray();
    private final List<View> views = new ArrayList<>();
    @Override public void onCreate(Bundle args) {
        super.onCreate(args); prefix = args.getString("prefix", "audit"); start();
    }
    private static Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target);
    }
    private static String label(View view) {
        if (view == null) return "NONE";
        CharSequence description = view.getContentDescription();
        if (description != null && description.length() > 0) return description.toString();
        if (view instanceof TextView) return ((TextView)view).getText().toString();
        return view.getClass().getSimpleName();
    }
    private void collect(View view) {
        views.add(view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)view;
            for (int i=0; i<group.getChildCount(); i++) collect(group.getChildAt(i));
        }
    }
    private View find(String part) {
        for (View view : views) if (label(view).contains(part)) return view;
        throw new IllegalStateException("No matching view: " + part);
    }
    private void screenshot(String name) throws Exception {
        Bitmap image = getUiAutomation().takeScreenshot();
        if (image == null) throw new IllegalStateException("Screenshot unavailable");
        try (FileOutputStream stream = new FileOutputStream(new File(output, prefix+"-"+name+".png"))) {
            image.compress(Bitmap.CompressFormat.PNG, 100, stream);
        }
        image.recycle();
    }
    private JSONObject accessibility(AccessibilityNodeInfo node) throws Exception {
        JSONObject value = new JSONObject().put("class", node.getClassName())
                .put("text", node.getText()).put("description", node.getContentDescription())
                .put("focusable", node.isFocusable()).put("focused", node.isFocused())
                .put("clickable", node.isClickable()).put("longClickable", node.isLongClickable())
                .put("checked", node.isChecked()).put("checkable", node.isCheckable())
                .put("enabled", node.isEnabled()).put("visible", node.isVisibleToUser());
        Rect bounds = new Rect(); node.getBoundsInScreen(bounds); value.put("bounds", bounds.toShortString());
        JSONArray children = new JSONArray();
        for (int i=0;i<node.getChildCount();i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if(child!=null) { children.put(accessibility(child)); child.recycle(); }
        }
        return value.put("children", children);
    }
    private void capture(String name, boolean inspectViews) throws Exception {
        waitForIdleSync(); SystemClock.sleep(350);
        JSONObject sample = new JSONObject().put("name", name);
        if (inspectViews) runOnMainSync(() -> {
            try {
                views.clear(); collect(activity.getWindow().getDecorView());
                JSONArray data = new JSONArray();
                for (View view : views) {
                    if (!view.isShown()) continue;
                    Rect visible = new Rect(); boolean inViewport = view.getGlobalVisibleRect(visible);
                    int[] location = new int[2]; view.getLocationOnScreen(location);
                    JSONObject item = new JSONObject().put("class",view.getClass().getSimpleName())
                            .put("label",label(view)).put("width",view.getWidth()).put("height",view.getHeight())
                            .put("x",location[0]).put("y",location[1]).put("visible",inViewport)
                            .put("visibleRect",visible.toShortString()).put("enabled",view.isEnabled())
                            .put("focused",view.isFocused()).put("focusable",view.isFocusable())
                            .put("clickable",view.isClickable()).put("longClickable",view.isLongClickable())
                            .put("backgroundStateful",view.getBackground()!=null && view.getBackground().isStateful());
                    if (view instanceof TextView) {
                        TextView text = (TextView)view;
                        android.text.Layout layout = text.getLayout();
                        item.put("text",text.getText()).put("textSizePx",text.getTextSize());
                        if(layout!=null) {
                            int ellipses=0; float maxWidth=0;
                            for(int line=0;line<layout.getLineCount();line++) { ellipses+=layout.getEllipsisCount(line); maxWidth=Math.max(maxWidth,layout.getLineMax(line)); }
                            item.put("lines",layout.getLineCount()).put("ellipsisCharacters",ellipses)
                                    .put("layoutHeight",layout.getHeight()).put("maxLineWidth",maxWidth)
                                    .put("availableWidth",Math.max(0,text.getWidth()-text.getCompoundPaddingLeft()-text.getCompoundPaddingRight()))
                                    .put("verticalTextClipped",layout.getHeight()+text.getCompoundPaddingTop()+text.getCompoundPaddingBottom()>text.getHeight());
                        }
                    }
                    data.put(item);
                }
                sample.put("views",data).put("focus",label(activity.getWindow().getDecorView().findFocus()))
                        .put("fontScale",activity.getResources().getConfiguration().fontScale)
                        .put("density",activity.getResources().getDisplayMetrics().density)
                        .put("widthPixels",activity.getResources().getDisplayMetrics().widthPixels)
                        .put("heightPixels",activity.getResources().getDisplayMetrics().heightPixels);
            } catch(Exception error) { throw new RuntimeException(error); }
        });
        AccessibilityNodeInfo root = null;
        for(int i=0;i<20 && root==null;i++) {
            root=getUiAutomation().getRootInActiveWindow();
            if(root!=null && !"com.thor.displaypowertest".equals(String.valueOf(root.getPackageName()))) { root.recycle(); root=null; }
            if(root==null) SystemClock.sleep(100);
        }
        if(root==null) throw new IllegalStateException("Target accessibility window unavailable: "+name);
        sample.put("accessibility",accessibility(root)); root.recycle();
        captures.put(sample); screenshot(name);
    }
    private boolean hasText(AccessibilityNodeInfo node, String expected) {
        if(expected.equals(String.valueOf(node.getText()))) return true;
        for(int i=0;i<node.getChildCount();i++) {
            AccessibilityNodeInfo child=node.getChild(i);
            if(child!=null) { boolean match=hasText(child,expected); child.recycle(); if(match)return true; }
        }
        return false;
    }
    private boolean clickVisibleText(AccessibilityNodeInfo node, String expected) {
        if(expected.equalsIgnoreCase(String.valueOf(node.getText())) && node.isVisibleToUser()
                && node.isEnabled() && node.isClickable()) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }
        for(int i=0;i<node.getChildCount();i++) {
            AccessibilityNodeInfo child=node.getChild(i);
            if(child!=null) { boolean clicked=clickVisibleText(child,expected); child.recycle(); if(clicked)return true; }
        }
        return false;
    }
    private void requireWindow(String expected) throws Exception {
        for(int i=0;i<20;i++) {
            SystemClock.sleep(150);
            AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
            if(root!=null) { boolean match=hasText(root,expected); root.recycle(); if(match)return; }
        }
        throw new IllegalStateException("Expected active window text: "+expected);
    }
    private void key(String name, int key, int modifiers) throws Exception {
        long now = SystemClock.uptimeMillis();
        sendKeySync(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,key,0,modifiers));
        sendKeySync(new KeyEvent(now,SystemClock.uptimeMillis(),KeyEvent.ACTION_UP,key,0,modifiers));
        waitForIdleSync();
        runOnMainSync(() -> {
            try { navigation.put(new JSONObject().put("key",name).put("focus",label(activity.getWindow().getDecorView().findFocus()))); }
            catch(Exception error) { throw new RuntimeException(error); }
        });
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            if (!"com.thor.displaypowertest".equals(getTargetContext().getPackageName())) throw new IllegalStateException("Unexpected target");
            output = getTargetContext().getExternalFilesDir(null);
            String hash = new String(new char[64]).replace('\0','a');
            String release = new JSONObject().put("tag","v1.7.2").put("prerelease",false)
                    .put("notes","Synthetic release metadata for layout only. No download is performed.")
                    .put("asset","Jesty-Thor-Fix-1.7.2.apk")
                    .put("url","https://github.com/JestyLabs/Jesty-Thor-Fix/releases/download/v1.7.2/Jesty-Thor-Fix-1.7.2.apk")
                    .put("size",4096).put("sha256",hash).toString();
            getTargetContext().getSharedPreferences("updates",0).edit().putBoolean("auto_check",false)
                    .putBoolean("include_prereleases",false).putString("release",release).putString("prompted_tag","v1.7.2").commit();
            getTargetContext().getSharedPreferences("state",0).edit().putBoolean("fix_enabled",false)
                    .putBoolean("dashboard_cpu_fix_enabled",false).putBoolean("lid_guard_enabled",false).commit();
            activity = startActivitySync(new Intent().setClassName("com.thor.displaypowertest","com.thor.displaypowertest.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            SystemClock.sleep(1600); waitForIdleSync();
            runOnMainSync(() -> {
                try { Object media=field(activity,"media"); Method pause=media.getClass().getDeclaredMethod("onPause"); pause.setAccessible(true); pause.invoke(media); }
                catch(Exception error) { throw new RuntimeException(error); }
            });
            capture("dashboard",true);
            for(int i=0;i<8;i++) key("TAB",KeyEvent.KEYCODE_TAB,0);
            key("SHIFT_TAB",KeyEvent.KEYCODE_TAB,KeyEvent.META_SHIFT_ON);
            key("DPAD_LEFT",KeyEvent.KEYCODE_DPAD_LEFT,0); key("DPAD_RIGHT",KeyEvent.KEYCODE_DPAD_RIGHT,0);
            key("DPAD_UP",KeyEvent.KEYCODE_DPAD_UP,0); key("DPAD_DOWN",KeyEvent.KEYCODE_DPAD_DOWN,0);
            capture("keyboard-focus",true);
            runOnMainSync(() -> find("App diagnostics").requestFocus());
            capture("diagnostics-focus",true);
            runOnMainSync(() -> find("GitHub project").requestFocus());
            capture("github-focus",true);
            runOnMainSync(() -> find("App diagnostics").requestFocus());
            key("SPACE_DIAGNOSTICS",KeyEvent.KEYCODE_SPACE,0);
            SystemClock.sleep(400);
            AccessibilityNodeInfo spaceRoot=getUiAutomation().getRootInActiveWindow();
            boolean spaceOpened=spaceRoot!=null && hasText(spaceRoot,"App diagnostics");
            if(spaceRoot!=null)spaceRoot.recycle();
            navigation.put(new JSONObject().put("key","SPACE_RESULT").put("openedDiagnostics",spaceOpened));
            if(spaceOpened) { key("BACK",KeyEvent.KEYCODE_BACK,0); requireWindow("TRUE BOTTOM SCREEN OFF"); }
            key("ENTER_DIAGNOSTICS",KeyEvent.KEYCODE_ENTER,0);
            requireWindow("App diagnostics");
            SystemClock.sleep(650); capture("enter-diagnostics",false);
            AccessibilityNodeInfo diagnosticRoot=getUiAutomation().getRootInActiveWindow();
            boolean sleepEntryOpened=diagnosticRoot!=null && clickVisibleText(diagnosticRoot,"Sleep trial");
            if(diagnosticRoot!=null)diagnosticRoot.recycle();
            navigation.put(new JSONObject().put("key","SLEEP_ENTRY_RESULT").put("openedSleepEntry",sleepEntryOpened));
            if(sleepEntryOpened) { requireWindow("Passive sleep trial"); capture("sleep-entry",false); }
            key("BACK",KeyEvent.KEYCODE_BACK,0);
            requireWindow("TRUE BOTTOM SCREEN OFF");
            runOnMainSync(() -> find("GitHub project").requestFocus());
            waitForIdleSync(); SystemClock.sleep(350);
            long now=SystemClock.uptimeMillis();
            sendKeySync(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER,0));
            SystemClock.sleep(700);
            sendKeySync(new KeyEvent(now,SystemClock.uptimeMillis(),KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER,1,0,0,0,KeyEvent.FLAG_LONG_PRESS));
            sendKeySync(new KeyEvent(now,SystemClock.uptimeMillis(),KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER,0));
            requireWindow("Updates");
            capture("settings-keyboard",false);
            key("BACK",KeyEvent.KEYCODE_BACK,0);
            requireWindow("TRUE BOTTOM SCREEN OFF");
            // Show cached-update dialog through real updater; do not press Update/Download.
            runOnMainSync(() -> { try { Object updater=field(activity,"updater"); Method prompt=updater.getClass().getDeclaredMethod("promptUpdate"); prompt.setAccessible(true); prompt.invoke(updater); } catch(Exception error) { throw new RuntimeException(error); } });
            requireWindow("Update available");
            capture("update-dialog",false);
            key("BACK",KeyEvent.KEYCODE_BACK,0);
            requireWindow("TRUE BOTTOM SCREEN OFF");
            runOnMainSync(() -> {
                for(View view:views) if(view instanceof ScrollView) { ScrollView scroll=(ScrollView)view; scroll.scrollTo(0,scroll.getChildAt(0).getHeight()); break; }
            });
            capture("lower-metrics",true);
            JSONObject report = new JSONObject().put("captures",captures).put("navigation",navigation)
                    .put("syntheticCachedRelease",true).put("mediaPausedForStaticCapture",true)
                    .put("hardwareControlsActivated",false).put("talkBackSpeechTested",false);
            try(FileOutputStream stream=new FileOutputStream(new File(output,prefix+"-results.json"))) { stream.write(report.toString(2).getBytes(StandardCharsets.UTF_8)); }
            result.putString("result","PASS"); finish(Activity.RESULT_OK,result);
        } catch(Throwable error) {
            android.util.Log.e("UiAudit","AUDIT_ERROR",error); result.putString("error",error.toString()); finish(Activity.RESULT_CANCELED,result);
        }
    }
}
