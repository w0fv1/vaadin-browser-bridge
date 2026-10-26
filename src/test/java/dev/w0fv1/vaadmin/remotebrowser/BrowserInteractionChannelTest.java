package dev.w0fv1.vaadmin.remotebrowser;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Timeout(120)
final class BrowserInteractionChannelTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path profileRoot = TestProfileRoot.create("channels");

    private HttpServer server;
    private String baseUrl;
    private BrowserSessionManager manager;

    @BeforeEach
    void startFixture() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::serveFixture);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        BrowserRuntimeProperties properties = new BrowserRuntimeProperties(
                BrowserMode.HEADLESS, BrowserChannel.CHROME, profileRoot, null);
        manager = new BrowserSessionManager(
                new BrowserRuntime(properties, MAPPER),
                properties,
                new BrowserEnvironmentStore(properties, MAPPER),
                MAPPER);
    }

    @AfterEach
    void shutdown() {
        if (manager != null) manager.shutdown();
        if (server != null) server.stop(0);
    }

    @Test
    void reportsCursorStyleUnderPointer() {
        try (RemoteBrowserSession session = manager.open(BrowserProfileId.DEFAULT, baseUrl)) {
            BrowserSession browser = session.session();
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                browser.move(new BrowserInput("move", 1, 120, 120, BrowserMouseButton.NONE, 0, 1, Set.of()));
                assertThat(browser.cursor()).isEqualTo("pointer");
            });

            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                browser.move(new BrowserInput("move", 2, 900, 640, BrowserMouseButton.NONE, 0, 1, Set.of()));
                assertThat(browser.cursor()).isEqualTo("auto");
            });
        }
    }

    @Test
    void exposesClipboardTextOfActiveTab() {
        try (RemoteBrowserSession session = manager.open(BrowserProfileId.DEFAULT, baseUrl)) {
            BrowserSession browser = session.session();
            await().atMost(Duration.ofSeconds(30))
                    .untilAsserted(() -> assertThat(browser.clipboardText()).isEqualTo("vaadmin-clipboard"));
        }
    }

    private void serveFixture(HttpExchange exchange) throws IOException {
        byte[] body = """
                <!doctype html>
                <html>
                <head><meta charset="utf-8"><title>channels</title></head>
                <body style="margin:0;width:100vw;height:100vh;cursor:auto">
                <div style="position:absolute;left:0;top:0;width:300px;height:300px;cursor:pointer">hit</div>
                <script>
                navigator.clipboard.writeText('vaadmin-clipboard').catch(() => {})
                </script>
                </body>
                </html>
                """.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
