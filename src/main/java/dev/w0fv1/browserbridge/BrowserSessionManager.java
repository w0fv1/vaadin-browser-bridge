package dev.w0fv1.browserbridge;

import com.google.gson.JsonObject;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.CDPSession;
import com.microsoft.playwright.Mouse;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.MouseButton;
import com.microsoft.playwright.options.WaitUntilState;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Base64;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Component
public final class BrowserSessionManager {
    private static final Logger log = LoggerFactory.getLogger(BrowserSessionManager.class);

    static final int DEFAULT_VIEWPORT_WIDTH = 1280;
    static final int DEFAULT_VIEWPORT_HEIGHT = 720;
    static final int DEFAULT_QUALITY = 75;
    static final int DEFAULT_INTERVAL_MILLIS = 40;
    static final boolean DEFAULT_NETWORK_ENABLED = false;
    static final int DEFAULT_BANDWIDTH_BYTES_PER_SECOND = 500 * 1024;
    static final int DEFAULT_LATENCY_MILLIS = 0;
    private static final int MIN_VIEWPORT_WIDTH = 320;
    private static final int MAX_VIEWPORT_WIDTH = 3840;
    private static final int MIN_VIEWPORT_HEIGHT = 240;
    private static final int MAX_VIEWPORT_HEIGHT = 2160;
    private static final int MAX_PENDING_OPERATIONS = 128;
    private static final int MAX_CAPTURE_QUEUE = 8;
    private static final int MIN_BANDWIDTH_BYTES_PER_SECOND = 32 * 1024;
    private static final int MAX_BANDWIDTH_BYTES_PER_SECOND = 20 * 1024 * 1024;
    private static final int MIN_LATENCY_MILLIS = 0;
    private static final int MAX_LATENCY_MILLIS = 1000;
    private static final int MIN_QUALITY = 20;
    private static final int MAX_QUALITY = 90;
    private static final int MIN_INTERVAL_MILLIS = 40;
    private static final int MAX_INTERVAL_MILLIS = 2000;
    private static final double NAVIGATION_TIMEOUT_MILLIS = 10000;
    private static final String DEFAULT_PROFILE = "default";

    private final BrowserRuntime runtime;
    private final BrowserRuntimeProperties properties;
    private final BrowserProfileRegistry profiles;
    private final String playwrightVersion;
    private final Map<String, BrowserSession> sessions = new ConcurrentHashMap<>();

    public BrowserSessionManager(BrowserRuntime runtime, BrowserRuntimeProperties properties) {
        this.runtime = runtime;
        this.properties = properties;
        this.profiles = new BrowserProfileRegistry(properties.profileRoot());
        this.playwrightVersion = Optional.ofNullable(Playwright.class.getPackage().getImplementationVersion())
                .orElse("unknown");
    }

    public BrowserSession create(String url) {
        return create(DEFAULT_PROFILE, url);
    }

    public BrowserSession create(String profileId, String url) {
        BrowserProfileLease lease = profiles.acquire(profileId);
        BrowserContext context;
        try {
            context = runtime.launch(lease.directory());
        } catch (RuntimeException failure) {
            lease.close();
            throw new IllegalStateException("无法启动真实 Chrome，配置 " + profileId, failure);
        }
        String browserVersion = Optional.ofNullable(context.browser())
                .map(browser -> browser.version())
                .orElse("unknown");
        BrowserSession session = new BrowserSession(
                UUID.randomUUID().toString(),
                context,
                browserVersion,
                playwrightVersion,
                properties.mode(),
                properties.channel(),
                lease);
        try {
            session.initialize(url);
        } catch (RuntimeException failure) {
            session.close();
            throw failure;
        }
        sessions.put(session.id(), session);
        return session;
    }

    public BrowserSession require(String id) {
        BrowserSession session = sessions.get(id);
        if (session == null) throw new IllegalArgumentException("浏览器会话不存在");
        return session;
    }

    public void close(String id) {
        BrowserSession session = sessions.remove(id);
        if (session != null) session.close();
    }

    @PreDestroy
    void shutdown() {
        sessions.values().forEach(BrowserSession::close);
        sessions.clear();
    }

    public record TabState(String id, String title, String url, boolean loading, String error) {
    }

    public record TabsState(
            long version,
            String activeTabId,
            List<TabState> tabs,
            int quality,
            int intervalMillis,
            boolean networkEnabled,
            int bandwidthBytesPerSecond,
            int latencyMillis) {
    }

    public record BrowserInfo(
            String engine,
            String browserVersion,
            String playwrightVersion,
            String profileId,
            int tabCount,
            String activeTitle,
            String activeUrl,
            boolean contextOpen,
            boolean headless,
            String channel,
            InputInfo input) {
    }

    public record InputInfo(
            long lastReceivedSequence,
            long lastAppliedSequence,
            int pendingOperations,
            long rejectedOperations) {
    }

    public static final class BrowserSession {
        private static final Set<String> NAMED_KEYS = Set.of(
                "Enter", "Tab", "Backspace", "Delete", "Escape", "Home", "End", "PageUp", "PageDown",
                "ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight");

        private final String id;
        private final String profileId;
        private final BrowserContext context;
        private final String browserVersion;
        private final String playwrightVersion;
        private final BrowserMode mode;
        private final BrowserChannel channel;
        private final BrowserProfileLease profileLease;
        private final ThreadPoolExecutor executor;
        private final Map<String, ManagedTab> tabs = new LinkedHashMap<>();
        private final Map<Page, ManagedTab> tabsByPage = new IdentityHashMap<>();
        private final AtomicLong version = new AtomicLong();
        private final AtomicLong lastReceivedInputSequence = new AtomicLong(-1);
        private final AtomicLong lastAppliedInputSequence = new AtomicLong(-1);
        private final AtomicLong rejectedInputOperations = new AtomicLong();
        private volatile String activeTabId;
        private volatile List<TabState> tabsSnapshot = List.of();
        private volatile MouseButton pressedButton;
        private volatile String pressedTabId;
        private volatile int viewportWidth = DEFAULT_VIEWPORT_WIDTH;
        private volatile int viewportHeight = DEFAULT_VIEWPORT_HEIGHT;
        private volatile int quality = DEFAULT_QUALITY;
        private volatile int intervalMillis = DEFAULT_INTERVAL_MILLIS;
        private volatile boolean networkEnabled = DEFAULT_NETWORK_ENABLED;
        private volatile int bandwidthBytesPerSecond = DEFAULT_BANDWIDTH_BYTES_PER_SECOND;
        private volatile int latencyMillis = DEFAULT_LATENCY_MILLIS;

        BrowserSession(
                String id,
                BrowserContext context,
                String browserVersion,
                String playwrightVersion,
                BrowserMode mode,
                BrowserChannel channel,
                BrowserProfileLease profileLease) {
            this.id = id;
            this.profileId = profileLease.profileId();
            this.context = context;
            this.browserVersion = browserVersion;
            this.playwrightVersion = playwrightVersion;
            this.mode = mode;
            this.channel = channel;
            this.profileLease = profileLease;
            this.executor = new ThreadPoolExecutor(
                    1,
                    1,
                    0,
                    TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(MAX_PENDING_OPERATIONS),
                    runnable -> {
                        Thread thread = new Thread(runnable, "browser-session-" + id);
                        thread.setDaemon(true);
                        return thread;
                    },
                    new ThreadPoolExecutor.AbortPolicy());
        }

        public String id() {
            return id;
        }

        private void initialize(String url) {
            String target = normalizeUrl(url);
            submit(() -> {
                context.onPage(page -> execute(() -> {
                    if (tabsByPage.containsKey(page)) return;
                    register(page);
                    publish();
                }));
                List<Page> existingPages = context.pages();
                if (existingPages.isEmpty()) {
                    createTab(target);
                    return null;
                }
                Page firstPage = existingPages.getFirst();
                for (int index = 1; index < existingPages.size(); index++) {
                    closeQuietly(existingPages.get(index));
                }
                ManagedTab tab = register(firstPage);
                activeTabId = tab.id;
                tab.page.bringToFront();
                tab.enforceWindowSize(viewportWidth, viewportHeight, mode.headless());
                navigate(tab, target);
                return null;
            });
        }

        public TabsState state() {
            return new TabsState(
                    version.get(),
                    activeTabId,
                    tabsSnapshot,
                    quality,
                    intervalMillis,
                    networkEnabled,
                    bandwidthBytesPerSecond,
                    latencyMillis);
        }

        public BrowserInfo browserInfo() {
            TabsState state = state();
            TabState active = state.tabs().stream()
                    .filter(tab -> tab.id().equals(state.activeTabId()))
                    .findFirst()
                    .orElse(null);
            return new BrowserInfo(
                    channel.displayName(),
                    browserVersion,
                    playwrightVersion,
                    profileId,
                    state.tabs().size(),
                    active == null ? "" : active.title(),
                    active == null ? "" : active.url(),
                    !executor.isShutdown(),
                    mode.headless(),
                    channel.playwrightName(),
                    new InputInfo(
                            lastReceivedInputSequence.get(),
                            lastAppliedInputSequence.get(),
                            executor.getQueue().size(),
                            rejectedInputOperations.get()));
        }

        public boolean canCapture() {
            return !executor.isShutdown() && executor.getQueue().size() < MAX_CAPTURE_QUEUE;
        }

        public byte[] capture() {
            try {
                return submit(() -> {
                    ManagedTab tab = tabs.get(activeTabId);
                    if (tab == null) return null;
                    try {
                        tab.enforceWindowSize(viewportWidth, viewportHeight, mode.headless());
                        if (tab.refresh()) publish();
                        return tab.capture(quality);
                    } catch (RuntimeException failure) {
                        return null;
                    }
                });
            } catch (IllegalStateException failure) {
                return null;
            }
        }

        public void resizeViewport(int requestedWidth, int requestedHeight) {
            int nextWidth = Math.min(MAX_VIEWPORT_WIDTH, Math.max(MIN_VIEWPORT_WIDTH, requestedWidth));
            int nextHeight = Math.min(MAX_VIEWPORT_HEIGHT, Math.max(MIN_VIEWPORT_HEIGHT, requestedHeight));
            if (nextWidth == viewportWidth && nextHeight == viewportHeight) return;
            viewportWidth = nextWidth;
            viewportHeight = nextHeight;
            String tabId = activeTabId;
            execute(() -> {
                ManagedTab tab = tabs.get(tabId);
                if (tab != null) tab.enforceWindowSize(viewportWidth, viewportHeight, mode.headless());
            });
        }

        public void openTab(String url) {
            execute(() -> createTab(normalizeUrl(url)));
        }

        public void activateTab(String tabId) {
            execute(() -> {
                ManagedTab tab = tabs.get(tabId);
                if (tab == null) return;
                activeTabId = tab.id;
                tab.page.bringToFront();
                tab.enforceWindowSize(viewportWidth, viewportHeight, mode.headless());
                publish();
            });
        }

        public void closeTab(String tabId) {
            execute(() -> {
                ManagedTab tab = tabs.remove(tabId);
                if (tab == null) return;
                tabsByPage.remove(tab.page);
                if (tab.id.equals(activeTabId)) {
                    ManagedTab next = tabs.values().stream().findFirst().orElse(null);
                    activeTabId = next == null ? null : next.id;
                    if (next != null) {
                        next.page.bringToFront();
                        next.enforceWindowSize(viewportWidth, viewportHeight, mode.headless());
                    }
                }
                if (tab.id.equals(pressedTabId)) {
                    pressedButton = null;
                    pressedTabId = null;
                }
                closeQuietly(tab.page);
                if (tabs.isEmpty()) createTab("about:blank");
                publish();
            });
        }

        public void navigateActive(String url) {
            String tabId = activeTabId;
            execute(() -> {
                ManagedTab tab = tabs.get(tabId);
                if (tab == null) return;
                navigate(tab, normalizeUrl(url));
            });
        }

        public void configure(
                int requestedQuality,
                int requestedIntervalMillis,
                boolean requestedNetworkEnabled,
                int requestedBandwidthBytesPerSecond,
                int requestedLatencyMillis) {
            execute(() -> {
                quality = Math.min(MAX_QUALITY, Math.max(MIN_QUALITY, requestedQuality));
                intervalMillis = Math.min(MAX_INTERVAL_MILLIS, Math.max(MIN_INTERVAL_MILLIS, requestedIntervalMillis));
                networkEnabled = requestedNetworkEnabled;
                bandwidthBytesPerSecond = Math.min(
                        MAX_BANDWIDTH_BYTES_PER_SECOND,
                        Math.max(MIN_BANDWIDTH_BYTES_PER_SECOND, requestedBandwidthBytesPerSecond));
                latencyMillis = Math.min(MAX_LATENCY_MILLIS, Math.max(MIN_LATENCY_MILLIS, requestedLatencyMillis));
                publish();
            });
        }

        public void move(BrowserInput input) {
            String tabId = activeTabId;
            executeInput(input, () -> {
                ManagedTab tab = tabs.get(tabId);
                if (tab != null) {
                    tab.page.mouse().move(
                            tab.mapX(input.x(), viewportWidth),
                            tab.mapY(input.y(), viewportHeight));
                }
            });
        }

        public void down(BrowserInput input) {
            String tabId = activeTabId;
            executeInput(input, () -> {
                ManagedTab tab = tabs.get(tabId);
                if (tab == null) return;
                tab.page.mouse().move(
                        tab.mapX(input.x(), viewportWidth),
                        tab.mapY(input.y(), viewportHeight));
                tab.page.mouse().down(new Mouse.DownOptions().setButton(input.mouseButton()));
                pressedButton = input.mouseButton();
                pressedTabId = tab.id;
            });
        }

        public void up(BrowserInput input) {
            String tabId = activeTabId;
            executeInput(input, () -> {
                ManagedTab tab = tabs.get(tabId);
                if (tab == null) return;
                tab.page.mouse().move(
                        tab.mapX(input.x(), viewportWidth),
                        tab.mapY(input.y(), viewportHeight));
                tab.page.mouse().up(new Mouse.UpOptions().setButton(input.mouseButton()));
                pressedButton = null;
                pressedTabId = null;
            });
        }

        public void context(BrowserInput input) {
            String tabId = activeTabId;
            executeInput(input, () -> {
                ManagedTab tab = tabs.get(tabId);
                if (tab == null) return;
                tab.page.mouse().click(
                        tab.mapX(input.x(), viewportWidth),
                        tab.mapY(input.y(), viewportHeight),
                        new Mouse.ClickOptions().setButton(input.mouseButton()));
            });
        }

        public void wheel(double x, double y, double deltaX, double deltaY) {
            String tabId = activeTabId;
            execute(() -> {
                ManagedTab tab = tabs.get(tabId);
                if (tab == null) return;
                tab.page.mouse().move(
                        tab.mapX(x, viewportWidth),
                        tab.mapY(y, viewportHeight));
                tab.page.mouse().wheel(deltaX, deltaY);
            });
        }

        public void key(String key) {
            if (key == null || key.isBlank()) return;
            String tabId = activeTabId;
            execute(() -> {
                ManagedTab tab = tabs.get(tabId);
                if (tab == null) return;
                if (key.codePointCount(0, key.length()) == 1) {
                    tab.page.keyboard().insertText(key);
                } else if (NAMED_KEYS.contains(key)) {
                    tab.page.keyboard().press(key);
                }
            });
        }

        public void releasePressedButton() {
            MouseButton button = pressedButton;
            String tabId = pressedTabId;
            pressedButton = null;
            pressedTabId = null;
            if (button == null || tabId == null) return;
            execute(() -> {
                ManagedTab tab = tabs.get(tabId);
                if (tab == null) return;
                tab.page.mouse().up(new Mouse.UpOptions().setButton(button));
            });
        }

        public void close() {
            try {
                if (!executor.isShutdown()) {
                    submit(() -> {
                        context.close();
                        return null;
                    });
                }
            } catch (IllegalStateException failure) {
                log.debug("浏览器会话关闭时目标已不存在: {}", failure.getMessage());
            } finally {
                executor.shutdownNow();
                profileLease.close();
            }
        }

        private void createTab(String url) {
            Page page = context.newPage();
            ManagedTab tab = register(page);
            if (tab == null) return;
            activeTabId = tab.id;
            tab.page.bringToFront();
            tab.enforceWindowSize(viewportWidth, viewportHeight, mode.headless());
            publish();
            navigate(tab, url);
        }

        private ManagedTab register(Page page) {
            ManagedTab existing = tabsByPage.get(page);
            if (existing != null) return existing;
            ManagedTab tab = new ManagedTab(UUID.randomUUID().toString(), page, context.newCDPSession(page));
            tabs.put(tab.id, tab);
            tabsByPage.put(page, tab);
            page.onLoad(ignored -> execute(() -> {
                ManagedTab current = tabsByPage.get(page);
                if (current == null) return;
                current.refresh();
                current.loading = false;
                current.error = "";
                publish();
            }));
            page.onClose(ignored -> execute(() -> removeClosedPage(page)));
            page.onConsoleMessage(message -> {
                if ("error".equals(message.type())) {
                    log.warn("远端页面控制台错误: {}", message.text());
                } else {
                    log.debug("远端页面控制台 {}: {}", message.type(), message.text());
                }
            });
            page.onPageError(error -> log.warn("远端页面异常: {}", String.valueOf(error)));
            return tab;
        }

        private void removeClosedPage(Page page) {
            ManagedTab tab = tabsByPage.remove(page);
            if (tab == null) return;
            tabs.remove(tab.id);
            if (tab.id.equals(activeTabId)) {
                ManagedTab next = tabs.values().stream().findFirst().orElse(null);
                activeTabId = next == null ? null : next.id;
                if (next != null) {
                    next.page.bringToFront();
                    next.enforceWindowSize(viewportWidth, viewportHeight, mode.headless());
                }
            }
            if (tab.id.equals(pressedTabId)) {
                pressedButton = null;
                pressedTabId = null;
            }
            if (tabs.isEmpty() && context.browser() != null && context.browser().isConnected()) {
                createTab("about:blank");
            }
            publish();
        }

        private void navigate(ManagedTab tab, String url) {
            tab.url = url;
            tab.title = "";
            tab.loading = true;
            tab.error = "";
            publish();
            try {
                tab.page.navigate(url, new Page.NavigateOptions()
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(NAVIGATION_TIMEOUT_MILLIS));
                tab.refresh();
                publish();
            } catch (RuntimeException failure) {
                tab.loading = false;
                tab.error = failure.getMessage() == null ? "导航失败" : failure.getMessage();
                publish();
                log.warn("远端页面导航失败 {}: {}", url, failure.getMessage());
            }
        }

        private void publish() {
            List<TabState> snapshot = new ArrayList<>(tabs.size());
            for (ManagedTab tab : tabs.values()) {
                snapshot.add(new TabState(tab.id, tab.title, tab.url, tab.loading, tab.error));
            }
            tabsSnapshot = List.copyOf(snapshot);
            version.incrementAndGet();
        }

        private void executeInput(BrowserInput input, Runnable action) {
            lastReceivedInputSequence.accumulateAndGet(input.sequence(), Math::max);
            if (executor.isShutdown()) {
                rejectedInputOperations.incrementAndGet();
                return;
            }
            Runnable command = () -> {
                try {
                    action.run();
                    lastAppliedInputSequence.accumulateAndGet(input.sequence(), Math::max);
                } catch (RuntimeException failure) {
                    log.warn("浏览器输入操作失败: {}", failure.getMessage());
                }
            };
            try {
                executor.execute(command);
                return;
            } catch (RejectedExecutionException ignored) {
            }
            while (!executor.isShutdown()) {
                try {
                    if (executor.getQueue().offer(command, 50, TimeUnit.MILLISECONDS)) return;
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    rejectedInputOperations.incrementAndGet();
                    return;
                }
            }
            rejectedInputOperations.incrementAndGet();
        }

        private void execute(Runnable action) {
            if (executor.isShutdown()) return;
            try {
                executor.execute(() -> {
                    try {
                        action.run();
                    } catch (RuntimeException failure) {
                        log.warn("浏览器会话操作失败: {}", failure.getMessage());
                    }
                });
            } catch (RejectedExecutionException ignored) {
            }
        }

        private <T> T submit(Callable<T> action) {
            try {
                return executor.submit(action).get();
            } catch (ExecutionException exception) {
                throw new IllegalStateException(exception.getCause());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            } catch (RejectedExecutionException exception) {
                throw new IllegalStateException("浏览器会话已关闭", exception);
            }
        }

        private static String normalizeUrl(String url) {
            String value = url == null ? "" : url.trim();
            if (value.isEmpty()) return "about:blank";
            if (value.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*$")) return value;
            return "https://" + value;
        }

        private static void closeQuietly(Page page) {
            try {
                page.close();
            } catch (RuntimeException ignored) {
            }
        }

        private static final class ManagedTab {
            private final String id;
            private final Page page;
            private final CDPSession cdp;
            private String title = "";
            private String url = "about:blank";
            private boolean loading;
            private String error = "";
            private int clientWidth = DEFAULT_VIEWPORT_WIDTH;
            private int clientHeight = DEFAULT_VIEWPORT_HEIGHT;
            private int appliedViewportWidth;
            private int appliedViewportHeight;
            private int lockedOuterWidth;
            private int lockedOuterHeight;

            private ManagedTab(String id, Page page, CDPSession cdp) {
                this.id = id;
                this.page = page;
                this.cdp = cdp;
            }

            private boolean refresh() {
                String previousUrl = url;
                String previousTitle = title;
                try {
                    url = page.url();
                } catch (RuntimeException ignored) {
                }
                try {
                    title = page.title();
                } catch (RuntimeException ignored) {
                }
                return !previousUrl.equals(url) || !previousTitle.equals(title);
            }

            private byte[] capture(int quality) {
                JsonObject parameters = new JsonObject();
                parameters.addProperty("format", "jpeg");
                parameters.addProperty("quality", quality);
                parameters.addProperty("fromSurface", true);
                JsonObject result = cdp.send("Page.captureScreenshot", parameters);
                return Base64.getDecoder().decode(result.get("data").getAsString());
            }

            private void enforceWindowSize(int targetWidth, int targetHeight, boolean headless) {
                if (headless) {
                    if (appliedViewportWidth != targetWidth || appliedViewportHeight != targetHeight) {
                        page.setViewportSize(targetWidth, targetHeight);
                        appliedViewportWidth = targetWidth;
                        appliedViewportHeight = targetHeight;
                        clientWidth = targetWidth;
                        clientHeight = targetHeight;
                    }
                    return;
                }
                try {
                    readViewportMetrics();
                    JsonObject window = cdp.send("Browser.getWindowForTarget");
                    int windowId = window.get("windowId").getAsInt();
                    JsonObject bounds = window.getAsJsonObject("bounds");
                    String windowState = bounds.has("windowState") && !bounds.get("windowState").isJsonNull()
                            ? bounds.get("windowState").getAsString()
                            : "normal";
                    int outerWidth = bounds.get("width").getAsInt();
                    int outerHeight = bounds.get("height").getAsInt();
                    boolean viewportChanged = appliedViewportWidth != targetWidth
                            || appliedViewportHeight != targetHeight;
                    boolean windowDrifted = !"normal".equals(windowState)
                            || (lockedOuterWidth > 0 && Math.abs(outerWidth - lockedOuterWidth) > 2)
                            || (lockedOuterHeight > 0 && Math.abs(outerHeight - lockedOuterHeight) > 2);
                    if (!viewportChanged && !windowDrifted) return;

                    if (!"normal".equals(windowState)) {
                        JsonObject normalBounds = new JsonObject();
                        normalBounds.addProperty("windowState", "normal");
                        setWindowBounds(windowId, normalBounds);
                        bounds = cdp.send("Browser.getWindowForTarget").getAsJsonObject("bounds");
                        outerWidth = bounds.get("width").getAsInt();
                        outerHeight = bounds.get("height").getAsInt();
                        readViewportMetrics();
                    }

                    int insetWidth = clientWidth > 0 ? Math.max(0, outerWidth - clientWidth) : 16;
                    int insetHeight = clientHeight > 0 ? Math.max(0, outerHeight - clientHeight) : 88;
                    JsonObject targetBounds = new JsonObject();
                    targetBounds.addProperty("windowState", "normal");
                    targetBounds.addProperty("width", targetWidth + insetWidth);
                    targetBounds.addProperty("height", targetHeight + insetHeight);
                    setWindowBounds(windowId, targetBounds);

                    JsonObject applied = cdp.send("Browser.getWindowForTarget").getAsJsonObject("bounds");
                    lockedOuterWidth = applied.get("width").getAsInt();
                    lockedOuterHeight = applied.get("height").getAsInt();
                    appliedViewportWidth = targetWidth;
                    appliedViewportHeight = targetHeight;
                    readViewportMetrics();
                } catch (RuntimeException ignored) {
                }
            }

            private void readViewportMetrics() {
                JsonObject cssViewport = cdp.send("Page.getLayoutMetrics").getAsJsonObject("cssVisualViewport");
                int width = cssViewport.get("clientWidth").getAsInt();
                int height = cssViewport.get("clientHeight").getAsInt();
                if (width > 0 && height > 0) {
                    clientWidth = width;
                    clientHeight = height;
                }
            }

            private void setWindowBounds(int windowId, JsonObject bounds) {
                JsonObject parameters = new JsonObject();
                parameters.addProperty("windowId", windowId);
                parameters.add("bounds", bounds);
                cdp.send("Browser.setWindowBounds", parameters);
            }

            private double mapX(double x, int viewportWidth) {
                return viewportWidth <= 0 || clientWidth <= 0 ? x : x * clientWidth / viewportWidth;
            }

            private double mapY(double y, int viewportHeight) {
                return viewportHeight <= 0 || clientHeight <= 0 ? y : y * clientHeight / viewportHeight;
            }
        }
    }
}
