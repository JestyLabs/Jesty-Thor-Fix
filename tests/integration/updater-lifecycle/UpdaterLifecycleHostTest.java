package com.thor.displaypowertest;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;
import java.io.File;
import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Controlled host ordering; no HTTP, APK validation, native Android or physical device. */
public final class UpdaterLifecycleHostTest {
    private static File root;
    private static boolean fixed, lease, queuedFix;
    private static int checks, cases;
    private static final UpdateInstallReceiver receiver = new UpdateInstallReceiver();
    private static void check(boolean value, String label) {
        checks++;
        if (!value) throw new AssertionError(label);
    }
    private static Object field(Object target, String name) throws Exception {
        Field f = AppUpdater.class.getDeclaredField(name); f.setAccessible(true); return f.get(target);
    }
    private static UpdateSessionOwnership owner() throws Exception {
        return (UpdateSessionOwnership) field(null, "installSessions");
    }
    private static Intent status(int value, int id) {
        return new Intent().setAction(UpdateInstallReceiver.ACTION_STATUS)
                .putExtra(PackageInstaller.EXTRA_STATUS, value)
                .putExtra(PackageInstaller.EXTRA_SESSION_ID, id);
    }
    private static void pending(Activity activity, int id) {
        receiver.onReceive(activity, status(-1, id).putExtra(Intent.EXTRA_INTENT, new Intent()));
    }
    private static final class RecordingHost implements AppUpdater.Host {
        boolean reserved, failReserve, failRelease;
        int reservations, releases;
        public void onUpdateAvailable(String version, boolean pre) { }
        public void onNoUpdate() { }
        Runnable blockerHook;
        public String installBlocker() { if(blockerHook!=null) blockerHook.run(); return null; }
        public String handoverNote() { return "host"; }
        public void setInstallReserved(boolean value) {
            reserved = value;
            if (value) reservations++; else releases++;
            if (value && failReserve) throw new IllegalStateException("HOST_RESERVATION_FAILURE");
            if (!value && failRelease) throw new IllegalStateException("HOST_RELEASE_FAILURE");
        }
    }
    private static final class ControlledActivity extends Activity {
        final Thread uiThread = Thread.currentThread();
        final Queue<Runnable> uiQueue = new ConcurrentLinkedQueue<>();
        boolean queueWorkerUi, destroyed;
        ControlledActivity(File cache) { super(cache); }
        @Override public boolean isDestroyed() { return destroyed; }
        @Override public void runOnUiThread(Runnable action) {
            if (queueWorkerUi && Thread.currentThread()!=uiThread) uiQueue.add(action);
            else action.run();
        }
        void flushUi() { Runnable action; while((action=uiQueue.poll())!=null) action.run(); }
    }
    private static final class CapturingExecutor extends AbstractExecutorService {
        final List<Runnable> tasks = new ArrayList<>();
        final CountDownLatch accepted = new CountDownLatch(1);
        volatile boolean reject, stopped;
        int submissions;
        volatile int ran;
        public synchronized void execute(Runnable task) {
            submissions++;
            if (reject || stopped) throw new RejectedExecutionException("HOST_WORKER_REJECTION");
            tasks.add(task); accepted.countDown();
        }
        public void shutdown() { stopped = true; }
        public List<Runnable> shutdownNow() { stopped = true; return new ArrayList<>(tasks); }
        public boolean isShutdown() { return stopped; }
        public boolean isTerminated() { return stopped; }
        public boolean awaitTermination(long timeout, TimeUnit unit) { return stopped; }
    }
    private static void reset() throws Exception {
        Object o = owner();
        for (String name : new String[]{"serial", "attempt", "transferInProgress", "sessionId"}) {
            Field f = UpdateSessionOwnership.class.getDeclaredField(name); f.setAccessible(true);
            if (name.equals("serial")) f.setLong(o, 0);
            else if (name.equals("attempt")) f.setLong(o, -1);
            else if (name.equals("sessionId")) f.setInt(o, -1);
            else f.setBoolean(o, false);
        }
        ((UpdateConfirmationStore<?>)field(null, "pendingConfirmations")).discardAll();
        Field activity = AppUpdater.class.getDeclaredField("resumedActivity");
        activity.setAccessible(true); activity.set(null, new WeakReference<Activity>(null));
        LinearLayout.failConstructor = false; AlertDialog.failShow = false;
        AlertDialog.last = null; AlertDialog.failDismiss=false; View.failListener = false; Toast.reset(); android.util.Log.reset();
    }
    private static final class Scenario implements AutoCloseable {
        final ControlledActivity activity;
        final RecordingHost host = new RecordingHost();
        final AppUpdater updater;
        final CapturingExecutor executor = new CapturingExecutor();
        Scenario() throws Exception { this(true); }
        Scenario(boolean resetGlobals) throws Exception {
            if(resetGlobals) reset();
            File cache = new File(root, "case-" + (++cases));
            check(cache.mkdirs(), "new fixture cache");
            activity = new ControlledActivity(cache); updater = new AppUpdater(activity, host);
            ((ExecutorService)field(updater, "worker")).shutdownNow();
            Field worker = AppUpdater.class.getDeclaredField("worker"); worker.setAccessible(true);
            worker.set(updater, executor);
        }
        File artifact(String name) throws Exception {
            File dir = new File(activity.getCacheDir(), "updates");
            check(dir.mkdirs() || dir.isDirectory(), "fixture updates directory");
            File f = new File(dir, name); Files.write(f.toPath(), new byte[]{1,2,3}); return f;
        }
        void bind(int id) throws Exception {
            long token = owner().beginTransfer(); check(token > 0, "reserve actual session transfer");
            UpdateCommitGate gate = (UpdateCommitGate) field(updater, "commitGate"); check(gate.start(), "start actual installer gate");
            activity.getPackageManager().getPackageInstaller().nextId = id;
            Method install = AppUpdater.class.getDeclaredMethod("install", File.class, long.class); install.setAccessible(true);
            install.invoke(updater, artifact("old.apk"), token);
            owner().finishTransfer(token); gate.finish();
            check(owner().ownsSession(id), "real install registration");
        }
        Throwable download() throws Exception {
            Class<?> type = Class.forName("com.thor.displaypowertest.AppUpdater$Release");
            Constructor<?> ctor = type.getDeclaredConstructors()[0]; ctor.setAccessible(true);
            Object release = ctor.newInstance("v1.7.2", false, "host", "host.apk", "https://fixture.invalid/host.apk", 3L, "00");
            Method method = AppUpdater.class.getDeclaredMethod("download", type); method.setAccessible(true);
            try { method.invoke(updater, release); return null; }
            catch (InvocationTargetException error) { return error.getCause(); }
        }
        void runTask(int index) throws Exception {
            AtomicReference<Throwable> fault=new AtomicReference<>();
            Thread worker=new Thread(()->{
                try { executor.ran++; executor.tasks.get(index).run(); }
                catch(Throwable error) {fault.set(error);}
            },"actual-updater-worker");
            worker.start(); worker.join(3000L);
            check(!worker.isAlive() && fault.get()==null,"actual captured worker finishes without hidden exception");
        }
        boolean coreRecovered() throws Exception {
            Field state = UpdateCommitGate.class.getDeclaredField("state"); state.setAccessible(true);
            return !((AtomicBoolean)field(updater, "busy")).get()
                    && "IDLE".equals(state.get(field(updater, "commitGate")).toString())
                    && !owner().transferInProgress() && !host.reserved;
        }
        public void close() {
            updater.onPause(); updater.shutdown();
        }
    }
        // setup uses coreRecovered plus visual state when no dismiss fault is injected.
    private static void setupFailures() throws Exception {
        for (String failure : new String[]{"reservation", "resources", "layout", "dialog", "button", "executor"})
            try (Scenario s = new Scenario()) {
                s.host.failReserve = failure.equals("reservation");
                s.activity.failResources = failure.equals("resources");
                LinearLayout.failConstructor = failure.equals("layout");
                AlertDialog.failShow = failure.equals("dialog");
                View.failListener = failure.equals("button");
                s.executor.reject = failure.equals("executor");
                Throwable escaped = s.download();
                boolean recovered = s.coreRecovered() && (AlertDialog.last==null || !AlertDialog.last.isShowing());
                check(recovered == fixed, "setup failure " + failure + " recovery expected=" + fixed);
                if (!fixed) check(escaped != null, "unfixed setup error escapes caller");
                s.host.failReserve = false; s.activity.failResources = false;
                LinearLayout.failConstructor = false; AlertDialog.failShow = false;
                View.failListener = false; s.executor.reject = false;
                int accepted = s.executor.tasks.size();
                check(s.download() == null, "retry call itself does not throw");
                check(s.executor.tasks.size() == accepted + (fixed ? 1 : 0), "retry acceptance follows released ownership");
                System.out.println("FAILURE_HOST injected=" + failure + " recovered=" + recovered
                        + " retry_accepted=" + (s.executor.tasks.size() > accepted));
            }
    }
    private static void duplicatedConfirmations() throws Exception {
        try (Scenario s = new Scenario()) {
            s.bind(9); s.updater.onResume(); pending(s.activity,9); pending(s.activity,9);
            check(s.activity.starts == 1, "foreground duplicate launches only once per session");
            System.out.println("PENDING_HOST foreground duplicates: starts=" + s.activity.starts);
        }
        try (Scenario s = new Scenario()) {
            s.bind(9); pending(s.activity,9); pending(s.activity,9);
            check(Toast.shown == 1 && s.activity.starts == 0, "paused duplicate retains one prompt and message");
            s.updater.onResume(); check(s.activity.starts == 1, "deferred slot delivers once");
            s.updater.onPause(); s.updater.onResume(); pending(s.activity,9);
            check(s.activity.starts == 1, "same-session duplicate after pause/resume stays consumed");
            receiver.onReceive(s.activity,status(3,9)); int before=s.activity.starts;
            pending(s.activity,9); check(s.activity.starts==before, "terminal stops further confirmation");
            s.bind(11); pending(s.activity,11); check(s.activity.starts==before+1, "new native session remains launchable");
            receiver.onReceive(s.activity,status(3,9)); pending(s.activity,11);
            check(s.activity.starts==before+1, "old terminal does not release newer launch marker");
            System.out.println("PENDING_HOST paused messages=1; same-session launches=1; new session remains eligible");
        }
        try (Scenario s = new Scenario()) {
            s.bind(9); s.updater.onResume(); s.activity.rejectStart=true; pending(s.activity,9);
            check(!owner().ownsSession(9), "existing launch failure invalidates session, not same-session retry");
            s.activity.rejectStart=false; pending(s.activity,9); check(s.activity.starts==0, "failed old session cannot retry via duplicate");
            s.bind(11); pending(s.activity,11); check(s.activity.starts==1, "explicit new attempt retries after launch failure");
            System.out.println("PENDING_HOST launch failure: old session invalidated; retry requires new session");
        }
    }
    private static void nativeReplacementControls() throws Exception {
        for (String failure : new String[]{"abandon", "inventory", "retained"}) try (Scenario s = new Scenario()) {
            s.bind(9); pending(s.activity,9); File existing=s.artifact("keep-native-failure.apk");
            PackageInstaller installer=s.activity.getPackageManager().getPackageInstaller();
            installer.failAbandon=failure.equals("abandon"); installer.failList=failure.equals("inventory");
            installer.retainAfterAbandon=failure.equals("retained");
            check(s.download()==null && s.executor.tasks.isEmpty(), "native "+failure+" blocks worker submission");
            check(owner().ownsSession(9) && existing.exists() && s.coreRecovered(), "native rejection preserves session/cache and releases attempt gates");
            check(((UpdateConfirmationStore<?>)field(null,"pendingConfirmations")).pendingSessionId()==9, "native rejection preserves old prompt");
            installer.failAbandon=false; installer.failList=false; installer.retainAfterAbandon=false;
            check(s.download()==null && s.executor.tasks.size()==1 && !owner().ownsSession(9), "native retry starts only after reconciled inventory");
            System.out.println("NATIVE_CONTROL_HOST failure="+failure+" preserved=true; retry_after_reconciliation=true");
        }
    }
    private static String gateState(Scenario s) throws Exception {
        Field f=UpdateCommitGate.class.getDeclaredField("state"); f.setAccessible(true);
        return f.get(field(s.updater,"commitGate")).toString();
    }
    private static void secondaryFailures() throws Exception {
        for(String secondary:new String[]{"dismiss","release","toast","combined"}) try(Scenario s=new Scenario()) {
            s.executor.reject=true;
            AlertDialog.failDismiss=secondary.equals("dismiss")||secondary.equals("combined");
            s.host.failRelease=secondary.equals("release")||secondary.equals("combined");
            Toast.failShow=secondary.equals("toast")||secondary.equals("combined");
            Throwable escaped=s.download();
            check(s.coreRecovered()==fixed,"secondary failure restores core state: "+secondary);
            check(s.executor.tasks.isEmpty()&&s.executor.ran==0,"rejected task never accepted or executed");
            if(fixed) {
                check(escaped==null,"secondary diagnostics do not escape preparation rollback");
                check(android.util.Log.errors.stream().anyMatch(e->"HOST_WORKER_REJECTION".equals(e.getMessage())),"original submission failure remains logged");
            } else check(escaped instanceof RejectedExecutionException,"baseline primary rejection escapes");
            boolean dialogStillVisible=AlertDialog.last!=null&&AlertDialog.last.isShowing();
            System.out.println("SECONDARY_HOST injected="+secondary+" core_recovered="+s.coreRecovered()+" dialog_still_visible="+dialogStillVisible
                    +"; failed dismiss is best-effort, not visual recovery proof");
            AlertDialog.failDismiss=false;Toast.failShow=false;s.host.failRelease=false;s.executor.reject=false;
            int accepted=s.executor.tasks.size();check(s.download()==null,"retry call returns");
            check(s.executor.tasks.size()==accepted+(fixed?1:0),"retry after secondary failure uses released control state");
        }
    }
    private static void acceptedTaskOwnership() throws Exception {
        try(Scenario s=new Scenario()) {
            s.activity.queueWorkerUi=true;
            check(s.download()==null&&s.executor.tasks.size()==1,"accepted submission remains reserved for worker");
            check(s.host.reserved&&owner().transferInProgress()&&"ACTIVE".equals(gateState(s)),"accepted task not rolled back by outer handler");
            s.runTask(0);
            check(!owner().transferInProgress()&&!((AtomicBoolean)field(s.updater,"busy")).get()&&"IDLE".equals(gateState(s)),"actual worker finally owns core cleanup");
            check(s.host.reserved&&!s.activity.uiQueue.isEmpty(),"UI release pending after actual worker finally");
            check(!android.util.Log.messages.contains("update preparation failed"),"accepted worker fault does not use setup recovery");
            s.activity.flushUi();check(s.coreRecovered(),"worker UI release completes normally");
            check(s.executor.ran==1,"accepted task executed exactly once");
            System.out.println("WORKER_HOST accepted task: real worker failure/finally ran; no setup double cleanup");
        }
    }
    private static void staleUiRelease() throws Exception {
        try(Scenario s=new Scenario()) {
            s.activity.queueWorkerUi=true;
            check(s.download()==null,"first attempt admitted");s.runTask(0);
            check(s.host.reserved&&!s.activity.uiQueue.isEmpty(),"old UI release held while core state is free");
            check(s.download()==null&&s.executor.tasks.size()==2,"next attempt admitted before old UI queue runs");
            Object nextDialog=field(s.updater,"progressDialog");
            check(s.host.reserved&&owner().transferInProgress(),"new attempt owns active reservation");
            s.activity.flushUi();
            check(s.host.reserved==lease,"old UI callback must not release newer same-Activity reservation");
            check(owner().transferInProgress()&&((AtomicBoolean)field(s.updater,"busy")).get(),"new core attempt remains active after old UI callback");
            check(field(s.updater,"progressDialog")==nextDialog&&((AlertDialog)nextDialog).isShowing(),"old callback does not dismiss/clear new dialog");
            System.out.println("UI_ORDER_HOST actual worker finally -> new attempt -> old UI queue: new_reserved="+s.host.reserved
                    +" new_transfer_still_active=true");
        }
        try(Scenario s=new Scenario()) {
            s.activity.queueWorkerUi=true;
            check(s.download()==null,"serial UI first admission");s.runTask(0);s.activity.flushUi();
            check(s.coreRecovered()&&s.download()==null&&s.host.reserved,"serial UI cleanup before next attempt preserves reservation");
            System.out.println("UI_CONTROL_HOST cleanup queue first: new reservation preserved");
        }
    }
    private static void lifecycleAndActivityOwnership() throws Exception {
        try(Scenario a=new Scenario()) {
            a.activity.resourcesHook=a.updater::shutdown;
            Throwable escaped=a.download();
            check(a.executor.tasks.isEmpty()&&a.executor.ran==0,"shutdown before submission never executes a task");
            check(a.coreRecovered()==fixed,"shutdown-driven submission rejection recovery");
            if(fixed)check(escaped==null,"shutdown rejection contained");
            a.activity.resourcesHook=null;
            try(Scenario b=new Scenario(false)) {
                check(b.download()==null,"new Activity attempts admission");
                check((b.executor.tasks.size()==1)==fixed,"new Activity succeeds only after old setup ownership released");
                if(fixed){a.updater.shutdown();check(b.host.reserved&&owner().transferInProgress(),"old Activity teardown cannot clear new Activity reservation");}
            }
            System.out.println("LIFECYCLE_HOST shutdown before execute: recreated_activity_admitted="+fixed);
        }
        try(Scenario a=new Scenario()) {
            a.activity.queueWorkerUi=true;check(a.download()==null,"old Activity worker admitted");a.runTask(0);a.activity.destroyed=true;
            try(Scenario b=new Scenario(false)) {
                check(b.download()==null&&b.host.reserved,"new Activity owns independent Host reservation");
                a.activity.flushUi();a.updater.shutdown();
                check(b.host.reserved&&owner().transferInProgress(),"dropped old Activity UI/teardown does not clear independent new Host");
            }
            System.out.println("LIFECYCLE_HOST destroyed old Activity: newer Host reservation preserved");
        }
        try(Scenario a=new Scenario()) {
            a.activity.failResources=true;
            try(Scenario b=new Scenario(false)) {
                AtomicReference<Throwable> nested=new AtomicReference<>();
                Toast.failShow=true;
                Toast.showHook=()->{Toast.showHook=null;try{nested.set(b.download());}catch(Throwable e){nested.set(e);}};
                Throwable escaped=a.download();
                if(fixed) {
                    check(escaped==null&&nested.get()==null&&b.host.reserved,"diagnostic failure cannot clear a newly admitted other-Activity Host");
                    check(b.executor.tasks.size()==1&&owner().transferInProgress(),"new attempt remains globally owned after old diagnostic failure");
                } else check(escaped!=null&&b.executor.tasks.isEmpty(),"baseline has no setup recovery diagnostic transition");
                Toast.failShow=false;Toast.showHook=null;
            }
            System.out.println("ACTIVITY_HOST reentrant diagnostic fixture: preserves_new_owner="+fixed);
        }
    }
    private static void codeqlGateInterleavings() throws Exception {
        for(String action:new String[]{"cancel","shutdown"}) try(Scenario s=new Scenario()) {
            CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
            PackageInstaller installer=s.activity.getPackageManager().getPackageInstaller();installer.failList=true;
            installer.listHook=()->{entered.countDown();try{if(!release.await(3,TimeUnit.SECONDS))throw new AssertionError("native inventory barrier");}catch(InterruptedException e){throw new AssertionError(e);}};
            AtomicReference<Throwable> fault=new AtomicReference<>();
            Thread admission=new Thread(()->{try{fault.set(s.download());}catch(Throwable e){fault.set(e);}},"gate-admission");
            admission.start();check(entered.await(3,TimeUnit.SECONDS),"pause after atomic gate.start and before rejected reservation");
            check("ACTIVE".equals(gateState(s)),"gate.start performs ACTIVE transition, not just state inspection");
            if(action.equals("cancel"))check(((UpdateCommitGate)field(s.updater,"commitGate")).cancel(),"cancel interleaved before rejection cleanup");
            else s.updater.shutdown();
            check("CANCELLED".equals(gateState(s)),"interleaved cancellation accepted");
            release.countDown();admission.join(3000L);
            check(!admission.isAlive()&&fault.get()==null&&s.coreRecovered(),"rejected admission resets its local gate without active global ownership");
            check(s.executor.tasks.isEmpty()&&s.executor.ran==0,"gate interleaving cannot reach worker/commit");
            System.out.println("CODEQL_HOST interleave="+action+" rejected_admission_clean=true worker_submitted=false");
        }
        try(Scenario a=new Scenario();Scenario b=new Scenario(false)) {
            CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),bStarted=new CountDownLatch(1);
            PackageInstaller installer=a.activity.getPackageManager().getPackageInstaller();installer.failList=true;
            installer.listHook=()->{entered.countDown();try{release.await(3,TimeUnit.SECONDS);}catch(InterruptedException e){throw new AssertionError(e);}};
            b.host.blockerHook=bStarted::countDown;
            AtomicReference<Throwable> errorA=new AtomicReference<>(),errorB=new AtomicReference<>();
            Thread ta=new Thread(()->{try{errorA.set(a.download());}catch(Throwable e){errorA.set(e);}},"activity-A-admission");
            Thread tb=new Thread(()->{try{errorB.set(b.download());}catch(Throwable e){errorB.set(e);}},"activity-B-admission");
            ta.start();check(entered.await(3,TimeUnit.SECONDS),"Activity A holds native admission check");
            tb.start();check(bStarted.await(3,TimeUnit.SECONDS),"Activity B enters admission concurrently");
            a.updater.shutdown();release.countDown();ta.join(3000L);tb.join(3000L);
            check(!ta.isAlive()&&!tb.isAlive()&&errorA.get()==null&&errorB.get()==null,"both admissions return");
            check(!((AtomicBoolean)field(a.updater,"busy")).get()&&"IDLE".equals(gateState(a)),"rejected A cleans only its gate");
            check(b.host.reserved&&"ACTIVE".equals(gateState(b))&&owner().transferInProgress()&&b.executor.tasks.size()==1,"A cancellation/cleanup does not reset B gate or global transfer");
            System.out.println("CODEQL_HOST two Activities: rejected A gate IDLE, accepted B gate ACTIVE/owned");
        }
    }
    private static void acceptedButQueuedShutdown() throws Exception {
        try(Scenario a=new Scenario()) {
            ThreadPoolExecutor real=new ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,new LinkedBlockingQueue<Runnable>());
            Field worker=AppUpdater.class.getDeclaredField("worker");worker.setAccessible(true);worker.set(a.updater,real);
            CountDownLatch occupied=new CountDownLatch(1),release=new CountDownLatch(1);
            real.execute(()->{occupied.countDown();try{release.await();}catch(InterruptedException ignored){Thread.currentThread().interrupt();}});
            try {
                check(occupied.await(3,TimeUnit.SECONDS),"real single-worker executor occupied by a prior task");
                check(a.download()==null&&real.getQueue().size()==1,"real updater task accepted but queued behind prior worker task");
                a.updater.shutdown();
                check(real.awaitTermination(3,TimeUnit.SECONDS)&&real.getQueue().isEmpty(),"shutdownNow terminates worker and drains queued update");
                boolean recovered=!owner().transferInProgress()
                        &&!((AtomicBoolean)field(a.updater,"busy")).get()
                        &&!a.host.reserved
                        &&"IDLE".equals(gateState(a));
                check(recovered==queuedFix,
                        "drained update releases only its own core and UI reservation");
                try(Scenario b=new Scenario(false)) {
                    check(b.download()==null,"new Activity attempts admission after queued drain");
                    check((b.executor.tasks.size()==1)==queuedFix,
                            "new Activity admitted only after drained ownership is released");
                    if(queuedFix) check(b.host.reserved&&owner().transferInProgress(),
                            "new Activity retains its own active transfer ownership");
                    if(queuedFix) {
                        a.updater.shutdown();
                        check(b.host.reserved&&owner().transferInProgress(),
                                "repeated old shutdown cannot reclaim the new Activity's update");
                    }
                }
                System.out.println("QUEUED_SHUTDOWN_HOST recovered="+recovered
                        +" new_activity_admitted="+queuedFix);
            } finally {release.countDown();real.shutdownNow();check(real.awaitTermination(3,TimeUnit.SECONDS),"test executor released");}
        }
    }
    private static void alreadyRunningShutdown() throws Exception {
        try(Scenario a=new Scenario()) {
            a.activity.queueWorkerUi=true;
            ThreadPoolExecutor real=new ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<Runnable>(),task->new Thread(task,"p1d-running-update"));
            Field worker=AppUpdater.class.getDeclaredField("worker");worker.setAccessible(true);worker.set(a.updater,real);
            CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
            a.activity.cacheHook=()->{
                if(!Thread.currentThread().getName().equals("p1d-running-update"))return;
                entered.countDown();boolean interrupted=false;
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
                try {
                    for(;;) {
                        long left=deadline-System.nanoTime();
                        if(left<=0)throw new AssertionError("running updater cleanup barrier timeout");
                        try { if(release.await(left,TimeUnit.NANOSECONDS))break; }
                        catch(InterruptedException error) {interrupted=true;}
                    }
                } finally {if(interrupted)Thread.currentThread().interrupt();}
            };
            try {
                check(a.download()==null&&entered.await(3,TimeUnit.SECONDS),"actual updater worker reaches its failure cleanup barrier");
                a.updater.shutdown();
                check(owner().transferInProgress()&&((AtomicBoolean)field(a.updater,"busy")).get()
                        &&"CANCELLED".equals(gateState(a)),"shutdown cannot steal a running task's finally ownership");
                try(Scenario b=new Scenario(false)) {
                    check(b.download()==null&&b.executor.tasks.isEmpty(),"other Activity rejected while running owner has not finished");
                    release.countDown();
                    check(real.awaitTermination(3,TimeUnit.SECONDS),"running worker finishes after barrier release");
                    check(!owner().transferInProgress()&&!((AtomicBoolean)field(a.updater,"busy")).get()
                            &&"IDLE".equals(gateState(a)),"only running worker finally releases core state");
                    check(b.download()==null&&b.executor.tasks.size()==1&&b.host.reserved,"new Activity admitted after running worker finishes");
                    a.activity.flushUi();
                    check(a.host.releases==1&&b.host.reserved&&owner().transferInProgress(),"old running cleanup releases its Host once and preserves newer owner");
                }
                System.out.println("RUNNING_SHUTDOWN_HOST actual worker keeps ownership until finally; newer Activity admitted afterward; old UI release once");
            } finally {release.countDown();a.activity.cacheHook=null;real.shutdownNow();check(real.awaitTermination(3,TimeUnit.SECONDS),"running test executor released");}
        }
    }
    private static final AtomicInteger forbiddenNetwork=new AtomicInteger();
    public static void main(String[] args) throws Exception {
        root=new File(args[0]);String mode=args.length>1?args[1]:"baseline";
        fixed=!mode.equals("baseline");lease=mode.equals("lease")||mode.equals("full");queuedFix=mode.equals("full");
        java.net.URL.setURLStreamHandlerFactory(protocol->(protocol.equals("https")||protocol.equals("http"))?new java.net.URLStreamHandler(){
            protected java.net.URLConnection openConnection(java.net.URL url)throws java.io.IOException{forbiddenNetwork.incrementAndGet();throw new java.io.IOException("HOST_NETWORK_FORBIDDEN");}
        }:null);
        check(root.mkdirs()||root.isDirectory(),"test-owned root");
        codeqlGateInterleavings();setupFailures();secondaryFailures();acceptedTaskOwnership();staleUiRelease();
        lifecycleAndActivityOwnership();acceptedButQueuedShutdown();alreadyRunningShutdown();duplicatedConfirmations();nativeReplacementControls();
        check(forbiddenNetwork.get()==0,"real worker stopped at invalid-release validation; no network connection attempted");
        System.out.println("REVIEW_HOST assertions="+checks+" scenarios="+cases+" mode="+mode
                +"; selected real worker bodies executed; UI queued; network/device/install NOT executed");
    }
}
