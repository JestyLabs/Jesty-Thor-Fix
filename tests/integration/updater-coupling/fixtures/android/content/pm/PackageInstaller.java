package android.content.pm;
import java.util.ArrayList;
import java.util.List;
/** Simulated host-only installer. Never installs, signs or accesses Android. */
public class PackageInstaller {
    public static final String EXTRA_STATUS="android.content.pm.extra.STATUS";
    public static final String EXTRA_SESSION_ID="android.content.pm.extra.SESSION_ID";
    public static final String EXTRA_STATUS_MESSAGE="android.content.pm.extra.STATUS_MESSAGE";
    public static final int STATUS_PENDING_USER_ACTION=-1, STATUS_SUCCESS=0, STATUS_FAILURE=1, STATUS_FAILURE_ABORTED=3;
    public final List<Integer> abandoned=new ArrayList<>();
    public final java.util.Map<Integer,Session> sessions=new java.util.HashMap<>();
    public int nextId=9;
    public boolean failFsync,failCommit,failAbandon,failOpen;
    public int commits;
    public void abandonSession(int id) {
        abandoned.add(id);
        if(failAbandon)throw new IllegalStateException("host abandon failure");
        sessions.remove(id);
    }
    public int createSession(SessionParams params) {
        int id=nextId++;sessions.put(id,new Session());return id;
    }
    public Session openSession(int id) throws java.io.IOException {
        if(failOpen)throw new java.io.IOException("host open failure");
        return sessions.get(id);
    }
    public List<SessionInfo> getMySessions() {
        List<SessionInfo> values=new ArrayList<>();
        for(Integer id:sessions.keySet())values.add(new SessionInfo(id));
        return values;
    }
    public static class SessionInfo {
        private final int id;
        public SessionInfo(int id){this.id=id;}
        public int getSessionId(){return id;}
    }
    public static class SessionParams {
        public static final int MODE_FULL_INSTALL=1,USER_ACTION_REQUIRED=1;
        public SessionParams(int mode) { }
        public void setAppPackageName(String name) { }
        public void setSize(long bytes) { }
        public void setRequireUserAction(int required) { }
    }
    public class Session implements AutoCloseable {
        public java.io.OutputStream openWrite(String name,long offset,long size) { return new java.io.ByteArrayOutputStream(); }
        public void fsync(java.io.OutputStream stream) throws java.io.IOException {
            if(failFsync)throw new java.io.IOException("host fsync failure");
        }
        public void commit(android.content.IntentSender sender) throws java.io.IOException {
            if(failCommit)throw new java.io.IOException("host commit failure");
            commits++;
        }
        public void close() { }
    }
}
