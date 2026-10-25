package dev.w0fv1.remotebrowser;

import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;

final class RemoteBrowserWebSocketConfigurer implements WebSocketConfigurer {
    private final BrowserStreamHandler handler;
    private final String path;
    private final String[] allowedOriginPatterns;

    RemoteBrowserWebSocketConfigurer(BrowserStreamHandler handler, BrowserRuntimeProperties properties) {
        this.handler = handler;
        this.path = properties.websocketPath();
        this.allowedOriginPatterns = properties.allowedOriginPatterns().toArray(String[]::new);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        WebSocketHandlerRegistration registration = registry.addHandler(handler, path + "/{id}");
        if (allowedOriginPatterns.length > 0) {
            registration.setAllowedOriginPatterns(allowedOriginPatterns);
        }
    }
}
