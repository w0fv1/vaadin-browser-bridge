package dev.w0fv1.vaadmin.remotebrowser;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import tools.jackson.databind.ObjectMapper;

@AutoConfiguration(after = RemoteBrowserAutoConfiguration.class)
@ConditionalOnProperty(prefix = "browser.remote", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(WebSocketConfigurer.class)
@EnableWebSocket
public class RemoteBrowserWebSocketAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    BrowserStreamHandler browserStreamHandler(
            BrowserSessionManager sessions,
            ObjectProvider<ObjectMapper> mappers) {
        return new BrowserStreamHandler(sessions, mappers.getIfAvailable(ObjectMapper::new));
    }

    @Bean
    @ConditionalOnMissingBean
    RemoteBrowserWebSocketConfigurer remoteBrowserWebSocketConfigurer(
            BrowserStreamHandler handler,
            BrowserRuntimeProperties properties) {
        return new RemoteBrowserWebSocketConfigurer(handler, properties);
    }
}
