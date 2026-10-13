package com.thor.displaypowertest;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.widget.Toast;
import java.io.File;
import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Production AppUpdater + production UpdateInstallReceiver + actual session
 * registration/commit code, but with disposable Android platform fakes.
 * Never downloads, verifies, signs or installs an APK.
 */
public final class UpdaterOwnershipRegression {
    private static int assertions, scenarios;
    private static File root;
    private static final UpdateInstallReceiver RECEIVER = new UpdateInstallReceiver();
    private static final AppUpdater.Host HOST = new AppUpdater.Host() {
        public void onUpdateAvailable(String version, boolean pre) { }
        public void onNoUpdate() { }
        public String installBlocker() { return null; }
        public String handoverNote() { return "host"; }
        public void setInstallReserved(boolean value) { }
    };
    private static void check(boolean condition, String label) {
        assertions++;
        if (!condition) throw new AssertionError(label);
    }
    private static Object field(Object target, String name) throws Exception {
        Field f = AppUpdater.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }
    private static UpdateSessionOwnership owner() throws Exception {
        return (UpdateSessionOwnership)field(null,"installSessions");
    }
    private static UpdateConfirmationStore<?> prompts() throws Exception {
        return (UpdateConfirmationStore<?>)field(null,"pendingConfirmations");
    }
    private static Intent status(int code,int id) {
        return new Intent().setAction(UpdateInstallReceiver.ACTION_STATUS)
                .putExtra(PackageInstaller.EXTRA_STATUS,code)
                .putExtra(PackageInstaller.EXTRA_SESSION_ID,id);
    }
    private static void pending(Activity activity,int id,Intent prompt) {
        RECEIVER.onReceive(activity,status(-1,id).putExtra(Intent.EXTRA_INTENT,prompt));
    }
    private static File artifact(Activity activity,String filename) throws Exception {
        File dir = new File(activity.getCacheDir(),"updates");
        check(dir.mkdirs() || dir.isDirectory(),"fixture directory exists");
        File file = new File(dir,filename);
        Files.write(file.toPath(),new byte[] {1,2,3});
        return file;
    }
    private static void resetVolatile() throws Exception {
        UpdateSessionOwnership ownership=owner();
        for(String name:new String[]{"serial","attempt","transferInProgress","sessionId"}) {
            Field f=UpdateSessionOwnership.class.getDeclaredField(name);
            f.setAccessible(true);
            if(name.equals("serial"))f.setLong(ownership,0L);
            else if(name.equals("attempt"))f.setLong(ownership,-1L);
            else if(name.equals("sessionId"))f.setInt(ownership,-1);
            else f.setBoolean(ownership,false);
        }
        prompts().discardAll();
        Field resumed=AppUpdater.class.getDeclaredField("resumedActivity");
        resumed.setAccessible(true);
        resumed.set(null,new WeakReference<Activity>(null));
        Toast.reset();
    }
    private static final class Scenario implements AutoCloseable {
        final Activity activity;
        final PackageInstaller installer;
        final AppUpdater updater;
        Scenario() throws Exception {
            resetVolatile();
            activity=new Activity(new File(root,"scenario-"+(++scenarios)));
            check(activity.getCacheDir().mkdirs(),"unique private cache");
            updater=new AppUpdater(activity,HOST);
            installer=activity.getPackageManager().getPackageInstaller();
        }
        long begin() throws Exception {
            long token=owner().beginTransfer();
            check(token>0,"transfer reserved");
            return token;
        }
        void install(long token,int id,File file) throws Exception {
            installer.nextId=id;
            UpdateCommitGate gate=(UpdateCommitGate)field(updater,"commitGate");
            check(gate.start(),"real commit gate started");
            Method method=AppUpdater.class.getDeclaredMethod("install",File.class,long.class);
            method.setAccessible(true);
            try {method.invoke(updater,file,token);}
            catch(InvocationTargetException e) {throw (Exception)e.getCause();}
        }
        void workerFinished(long token) throws Exception {
            owner().finishTransfer(token);
            ((UpdateCommitGate)field(updater,"commitGate")).finish();
        }
        long bound(int id) throws Exception {
            long token=begin();
            install(token,id,artifact(activity,"prepared.apk"));
            workerFinished(token);
            check(owner().ownsSession(id),"production updater registered native session");
            return token;
        }
        boolean attemptDownloadPrefix() throws Exception {
            Method method=AppUpdater.class.getDeclaredMethod("download",
                    Class.forName("com.thor.displaypowertest.UpdateReleaseCatalog$Release"));
            method.setAccessible(true);
            try {
                method.invoke(updater,new Object[]{null});
                return Toast.lastText != null && Toast.lastText.contains("HOST_UI_CHECKPOINT")
                        && !((AtomicBoolean)field(updater,"busy")).get()
                        && !owner().transferInProgress();
            } catch(InvocationTargetException e) {
                if("HOST_UI_CHECKPOINT".equals(e.getCause().getMessage())) return true;
                throw (Exception)e.getCause();
            }
        }
        public void close() {
            updater.onPause();
            updater.shutdown();
            Thread.interrupted();
        }
    }
    private static void originalRegressions() throws Exception {
        try(Scenario s=new Scenario()) {
            s.bound(9);
            pending(s.activity,9,new Intent());
            check(s.activity.starts==0,"old pending confirmation is deferred");
            RECEIVER.onReceive(s.activity,status(3,9));
            s.updater.onResume();
            check(s.activity.starts==0&&!owner().ownsSession(9),
                    "canceled session9 cannot launch after resume");
            int prior=Toast.shown;
            File newer=artifact(s.activity,"new-download.apk");
            long token=s.begin();
            for(int code:new int[]{3,0,1,3})RECEIVER.onReceive(s.activity,status(code,9));
            check(newer.exists()&&Toast.shown==prior,
                    "obsolete callback cannot erase or notify during new transfer");
            s.install(token,11,newer);
            s.workerFinished(token);
            s.updater.onPause();
            Intent newPrompt=new Intent();
            pending(s.activity,11,newPrompt);
            prior=Toast.shown;
            for(int code:new int[]{3,0,1})RECEIVER.onReceive(s.activity,status(code,9));
            check(newer.exists()&&owner().ownsSession(11)&&Toast.shown==prior,
                    "obsolete callback cannot disturb new pending native session");
            s.updater.onResume();
            check(s.activity.starts==1&&s.activity.last==newPrompt,
                    "legitimate session11 prompt launches exactly once");
            s.updater.onResume();
            check(s.activity.starts==1,"session11 prompt consumed only once");
            RECEIVER.onReceive(s.activity,status(0,11));
            check(!newer.exists()&&!owner().ownsSession(11),
                    "matching success cleans new session only");
            File after=artifact(s.activity,"later-transfer.apk");
            prior=Toast.shown;
            RECEIVER.onReceive(s.activity,status(0,11));
            RECEIVER.onReceive(s.activity,status(3,11));
            check(after.exists()&&Toast.shown==prior,"duplicate terminal statuses inert");
        }
    }
    private static void actualInstallerFailures() throws Exception {
        for(int terminal:new int[]{1,3})try(Scenario s=new Scenario()) {
            s.bound(9);
            File data=artifact(s.activity,"owned.apk");
            s.updater.onResume();
            pending(s.activity,9,new Intent());
            check(s.activity.starts==1,"owned foreground confirmation shown");
            RECEIVER.onReceive(s.activity,status(terminal,9));
            check(!data.exists()&&!owner().ownsSession(9)
                    &&s.installer.abandoned.contains(9),"owned terminal cleans and abandons");
            int prior=Toast.shown;
            RECEIVER.onReceive(s.activity,status(terminal,9));
            check(Toast.shown==prior,"duplicate failure feedback suppressed");
        }
        try(Scenario s=new Scenario()) {
            s.bound(9);
            s.activity.rejectStart=true;
            s.updater.onResume();
            pending(s.activity,9,new Intent());
            check(!owner().ownsSession(9)&&s.installer.abandoned.contains(9),
                    "Android consent activity failure cleaned");
        }
        try(Scenario s=new Scenario()) {
            s.bound(9);
            RECEIVER.onReceive(s.activity,status(-1,9));
            check(!owner().ownsSession(9)&&s.installer.abandoned.contains(9),
                    "missing Android confirmation abandoned");
        }
        for(String failure:new String[]{"open","fsync","commit","interrupted","cancel"}) {
            try(Scenario s=new Scenario()) {
                long token=s.begin();
                File file=artifact(s.activity,"prepared.apk");
                s.installer.failOpen=failure.equals("open");
                s.installer.failFsync=failure.equals("fsync");
                s.installer.failCommit=failure.equals("commit");
                if(failure.equals("interrupted"))Thread.currentThread().interrupt();
                if(failure.equals("cancel"))((AtomicBoolean)field(s.updater,"cancelled")).set(true);
                boolean failed=false;
                try{s.install(token,9,file);}catch(Exception expected){failed=true;}
                finally{Thread.interrupted();s.workerFinished(token);}
                check(failed&&!owner().ownsSession(9)&&s.installer.abandoned.contains(9)
                    &&s.installer.commits==0,"real native install "+failure+" must not commit");
            }
        }
        try(Scenario s=new Scenario()) {
            long token=s.begin();
            File file=artifact(s.activity,"prepared.apk");
            UpdateCommitGate gate=(UpdateCommitGate)field(s.updater,"commitGate");
            check(gate.start()&&gate.cancel(),"cancel gate accepted");
            s.installer.nextId=9;
            Method method=AppUpdater.class.getDeclaredMethod("install",File.class,long.class);
            method.setAccessible(true);
            boolean rejected=false;
            try{method.invoke(s.updater,file,token);}
            catch(InvocationTargetException expected){rejected=true;}
            check(rejected&&s.installer.commits==0&&!owner().ownsSession(9),
                    "cancel before commit blocks real install");
            s.workerFinished(token);
        }
    }
    private static void replacementAndRestart() throws Exception {
        try(Scenario s=new Scenario()) {
            s.bound(9);
            s.updater.onPause();
            pending(s.activity,9,new Intent());
            File next=artifact(s.activity,"replacement.apk");
            check(s.attemptDownloadPrefix(),"normal replacement reaches host UI checkpoint");
            check(s.installer.abandoned.contains(9)&&!s.installer.sessions.containsKey(9)
                    &&!owner().ownsSession(9)&&next.exists(),
                    "old Android session removed before replacement");
            check(prompts().pendingSessionId()==-1,"old consent cleared after successful release");
        }
        try(Scenario s=new Scenario()) {
            s.bound(9);
            s.updater.onPause();
            pending(s.activity,9,new Intent());
            File cache=artifact(s.activity,"preserve-on-failure.apk");
            s.installer.failAbandon=true;
            check(!s.attemptDownloadPrefix(),
                    "R1: native abandon failure blocks before creating replacement UI");
            check(s.installer.sessions.containsKey(9)&&owner().ownsSession(9)
                    &&!owner().transferInProgress()&&cache.exists(),
                    "R1: old native session, owner and download remain unchanged");
            check(prompts().pendingSessionId()==9,
                    "R1: old consent retained after failed abandon");
            check(!((UpdateCommitGate)field(s.updater,"commitGate")).cancel(),
                    "R1: rejected transfer releases commit gate");
            s.installer.failAbandon=false;
            check(s.attemptDownloadPrefix(),"R1: user may retry when native abandon succeeds");
            check(!s.installer.sessions.containsKey(9)&&!owner().ownsSession(9)
                    &&!owner().transferInProgress(),"R1: successful native retry reaches UI checkpoint then recovers");
        }
        try(Scenario s=new Scenario()) {
            s.bound(9);
            File cache=artifact(s.activity,"inventory-failure.apk");
            s.installer.failList=true;
            check(!s.attemptDownloadPrefix(),
                    "native inventory failure must block replacement");
            check(cache.exists()&&owner().ownsSession(9)&&!owner().transferInProgress(),
                    "inventory failure preserves owner and cache");
            s.installer.failList=false;
            check(s.attemptDownloadPrefix(),
                    "inventory retry can proceed after native list works");
        }
        try(Scenario s=new Scenario()) {
            s.bound(9);
            File cache=artifact(s.activity,"still-listed.apk");
            s.installer.retainAfterAbandon=true;
            check(!s.attemptDownloadPrefix(),
                    "void abandon return with a still-listed session must fail closed");
            check(cache.exists()&&owner().ownsSession(9)&&!owner().transferInProgress(),
                    "still-listed session preserves old ownership and transfer gate");
            s.installer.retainAfterAbandon=false;
            check(s.attemptDownloadPrefix(),
                    "retry succeeds only after native list confirms no session");
        }
        try(Scenario s=new Scenario()) {
            s.bound(9);
            resetVolatile();
            long token=s.begin();
            File cache=artifact(s.activity,"post-loss.apk");
            RECEIVER.onReceive(s.activity,status(3,9));
            RECEIVER.onReceive(s.activity,status(0,9));
            s.updater.start();
            check(cache.exists()&&Toast.shown==0&&owner().transferInProgress(),
                    "simulated process-state loss ignores stale callbacks during live transfer");
            s.workerFinished(token);
        }
        try(Scenario s=new Scenario()) {
            s.bound(9);
            resetVolatile();
            s.updater.start();
            check(!s.installer.sessions.containsKey(9)&&s.installer.abandoned.contains(9),
                    "startup attempts old native session cleanup");
        }
        try(Scenario s=new Scenario()) {
            s.bound(9);
            s.updater.onResume();
            pending(s.activity,9,new Intent());
            pending(s.activity,9,new Intent());
            check(s.activity.starts==1,
                    "repeated current-session pending prompts launch once");
        }
    }
    public static void main(String[] args) throws Exception {
        root=new File(args[0]);
        check(root.mkdirs()||root.isDirectory(),"top-level fixture root");
        originalRegressions();
        actualInstallerFailures();
        replacementAndRestart();
        System.out.println("UpdaterOwnershipRegression passed: "+assertions
                +" assertions in "+scenarios+" scenarios; Android is host-simulated");
    }
}
