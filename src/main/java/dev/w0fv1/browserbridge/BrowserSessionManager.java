package dev.w0fv1.browserbridge;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Playwright;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public final class BrowserSessionManager {
    private static final Logger log = LoggerFactory.getLogger(BrowserSessionManager.class);
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
        BrowserContext context = null;
        BrowserSession session = null;
        try {
            context = runtime.launch(lease.directory());
            String browserVersion = Optional.ofNullable(context.browser())
                    .map(browser -> browser.version())
                    .orElse("unknown");
            session = new BrowserSession(
                    UUID.randomUUID().toString(),
                    context,
                    browserVersion,
                    playwrightVersion,
                    properties.mode(),
                    properties.channel(),
                    lease);
            session.initialize(url);
            sessions.put(session.id(), session);
            return session;
        } catch (RuntimeException failure) {
            if (session != null) {
                session.close();
            } else {
                boolean contextClosed = true;
                if (context != null) {
                    try {
                        context.close();
                    } catch (RuntimeException closeFailure) {
                        contextClosed = false;
                        log.warn("启动失败后无法关闭浏览器 context: {}", closeFailure.getMessage());
                    }
                }
                if (contextClosed) {
                    lease.close();
                }
            }
            String message = "浏览器会话初始化失败";
            if (context == null) {
                message = "无法启动真实 Chrome";
            }
            throw new IllegalStateException(message + "，配置 " + profileId, failure);
        }
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
