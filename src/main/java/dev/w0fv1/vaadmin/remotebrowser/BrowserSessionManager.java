package dev.w0fv1.vaadmin.remotebrowser;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class BrowserSessionManager implements RemoteBrowser {
    private static final Logger log = LoggerFactory.getLogger(BrowserSessionManager.class);
    private static final BrowserProfileId DEFAULT_PROFILE = BrowserProfileId.DEFAULT;

    private final BrowserRuntime runtime;
    private final BrowserRuntimeProperties properties;
    private final BrowserEnvironmentStore environments;
    private final BrowserProfileRegistry profiles;
    private final ObjectMapper mapper;
    private final Map<String, BrowserSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, SharedSession> sharedSessions = new ConcurrentHashMap<>();

    BrowserSessionManager(
            BrowserRuntime runtime,
            BrowserRuntimeProperties properties,
            BrowserEnvironmentStore environments,
            ObjectMapper mapper) {
        this.runtime = runtime;
        this.properties = properties;
        this.environments = environments;
        this.profiles = new BrowserProfileRegistry(properties.profileRoot(), environments);
        this.mapper = mapper;
    }

    @Override
    public List<BrowserProfileId> profiles() {
        return environments.profileIds();
    }

    @Override
    public BrowserEnvironmentProfile environment(BrowserProfileId profileId) {
        return environments.read(profileId);
    }

    @Override
    public void saveEnvironment(BrowserEnvironmentProfile environment) {
        environments.save(environment);
    }

    @Override
    public String streamPath() {
        return properties.websocketPath();
    }

    @Override
    public boolean isOpen(BrowserProfileId profileId) {
        SharedSession shared = sharedSessions.get(profileId.value());
        return shared != null && shared.session.isAvailable();
    }

    RemoteBrowserSession openDefault(String url) {
        return open(DEFAULT_PROFILE, url);
    }

    @Override
    public synchronized RemoteBrowserSession open(BrowserProfileId profileId, String url) {
        SharedSession shared = sharedSessions.get(profileId.value());
        if (shared != null && !shared.session.isAvailable()) {
            sharedSessions.remove(profileId.value());
            sessions.remove(shared.session.id());
            shared.session.close();
            shared = null;
        }
        if (shared == null) {
            BrowserSession session = createSession(profileId.value(), url);
            shared = new SharedSession(session);
            sharedSessions.put(profileId.value(), shared);
            sessions.put(session.id(), session);
        }
        shared.references++;
        SharedSession leased = shared;
        return new RemoteBrowserSession(leased.session, () -> release(profileId.value(), leased.session));
    }

    BrowserSession require(String id) {
        BrowserSession session = sessions.get(id);
        if (session == null) throw new IllegalArgumentException("浏览器会话不存在");
        return session;
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
