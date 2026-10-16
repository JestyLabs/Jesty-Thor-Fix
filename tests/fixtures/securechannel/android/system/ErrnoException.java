package android.system;
public class ErrnoException extends Exception {
    public final int errno;
    public ErrnoException(String functionName, int errno) {
        super(functionName + ": synthetic errno=" + errno);
        this.errno = errno;
    }
}
