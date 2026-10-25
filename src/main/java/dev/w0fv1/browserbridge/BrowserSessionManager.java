package dev.w0fv1.browserbridge;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Playwright;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public final class BrowserSessionManager {
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
}
