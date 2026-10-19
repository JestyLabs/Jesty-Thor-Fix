package android.system;
public final class StructStat {
    public final int st_uid, st_gid, st_mode;
    public StructStat(int uid, int gid, int mode) {
        st_uid = uid; st_gid = gid; st_mode = mode;
    }
}
