package dev.w0fv1.remotebrowser;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public final class BrowserSessionManager {
    private static final Logger log = LoggerFactory.getLogger(BrowserSessionManager.class);
    private static final String DEFAULT_PROFILE = "default";

    private final BrowserRuntime runtime;
    private final BrowserRuntimeProperties properties;
    private final BrowserProfileRegistry profiles;
    private final ObjectMapper mapper;
    private final Map<String, BrowserSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, SharedSession> sharedSessions = new ConcurrentHashMap<>();

    public BrowserSessionManager(
            BrowserRuntime runtime,
            BrowserRuntimeProperties properties,
            BrowserEnvironmentStore environments,
            ObjectMapper mapper) {
        this.runtime = runtime;
        this.properties = properties;
        this.profiles = new BrowserProfileRegistry(properties.profileRoot(), environments);
        this.mapper = mapper;
    }

    public BrowserSessionLease openDefault(String url) {
        return open(DEFAULT_PROFILE, url);
    }

    public synchronized BrowserSessionLease open(String profileId, String url) {
        SharedSession shared = sharedSessions.get(profileId);
        if (shared != null && !shared.session.isAvailable()) {
            sharedSessions.remove(profileId);
            sessions.remove(shared.session.id());
            shared.session.close();
            shared = null;
        }
        if (shared == null) {
            BrowserSession session = createSession(profileId, url);
            shared = new SharedSession(session);
            sharedSessions.put(profileId, shared);
            sessions.put(session.id(), session);
        }
        shared.references++;
        SharedSession leased = shared;
        return new BrowserSessionLease(leased.session, () -> release(profileId, leased.session));
    }

    private BrowserSession createSession(String profileId, String url) {
        BrowserProfileLease lease = profiles.acquire(profileId);
        ChromeBrowser browser = null;
        BrowserSession session = null;
        try {
            browser = runtime.launch(lease.directory(), lease.environment());
            session = new BrowserSession(
                    UUID.randomUUID().toString(),
                    browser,
                    mapper,
                    properties.mode(),
                    properties.channel(),
                    lease);
            session.initialize(url);
            return session;
        } catch (RuntimeException failure) {
            if (session != null) {
                session.close();
            } else {
                if (browser != null) {
                    try {
                        browser.close();
                    } catch (RuntimeException closeFailure) {
                        log.warn("启动失败后无法关闭浏览器: {}", closeFailure.getMessage());
                    }
                }
                lease.close();
            }
            String message = "浏览器会话初始化失败";
            if (browser == null) {
                message = "无法启动真实 Chrome";
            }
            throw new IllegalStateException(message + "，配置 " + profileId, failure);
        }
    }

    private void release(String profileId, BrowserSession session) {
        BrowserSession closing = null;
        synchronized (this) {
            SharedSession shared = sharedSessions.get(profileId);
            if (shared == null || shared.session != session) return;
            shared.references--;
            if (shared.references <= 0) {
                sharedSessions.remove(profileId);
                sessions.remove(session.id());
                closing = session;
            }
        }
        if (closing != null) {
            closing.close();
        }
    }

    public BrowserSession require(String id) {
        BrowserSession session = sessions.get(id);
        if (session == null) throw new IllegalArgumentException("浏览器会话不存在");
        return session;
    }

    @PreDestroy
    void shutdown() {
        List<BrowserSession> closing;
        synchronized (this) {
            closing = new ArrayList<>(sharedSessions.size());
            for (SharedSession shared : sharedSessions.values()) {
                closing.add(shared.session);
            }
            sharedSessions.clear();
            sessions.clear();
        }
        closing.forEach(BrowserSession::close);
    }

    private static final class SharedSession {
        private final BrowserSession session;
        private int references;

        private SharedSession(BrowserSession session) {
            this.session = session;
        }
    }
}
