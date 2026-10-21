package com.thor.displaypowertest;

import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.SystemClock;
import android.system.Os;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/** Emulator-only entry point. No daemon command handler or vendor classes. */
public final class NativeSecureChannelFixture {
    private static final String SENTINEL = "FIXTURE_EXCLUSIVE_OWNER\n";

    private static final class ContractFailure extends Exception {
        final String code;
        ContractFailure(String code) { this.code = code; }
    }

    private static File base() {
        return new File(SecureChannel.path()).getParentFile().getParentFile().getParentFile();
    }

    private static void mark(String name, String text) throws IOException {
        try (FileOutputStream file = new FileOutputStream(new File(base(), name))) {
            file.write(text.getBytes(StandardCharsets.US_ASCII));
        }
    }

    private static void hold(boolean app, int uid) throws Exception {
        if (app) { Os.setgid(uid); Os.setuid(uid); }
        if (Os.getuid() != (app ? uid : 0)) throw new ContractFailure("HOLDER_UID");
        final LocalServerSocket server = SecureChannel.listen();
        if (!app && !DaemonLaunchModel.validBootId(SecureChannel.currentBootId())) {
            throw new ContractFailure("BOOT_STAMP_UNAVAILABLE");
        }
        // Closing another FD for this inode could release process-associated
        // POSIX locks. Write through the original locked descriptor instead.
        Field field = SecureChannel.class.getDeclaredField("instanceLockStream");
        field.setAccessible(true);
        FileOutputStream locked = (FileOutputStream) field.get(null);
        locked.getChannel().truncate(0L);
        locked.getChannel().write(ByteBuffer.wrap(SENTINEL.getBytes(StandardCharsets.US_ASCII)), 0L);
        locked.getChannel().force(false);
        Thread reader = new Thread(() -> {
            try {
                while (true) {
                    try (LocalSocket peer = server.accept()) {
                        peer.setSoTimeout(1500);
                        int value = peer.getInputStream().read();
                        mark("bytes", value == -1 ? "0" : "NONZERO");
                    }
                }
            } catch (Throwable ignored) {
                try { mark("bytes", "READ_FAILED"); } catch (IOException unavailable) { }
            }
        }, "native-fixture-reader");
        reader.setDaemon(true);
        reader.start();
        mark("ready", app ? "APP_HOLDER" : "ROOT_HOLDER");
        long deadline = SystemClock.elapsedRealtime() + 45000L;
        while (!new File(base(), "stop").exists()) {
            if (SystemClock.elapsedRealtime() >= deadline) throw new ContractFailure("HOLDER_DEADLINE");
            Thread.sleep(25L);
        }
        // Process exit releases the native lock; no daemon signal/kill helper.
    }

    private static void run(String mode, int uid) throws Exception {
        if (mode.equals("hold-root") || mode.equals("hold-app")) {
            hold(mode.equals("hold-app"), uid);
        } else if (mode.equals("contend")) {
            try {
                SecureChannel.listen();
                throw new ContractFailure("CONTENDER_ACQUIRED");
            } catch (IOException failure) {
                if (!"Daemon instance already locked".equals(failure.getMessage())) throw failure;
                System.out.println("LOCK_DENIED");
            }
        } else if (mode.equals("connect-root")) {
            try (LocalSocket peer = SecureChannel.connect(1500)) {
                System.out.println("ROOT_PEER_ACCEPTED");
            }
        } else if (mode.equals("reject-app")) {
            try (LocalSocket peer = SecureChannel.connect(1500)) {
                throw new ContractFailure("UNTRUSTED_PEER_ACCEPTED");
            } catch (IOException failure) {
                if (!"Untrusted daemon UID".equals(failure.getMessage())) throw failure;
                System.out.println("UNTRUSTED_PEER_REJECTED");
            }
        } else if (mode.equals("recover")) {
            SecureChannel.listen();
            System.out.println("STALE_RECOVERED");
        } else {
            throw new ContractFailure("MODE");
        }
    }

    public static void main(String[] args) {
        int result = 0;
        try {
            if (args.length != 2 || !args[1].matches("[1-9][0-9]{4,8}")) {
                throw new ContractFailure("ARGS");
            }
            run(args[0], Integer.parseInt(args[1]));
        } catch (ContractFailure failure) {
            System.out.println("CONTRACT|" + failure.code);
            result = 2;
        } catch (Throwable failure) {
            System.out.println("FIXTURE_ERROR|" + failure.getClass().getSimpleName());
            result = 3;
        }
        System.exit(result);
    }
}
