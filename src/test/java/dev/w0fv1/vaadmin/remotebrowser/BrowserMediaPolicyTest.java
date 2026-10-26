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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Timeout(120)
final class BrowserMediaPolicyTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path profileRoot = TestProfileRoot.create("media");

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startFixture() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::serveFixture);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopFixture() {
        if (server != null) server.stop(0);
    }

    @Test
    void blockedPolicyKeepsMediaPaused() {
        assertThat(playbackTitle(BrowserMediaPolicy.BLOCK)).isEqualTo("paused:true");
    }

    @Test
    void allowedPolicyLetsMediaStart() {
        assertThat(playbackTitle(BrowserMediaPolicy.ALLOW)).isEqualTo("paused:false");
    }

    private String playbackTitle(BrowserMediaPolicy mediaPolicy) {
        BrowserSessionManager manager = new BrowserSessionManager(
                new BrowserRuntime(properties(mediaPolicy), MAPPER),
                properties(mediaPolicy),
                new BrowserEnvironmentStore(properties(mediaPolicy), MAPPER),
                MAPPER);
        try (RemoteBrowserSession session = manager.open(BrowserProfileId.DEFAULT, baseUrl)) {
            BrowserSession browser = session.session();
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                    assertThat(browser.browserInfo().activeTitle()).startsWith("paused:"));
            return browser.browserInfo().activeTitle();
        } finally {
            manager.shutdown();
        }
    }

    private BrowserRuntimeProperties properties(BrowserMediaPolicy mediaPolicy) {
        return new BrowserRuntimeProperties(
                BrowserMode.HEADLESS,
                BrowserChannel.CHROME,
                profileRoot,
                null,
                null,
                null,
                null,
                null,
                mediaPolicy);
    }

    private void serveFixture(HttpExchange exchange) throws IOException {
        byte[] body = """
                <!doctype html>
                <html>
                <head><meta charset="utf-8"><title>media</title></head>
                <body>
                <script>
                const media = new Audio();
                const pending = media.play();
                const resolves = pending !== undefined && typeof pending.then === 'function';
                document.title = `paused:${media.paused}`;
                if (!resolves) document.title = 'unsupported';
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
