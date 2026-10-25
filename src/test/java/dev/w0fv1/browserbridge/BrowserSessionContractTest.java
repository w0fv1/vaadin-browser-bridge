package dev.w0fv1.browserbridge;

import com.microsoft.playwright.options.ColorScheme;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Timeout(60)
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

    @Test
    void headlessPreservesViewportInputTabsAndPersistentStorage() throws Exception {
        verifyContract(BrowserMode.HEADLESS);
    }

    @Test
    @Tag("headed")
    void headedPreservesViewportInputTabsAndPersistentStorage() throws Exception {
        verifyContract(BrowserMode.HEADED);
    }

    @Test
    void closesWhileCaptureIsRunningWithoutHanging() throws Exception {
        BrowserRuntimeProperties properties = new BrowserRuntimeProperties(
                BrowserMode.HEADLESS, BrowserChannel.CHROME, profileRoot);

        try (BrowserRuntime runtime = new BrowserRuntime(properties)) {
            BrowserSessionManager manager = manager(runtime, properties);
            try {
                BrowserSessionLease lease = manager.open("close-race", baseUrl);
                BrowserSession session = lease.session();
                session.resizeViewport(800, 600);
                awaitImage(session);

                CountDownLatch start = new CountDownLatch(1);
                ExecutorService workers = Executors.newFixedThreadPool(2);
                try {
                    Future<?> captures = workers.submit(() -> {
                        awaitLatch(start);
                        for (int index = 0; index < 100; index++) {
                            session.capture();
                        }
                        return null;
                    });
                    Future<?> closing = workers.submit(() -> {
                        awaitLatch(start);
                        lease.close();
                        return null;
                    });
                    start.countDown();

                    closing.get(15, TimeUnit.SECONDS);
                    captures.get(15, TimeUnit.SECONDS);
                    assertThat(session.capture()).isNull();
                    assertThat(session.browserInfo().contextOpen()).isFalse();
                } finally {
                    workers.shutdownNow();
                    lease.close();
                }
            } finally {
                manager.shutdown();
            }
        }
    }

    @Test
    void managedHeadlessEnvironmentPreservesViewportAndInput() throws Exception {
        verifyManagedEnvironment(BrowserMode.HEADLESS);
    }

    @Test
    @Tag("headed")
    void managedHeadedEnvironmentPreservesViewportAndInput() throws Exception {
        verifyManagedEnvironment(BrowserMode.HEADED);
    }

    private void verifyManagedEnvironment(BrowserMode mode) throws Exception {
        BrowserRuntimeProperties properties = new BrowserRuntimeProperties(
                mode, BrowserChannel.CHROME, profileRoot);
        BrowserEnvironmentStore environments = new BrowserEnvironmentStore(properties, new ObjectMapper());
        BrowserProfileId profileId = new BrowserProfileId("managed-" + mode.name().toLowerCase());
        environments.save(BrowserEnvironmentProfile.managedProfile(
                profileId,
                Locale.forLanguageTag("fr-CA"),
                ZoneId.of("Asia/Tokyo"),
                ColorScheme.DARK,
                1.5,
                Set.of(BrowserPermission.GEOLOCATION)));

        try (BrowserRuntime runtime = new BrowserRuntime(properties)) {
            BrowserSessionManager manager = new BrowserSessionManager(runtime, properties, environments);
            BrowserSessionLease lease = null;
            try {
                lease = manager.open(profileId.value(), baseUrl);
                BrowserSession session = lease.session();
                session.resizeViewport(1000, 600);
                awaitTitle(session, "state:1000x600:none");
                session.down(new BrowserInput("down", 1, 500, 300, 0, 1, 1, System.currentTimeMillis()));
                session.up(new BrowserInput("up", 2, 500, 300, 0, 0, 1, System.currentTimeMillis()));
                awaitTitle(session, "click:500x300:1000x600");

                BufferedImage image = awaitImage(session);
                assertThat(image.getWidth()).isEqualTo(1500);
                assertThat(image.getHeight()).isEqualTo(900);
                assertThat(session.browserInfo().environmentMode()).isEqualTo(BrowserEnvironmentMode.MANAGED);
            } finally {
                if (lease != null) lease.close();
                manager.shutdown();
            }
        }
    }

    private void verifyContract(BrowserMode mode) throws Exception {
        BrowserRuntimeProperties properties = new BrowserRuntimeProperties(
                mode, BrowserChannel.CHROME, profileRoot);
        String profileId = "contract-" + mode.name().toLowerCase();

        try (BrowserRuntime runtime = new BrowserRuntime(properties)) {
            BrowserSessionManager manager = manager(runtime, properties);
            BrowserSessionLease lease = null;
            try {
                lease = manager.open(profileId, baseUrl);
                BrowserSession session = lease.session();
                session.resizeViewport(1000, 600);
                awaitTitle(session, "state:1000x600:none");

                session.down(new BrowserInput("down", 1, 500, 300, 0, 1, 1, System.currentTimeMillis()));
                session.up(new BrowserInput("up", 2, 500, 300, 0, 0, 1, System.currentTimeMillis()));
                awaitTitle(session, "click:500x300:1000x600");

                BufferedImage image = awaitImage(session);
                assertFrameScale(image, mode);
                int pixel = image.getRGB(10, 10);
                assertThat(red(pixel)).isGreaterThan(150);
                assertThat(green(pixel)).isLessThan(90);
                assertThat(blue(pixel)).isLessThan(90);

                session.openTab(baseUrl + "/second");
                await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
                    session.capture();
                    BrowserSession.TabsState state = session.state();
                    assertThat(state.tabs()).hasSize(2);
                    assertThat(state.tabs().get(1).url()).endsWith("/second");
                    assertThat(state.activeTabId()).isEqualTo(state.tabs().get(1).id());
                });

                BufferedImage secondImage = awaitImage(session);
                assertFrameScale(secondImage, mode);
                int secondPixel = secondImage.getRGB(500, 300);
                assertThat(green(secondPixel)).isGreaterThan(80);
                assertThat(red(secondPixel)).isLessThan(90);
                assertThat(blue(secondPixel)).isLessThan(90);

                BrowserSession.TabsState state = session.state();
                session.closeTab(state.activeTabId());
                await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
                    session.capture();
                    assertThat(session.state().tabs()).hasSize(1);
                });

                BrowserSession.BrowserInfo info = session.browserInfo();
                assertThat(info.profileId()).isEqualTo(profileId);
                assertThat(info.headless()).isEqualTo(mode.headless());
                assertThat(info.channel()).isEqualTo("chrome");
                assertThat(info.browserVersion()).isNotBlank();
                assertThat(info.contextOpen()).isTrue();
                assertThat(info.lastFailure()).isNull();

                lease.close();
                assertThat(session.browserInfo().contextOpen()).isFalse();

                AtomicReference<BrowserSessionLease> reopened = new AtomicReference<>();
                await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
                    if (reopened.get() == null) {
                        reopened.set(manager.open(profileId, baseUrl));
                    }
                });
                try {
                    reopened.get().session().resizeViewport(1000, 600);
                    awaitTitle(reopened.get().session(), "state:1000x600:500x300");
                } finally {
                    reopened.get().close();
                }
            } finally {
                if (lease != null) lease.close();
                manager.shutdown();
            }
        }
    }

    private static BrowserSessionManager manager(
            BrowserRuntime runtime,
            BrowserRuntimeProperties properties) {
        return new BrowserSessionManager(
                runtime,
                properties,
                new BrowserEnvironmentStore(properties, new ObjectMapper()));
    }

    private static void awaitLatch(CountDownLatch start) {        try {
            start.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void awaitTitle(BrowserSession session, String expected) {
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(session.capture()).isNotNull();
            assertThat(session.browserInfo().activeTitle()).isEqualTo(expected);
        });
    }

    private BufferedImage awaitImage(BrowserSession session) throws IOException {
        AtomicReference<BufferedImage> image = new AtomicReference<>();
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            byte[] frame = session.capture();
            assertThat(frame).isNotNull();
            image.set(ImageIO.read(new ByteArrayInputStream(frame)));
            assertThat(image.get()).isNotNull();
        });
        return image.get();
    }

    private static void assertFrameScale(BufferedImage image, BrowserMode mode) {
        int scaleX = image.getWidth() / 1000;
        int scaleY = image.getHeight() / 600;
        assertThat(scaleX).isGreaterThanOrEqualTo(1);
        assertThat(scaleX).isEqualTo(scaleY);
        assertThat(image.getWidth()).isEqualTo(1000 * scaleX);
        assertThat(image.getHeight()).isEqualTo(600 * scaleX);
        if (mode.headless()) {
            assertThat(scaleX).isEqualTo(1);
        }
    }

    private static int red(int pixel) {
        return (pixel >> 16) & 0xff;
    }    private static int green(int pixel) {
        return (pixel >> 8) & 0xff;
    }

    private static int blue(int pixel) {
        return pixel & 0xff;
    }

    private void serveFixture(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        byte[] body;
        if ("/second".equals(path)) {
            body = """
                    <!doctype html>
                    <html>
                    <head><meta charset="utf-8"><title>second</title></head>
                    <body style="margin:0;background:#1f7a1f">second</body>
                    </html>
                    """.getBytes(StandardCharsets.UTF_8);
        } else {
            body = """
                    <!doctype html>
                    <html>
                    <head><meta charset="utf-8"><title>fixture</title></head>
                    <body style="margin:0;width:100vw;height:100vh;overflow:hidden;background:#d22a2a">
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
