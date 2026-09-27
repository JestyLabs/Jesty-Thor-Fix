import com.thor.displaypowertest.DaemonState;
import com.thor.displaypowertest.DaemonWatchThread;
import com.thor.displaypowertest.DisplayEventManager;
import com.thor.displaypowertest.DisplayHardware;
import com.thor.displaypowertest.Telemetry;
import com.thor.displaypowertest.WakeRepairScheduler;

import android.util.Log;

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public final class D {
    public static void main(String[] args) throws Exception {
        boolean enabled = args.length == 0 || !"0".equals(args[0]);
        DaemonState.setEnabled(enabled);
        new DaemonWatchThread().start();
        DisplayEventManager.register();
        ServerSocket server = new ServerSocket(3804, 4, InetAddress.getByName("127.0.0.1"));
        Log.d("ThorDisplayDaemon", "READY 1.0.0 enabled=" + enabled);
        while (true) {
            Socket socket = server.accept();
            try {
                int command = socket.getInputStream().read();
                String response = handle((char) command);
                OutputStream output = socket.getOutputStream();
                output.write((response + "\n").getBytes(StandardCharsets.UTF_8));
                output.flush();
            } catch (Throwable error) {
                Log.e("ThorDisplayDaemon", "command failed", error);
            } finally {
                socket.close();
            }
        }
    }

    private static String handle(char command) {
        switch (command) {
            case '0':
                WakeRepairScheduler.cancel();
                return DisplayHardware.apply(false, "LEGACY") ? "ok=1" : "ok=0;error=OFF_FAILED";
            case '1':
                WakeRepairScheduler.cancel();
                return DisplayHardware.apply(true, "LEGACY") ? "ok=1" : "ok=0;error=ON_FAILED";
            case 'E':
                DaemonState.setEnabled(true);
                WakeRepairScheduler.cancel();
                boolean top = "1".equals(DaemonState.getMode());
                boolean enabledOk = DisplayHardware.apply(!top, "ENABLE_RECONCILE");
                return enabledOk ? "ok=1;fix=1" : "ok=0;error=ENABLE_FAILED";
            case 'N':
                DaemonState.setEnabled(false);
                WakeRepairScheduler.cancel();
                final boolean nativeOk = DisplayHardware.apply(true, "NATIVE_IMMEDIATE");
                new Thread(new Runnable() {
                    @Override public void run() {
                        try { Thread.sleep(350L); } catch (InterruptedException ignored) {}
                        if (!DaemonState.isEnabled()) DisplayHardware.apply(true, "NATIVE_FINAL");
                    }
                }, "native-final").start();
                return nativeOk ? "ok=1;fix=0" : "ok=0;error=NATIVE_FAILED";
            case 'Q': return DaemonState.snapshot();
            case 'V': return Telemetry.verifyDrm();
            default: return "ok=0;error=UNKNOWN_COMMAND";
        }
    }
}
