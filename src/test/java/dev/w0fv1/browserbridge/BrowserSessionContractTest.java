package dev.w0fv1.browserbridge;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

final class BrowserSessionContractTest {
    @TempDir
    Path profileRoot;

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

    @ParameterizedTest
    @EnumSource(BrowserMode.class)
    void preservesViewportInputTabsAndPersistentStorage(BrowserMode mode) throws Exception {
        BrowserRuntimeProperties properties = new BrowserRuntimeProperties(
                mode, BrowserChannel.CHROME, profileRoot);
        String profileId = "contract-" + mode.name().toLowerCase();

        try (BrowserRuntime runtime = new BrowserRuntime(properties)) {
            BrowserSessionManager manager = new BrowserSessionManager(runtime, properties);
            try {
                BrowserSessionManager.BrowserSession session = manager.create(profileId, baseUrl);
                session.resizeViewport(1000, 600);
                awaitTitle(session, "state:1000x600:none");

                session.down(new BrowserInput("down", 1, 500, 300, 0, 1, 1, System.currentTimeMillis()));
                session.up(new BrowserInput("up", 2, 500, 300, 0, 0, 1, System.currentTimeMillis()));
                awaitTitle(session, "click:500x300:1000x600");

                byte[] frame = awaitFrame(session);
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(frame));
                assertThat(image).isNotNull();
                assertThat(image.getWidth() * 600).isEqualTo(image.getHeight() * 1000);

                session.openTab(baseUrl + "/second");
                await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
                    session.capture();
                    BrowserSessionManager.TabsState state = session.state();
                    assertThat(state.tabs()).hasSize(2);
                    assertThat(state.tabs().get(1).url()).endsWith("/second");
                    assertThat(state.activeTabId()).isEqualTo(state.tabs().get(1).id());
                });

                BrowserSessionManager.TabsState state = session.state();
                session.closeTab(state.activeTabId());
                await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
                    session.capture();
                    assertThat(session.state().tabs()).hasSize(1);
                });

                BrowserSessionManager.BrowserInfo info = session.browserInfo();
                assertThat(info.profileId()).isEqualTo(profileId);
                assertThat(info.headless()).isEqualTo(mode.headless());
                assertThat(info.channel()).isEqualTo("chrome");
                assertThat(info.browserVersion()).isNotBlank();

                manager.close(session.id());
                AtomicReference<BrowserSessionManager.BrowserSession> reopened = new AtomicReference<>();
                await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
                    if (reopened.get() == null) {
                        reopened.set(manager.create(profileId, baseUrl));
                    }
                });
                try {
                    reopened.get().resizeViewport(1000, 600);
                    awaitTitle(reopened.get(), "state:1000x600:500x300");
                } finally {
                    manager.close(reopened.get().id());
                }
            } finally {
                manager.shutdown();
            }
        }
    }

    private void awaitTitle(BrowserSessionManager.BrowserSession session, String expected) {
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(session.capture()).isNotNull();
            assertThat(session.browserInfo().activeTitle()).isEqualTo(expected);
        });
    }

    private byte[] awaitFrame(BrowserSessionManager.BrowserSession session) {
        AtomicReference<byte[]> frame = new AtomicReference<>();
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            frame.set(session.capture());
            assertThat(frame.get()).isNotNull();
        });
        return frame.get();
    }

    private void serveFixture(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        byte[] body;
        if ("/second".equals(path)) {
            body = "<!doctype html><html><head><title>second</title></head><body>second</body></html>"
                    .getBytes(StandardCharsets.UTF_8);
        } else {
            body = """
                    <!doctype html>
                    <html>
                    <head><meta charset="utf-8"><title>fixture</title></head>
                    <body style="margin:0;width:100vw;height:100vh;overflow:hidden">
                    <script>
                    const viewport = () => `${innerWidth}x${innerHeight}`;
                    const persisted = () => localStorage.getItem('persist') || 'none';
                    const render = () => { document.title = `state:${viewport()}:${persisted()}`; };
                    window.addEventListener('resize', render);
                    window.addEventListener('pointerdown', event => {
                      const position = `${Math.round(event.clientX)}x${Math.round(event.clientY)}`;
                      localStorage.setItem('persist', position);
                      document.title = `click:${position}:${viewport()}`;
                    });
                    render();
                    </script>
                    </body>
                    </html>
                    """.getBytes(StandardCharsets.UTF_8);
        }
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
