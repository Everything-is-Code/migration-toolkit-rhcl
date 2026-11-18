package com.redhat.migrationtoolkit.rhcl.controller;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiWaitTest {

    private final int originalAttempts = ApplyController.openApiAttempts;
    private final Duration originalInterval = ApplyController.openApiInterval;

    @AfterEach
    void restoreWait() {
        ApplyController.openApiAttempts = originalAttempts;
        ApplyController.openApiInterval = originalInterval;
    }

    @Test
    void waitForOpenApi_ignoresBlankUrl() {
        ApplyController.openApiAttempts = 1;
        new ApplyController().waitForOpenApi(null);
        new ApplyController().waitForOpenApi("  ");
    }

    @Test
    void waitForOpenApi_retriesUntilSuccess() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/openapi", exchange -> {
            int status = hits.incrementAndGet() == 1 ? 503 : 200;
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        ApplyController.openApiAttempts = 3;
        ApplyController.openApiInterval = Duration.ofMillis(1);
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/openapi";
            new ApplyController().waitForOpenApi(url);
            assertTrue(hits.get() >= 2);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void waitForOpenApi_appliesAfterConnectionFailure() {
        ApplyController.openApiAttempts = 1;
        ApplyController.openApiInterval = Duration.ofMillis(1);
        new ApplyController().waitForOpenApi("http://127.0.0.1:1/q/openapi");
    }

    @Test
    void waitForOpenApi_stopsWhenInterrupted() throws Exception {
        ApplyController.openApiAttempts = 2;
        ApplyController.openApiInterval = Duration.ofSeconds(30);
        ApplyController controller = new ApplyController();
        Thread worker = new Thread(() -> controller.waitForOpenApi("http://127.0.0.1:1/q/openapi"));
        worker.start();
        long deadline = System.currentTimeMillis() + 4_000;
        while (worker.isAlive() && System.currentTimeMillis() < deadline) {
            worker.interrupt();
            worker.join(50);
        }
        assertFalse(worker.isAlive());
    }

    @Test
    void waitForOpenApi_acceptsSelfSignedHttps() throws Exception {
        Path dir = Files.createTempDirectory("openapi-tls");
        Path store = dir.resolve("keystore.p12");
        String keytoolName = System.getProperty("os.name").toLowerCase().contains("win") ? "keytool.exe" : "keytool";
        Path keytool = Path.of(System.getProperty("java.home"), "bin", keytoolName);
        Process process = new ProcessBuilder(
                keytool.toString(),
                "-genkeypair", "-alias", "test", "-keyalg", "RSA", "-keysize", "2048",
                "-storetype", "PKCS12", "-keystore", store.toString(), "-storepass", "changeit",
                "-keypass", "changeit", "-dname", "CN=127.0.0.1",
                "-ext", "SAN=ip:127.0.0.1", "-validity", "1", "-noprompt")
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes());
        assertEquals(0, process.waitFor(), output);

        KeyStore keys = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(store)) {
            keys.load(in, "changeit".toCharArray());
        }
        KeyManagerFactory factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        factory.init(keys, "changeit".toCharArray());
        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(factory.getKeyManagers(), null, null);

        HttpsServer server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(ssl));
        server.createContext("/openapi", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        ApplyController.openApiAttempts = 1;
        ApplyController.openApiInterval = Duration.ofMillis(1);
        try {
            String url = "https://127.0.0.1:" + server.getAddress().getPort() + "/openapi";
            new ApplyController().waitForOpenApi(url);
        } finally {
            server.stop(0);
        }
    }
}
