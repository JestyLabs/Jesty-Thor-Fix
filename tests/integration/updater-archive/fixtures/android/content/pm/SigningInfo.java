package android.content.pm;

/** Returns supplied Android metadata shapes; trust policy remains production code. */
public class SigningInfo {
    private final boolean multiple;
    private final Signature[] contents;
    private final Signature[] history;
    public SigningInfo(boolean multiple, Signature[] contents, Signature[] history) {
        this.multiple = multiple;
        this.contents = contents;
        this.history = history;
    }
    public boolean hasMultipleSigners() { return multiple; }
    public Signature[] getApkContentsSigners() { return contents; }
    public Signature[] getSigningCertificateHistory() { return history; }
}
