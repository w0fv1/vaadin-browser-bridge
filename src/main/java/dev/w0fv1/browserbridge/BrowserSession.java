package dev.w0fv1.browserbridge;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Mouse;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.MouseButton;
import com.microsoft.playwright.options.WaitUntilState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class BrowserSession {
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
    private static final Logger log = LoggerFactory.getLogger(BrowserSession.class);
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
    private final Object lifecycleLock = new Object();
    private final Map<String, BrowserTab> tabs = new LinkedHashMap<>();
    private final Map<Page, BrowserTab> tabsByPage = new IdentityHashMap<>();
    private final AtomicLong version = new AtomicLong();
    private final AtomicLong lastReceivedInputSequence = new AtomicLong(-1);
    private final AtomicLong lastAppliedInputSequence = new AtomicLong(-1);
    private final AtomicLong rejectedInputOperations = new AtomicLong();
    private final AtomicReference<BrowserFailure> lastFailure = new AtomicReference<>();
    private volatile boolean closing;
    private Future<?> closeFuture;
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

    void initialize(String url) {
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
            BrowserTab tab = register(firstPage);
            activeTabId = tab.id();
            tab.page().bringToFront();
            tab.resize(viewportWidth, viewportHeight);
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
        String activeTitle = "";
        String activeUrl = "";
        if (active != null) {
            activeTitle = active.title();
            activeUrl = active.url();
        }
        return new BrowserInfo(
                channel.displayName(),
                browserVersion,
                playwrightVersion,
                profileId,
                state.tabs().size(),
                activeTitle,
                activeUrl,
                contextOpen(),
                mode.headless(),
                channel.playwrightName(),
                new InputInfo(
                        lastReceivedInputSequence.get(),
                        lastAppliedInputSequence.get(),
                        executor.getQueue().size(),
                        rejectedInputOperations.get()),
                lastFailure.get());
    }

    public boolean canCapture() {
        return !closing && !executor.isShutdown() && executor.getQueue().size() < MAX_CAPTURE_QUEUE;
    }

    boolean isAvailable() {
        return !closing && !executor.isShutdown() && browserConnected();
    }

    public byte[] capture() {
        try {
            return submit(() -> {
                BrowserTab tab = tabs.get(activeTabId);
                if (tab == null) return null;
                tab.resize(viewportWidth, viewportHeight);
                if (tab.refresh()) publish();
                return tab.capture(quality);
            });
        } catch (RuntimeException failure) {
            if (!closing && !executor.isShutdown()) recordFailure("capture", failure);
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
            BrowserTab tab = tabs.get(tabId);
            if (tab != null) tab.resize(viewportWidth, viewportHeight);
        });
    }

    public void openTab(String url) {
        execute(() -> createTab(normalizeUrl(url)));
    }

    public void activateTab(String tabId) {
        execute(() -> {
            BrowserTab tab = tabs.get(tabId);
            if (tab == null) return;
            activeTabId = tab.id();
            tab.page().bringToFront();
            tab.resize(viewportWidth, viewportHeight);
            publish();
        });
    }

    public void closeTab(String tabId) {
        execute(() -> {
            BrowserTab tab = tabs.remove(tabId);
            if (tab == null) return;
            tabsByPage.remove(tab.page());
            if (tab.id().equals(activeTabId)) {
                BrowserTab next = tabs.values().stream().findFirst().orElse(null);
                activeTabId = null;
                if (next != null) {
                    activeTabId = next.id();
                    next.page().bringToFront();
                    next.resize(viewportWidth, viewportHeight);
                }
            }
            if (tab.id().equals(pressedTabId)) {
                pressedButton = null;
                pressedTabId = null;
            }
            closeQuietly(tab.page());
            if (tabs.isEmpty()) createTab("about:blank");
            publish();
        });
    }

    public void navigateActive(String url) {
        String tabId = activeTabId;
        execute(() -> {
            BrowserTab tab = tabs.get(tabId);
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
            BrowserTab tab = tabs.get(tabId);
            if (tab != null) {
                tab.page().mouse().move(
                        tab.mapX(input.x(), viewportWidth),
                        tab.mapY(input.y(), viewportHeight));
            }
        });
    }

    public void down(BrowserInput input) {
        String tabId = activeTabId;
        executeInput(input, () -> {
            BrowserTab tab = tabs.get(tabId);
            if (tab == null) return;
            tab.page().mouse().move(
                    tab.mapX(input.x(), viewportWidth),
                    tab.mapY(input.y(), viewportHeight));
            tab.page().mouse().down(new Mouse.DownOptions().setButton(input.mouseButton()));
            pressedButton = input.mouseButton();
            pressedTabId = tab.id();
        });
    }

    public void up(BrowserInput input) {
        String tabId = activeTabId;
        executeInput(input, () -> {
            BrowserTab tab = tabs.get(tabId);
            if (tab == null) return;
            tab.page().mouse().move(
                    tab.mapX(input.x(), viewportWidth),
                    tab.mapY(input.y(), viewportHeight));
            tab.page().mouse().up(new Mouse.UpOptions().setButton(input.mouseButton()));
            pressedButton = null;
            pressedTabId = null;
        });
    }

    public void context(BrowserInput input) {
        String tabId = activeTabId;
        executeInput(input, () -> {
            BrowserTab tab = tabs.get(tabId);
            if (tab == null) return;
            tab.page().mouse().click(
                    tab.mapX(input.x(), viewportWidth),
                    tab.mapY(input.y(), viewportHeight),
                    new Mouse.ClickOptions().setButton(input.mouseButton()));
        });
    }

    public void wheel(double x, double y, double deltaX, double deltaY) {
        String tabId = activeTabId;
        execute(() -> {
            BrowserTab tab = tabs.get(tabId);
            if (tab == null) return;
            tab.page().mouse().move(
                    tab.mapX(x, viewportWidth),
                    tab.mapY(y, viewportHeight));
            tab.page().mouse().wheel(deltaX, deltaY);
        });
    }

    public void key(String key) {
        if (key == null || key.isBlank()) return;
        String tabId = activeTabId;
        execute(() -> {
            BrowserTab tab = tabs.get(tabId);
            if (tab == null) return;
            if (key.codePointCount(0, key.length()) == 1) {
                tab.page().keyboard().insertText(key);
            } else if (NAMED_KEYS.contains(key)) {
                tab.page().keyboard().press(key);
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
            BrowserTab tab = tabs.get(tabId);
            if (tab == null) return;
            tab.page().mouse().up(new Mouse.UpOptions().setButton(button));
        });
    }

    public void close() {
        Future<?> pendingClose;
        synchronized (lifecycleLock) {
            if (!closing) {
                closing = true;
                try {
                    pendingClose = executor.submit(() -> {
                        context.close();
                        return null;
                    });
                } catch (RejectedExecutionException failure) {
                    pendingClose = null;
                    recordFailure("close", failure);
                }
                closeFuture = pendingClose;
                executor.shutdown();
            } else {
                pendingClose = closeFuture;
            }
        }
        boolean closed = false;
        if (pendingClose != null) {
            try {
                pendingClose.get();
                closed = true;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                recordFailure("close", interrupted);
            } catch (ExecutionException failure) {
                Throwable cause = failure.getCause();
                if (cause != null) {
                    recordFailure("close", cause);
                }
            }
        }
        if (closed || contextDisconnected()) {
            profileLease.close();
        }
    }

    private void createTab(String url) {
        Page page = context.newPage();
        BrowserTab tab = register(page);
        if (tab == null) return;
        activeTabId = tab.id();
        tab.page().bringToFront();
        tab.resize(viewportWidth, viewportHeight);
        publish();
        navigate(tab, url);
    }

    private BrowserTab register(Page page) {
        BrowserTab existing = tabsByPage.get(page);
        if (existing != null) return existing;
        BrowserTab tab = new BrowserTab(
                UUID.randomUUID().toString(),
                page,
                context.newCDPSession(page),
                mode);
        tabs.put(tab.id(), tab);
        tabsByPage.put(page, tab);
        page.onLoad(ignored -> execute(() -> {
            BrowserTab current = tabsByPage.get(page);
            if (current == null) return;
            current.refresh();
            current.markLoaded();
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
        BrowserTab tab = tabsByPage.remove(page);
        if (tab == null) return;
        tabs.remove(tab.id());
        if (tab.id().equals(activeTabId)) {
            BrowserTab next = tabs.values().stream().findFirst().orElse(null);
            activeTabId = null;
            if (next != null) {
                activeTabId = next.id();
                next.page().bringToFront();
                next.resize(viewportWidth, viewportHeight);
            }
        }
        if (tab.id().equals(pressedTabId)) {
            pressedButton = null;
            pressedTabId = null;
        }
        if (tabs.isEmpty() && context.browser() != null && context.browser().isConnected()) {
            createTab("about:blank");
        }
        publish();
    }

    private void navigate(BrowserTab tab, String url) {
        tab.startNavigation(url);
        publish();
        try {
            tab.page().navigate(url, new Page.NavigateOptions()
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                    .setTimeout(NAVIGATION_TIMEOUT_MILLIS));
            tab.refresh();
            publish();
        } catch (RuntimeException failure) {
            String message = "导航失败";
            if (failure.getMessage() != null) {
                message = failure.getMessage();
            }
            tab.markNavigationFailure(message);
            publish();
            log.warn("远端页面导航失败 {}: {}", url, message);
        }
    }

    private void publish() {
        List<TabState> snapshot = new ArrayList<>(tabs.size());
        for (BrowserTab tab : tabs.values()) {
            snapshot.add(new TabState(
                    tab.id(),
                    tab.title(),
                    tab.url(),
                    tab.loading(),
                    tab.error()));
        }
        tabsSnapshot = List.copyOf(snapshot);
        version.incrementAndGet();
    }

    private void executeInput(BrowserInput input, Runnable action) {
        lastReceivedInputSequence.accumulateAndGet(input.sequence(), Math::max);
        if (closing || executor.isShutdown()) {
            rejectedInputOperations.incrementAndGet();
            return;
        }
        Runnable command = () -> {
            try {
                action.run();
                lastAppliedInputSequence.accumulateAndGet(input.sequence(), Math::max);
            } catch (RuntimeException failure) {
                recordFailure("input", failure);
            }
        };
        synchronized (lifecycleLock) {
            if (closing || executor.isShutdown()) {
                rejectedInputOperations.incrementAndGet();
                return;
            }
            try {
                executor.execute(command);
                return;
            } catch (RejectedExecutionException ignored) {
            }
            try {
                if (executor.getQueue().offer(command, 50, TimeUnit.MILLISECONDS)) return;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            rejectedInputOperations.incrementAndGet();
        }
    }

    private void execute(Runnable action) {
        synchronized (lifecycleLock) {
            if (closing || executor.isShutdown()) return;
            try {
                executor.execute(() -> {
                    try {
                        action.run();
                    } catch (RuntimeException failure) {
                        recordFailure("session", failure);
                    }
                });
            } catch (RejectedExecutionException ignored) {
            }
        }
    }

    private <T> T submit(Callable<T> action) {
        Future<T> future;
        synchronized (lifecycleLock) {
            if (closing || executor.isShutdown()) {
                throw new IllegalStateException("浏览器会话已关闭");
            }
            try {
                future = executor.submit(action);
            } catch (RejectedExecutionException exception) {
                throw new IllegalStateException("浏览器会话已关闭", exception);
            }
        }
        try {
            return future.get();
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            throw new IllegalStateException(cause);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private void recordFailure(String operation, Throwable failure) {
        String message = failure.getClass().getSimpleName();
        if (failure.getMessage() != null) {
            message = failure.getMessage();
        }
        BrowserFailure current = new BrowserFailure(operation, message, System.currentTimeMillis());
        BrowserFailure previous = lastFailure.getAndSet(current);
        boolean changed = previous == null
                || !previous.operation().equals(current.operation())
                || !previous.message().equals(current.message());
        if (changed) {
            log.warn("浏览器{}操作失败: {}", operation, message);
        }
    }

    private boolean contextOpen() {
        if (closing || executor.isShutdown()) return false;
        return browserConnected();
    }

    private boolean contextDisconnected() {
        return !browserConnected();
    }

    private boolean browserConnected() {
        try {
            return context.browser() != null && context.browser().isConnected();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static String normalizeUrl(String url) {
        String value = "";
        if (url != null) {
            value = url.trim();
        }
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
            InputInfo input,
            BrowserFailure lastFailure) {
    }

    public record InputInfo(
            long lastReceivedSequence,
            long lastAppliedSequence,
            int pendingOperations,
            long rejectedOperations) {
    }

    public record BrowserFailure(String operation, String message, long timestamp) {
    }
}
