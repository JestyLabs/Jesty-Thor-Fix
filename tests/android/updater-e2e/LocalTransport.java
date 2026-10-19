package com.thor.displaypowertest;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/** Disposable APK only: real Android HTTPS through a non-forwarding loopback fixture. */
final class LocalTransport {
    static void configure(File certificate, int port) throws Exception {
        if (port < 1024 || port > 65535) throw new IllegalArgumentException("Invalid fixture port");
        KeyStore roots = KeyStore.getInstance(KeyStore.getDefaultType());
        roots.load(null, null);
        try (FileInputStream input = new FileInputStream(certificate)) {
            roots.setCertificateEntry("fixture", CertificateFactory.getInstance("X.509").generateCertificate(input));
        }
        TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trust.init(roots);
        SSLContext tls = SSLContext.getInstance("TLS");
        tls.init(null, trust.getTrustManagers(), null);
        // Retain the native handler before registering the test routing adapter.
        // A same-protocol context URL inherits that handler, without private APIs.
        URL nativeContext = new URL("https://github.com/");
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("10.0.2.2", port));
        URL.setURLStreamHandlerFactory(protocol -> "https".equals(protocol) ? new URLStreamHandler() {
            @Override protected URLConnection openConnection(URL requested) throws IOException {
                HttpsURLConnection connection = (HttpsURLConnection)
                        new URL(nativeContext, requested.toExternalForm()).openConnection(proxy);
                connection.setSSLSocketFactory(tls.getSocketFactory());
                // Explicit per-connection proxy/trust; keep the native hostname verifier.
                return connection;
            }
        } : null);
    }
}
