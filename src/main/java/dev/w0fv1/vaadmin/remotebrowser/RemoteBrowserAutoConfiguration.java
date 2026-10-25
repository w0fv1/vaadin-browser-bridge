package dev.w0fv1.vaadmin.remotebrowser;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.ObjectMapper;

@AutoConfiguration
@ConditionalOnProperty(prefix = "browser.remote", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(BrowserRuntimeProperties.class)
public class RemoteBrowserAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    BrowserEnvironmentStore browserEnvironmentStore(
            BrowserRuntimeProperties properties,
            ObjectProvider<ObjectMapper> mappers) {
        return new BrowserEnvironmentStore(properties, mappers.getIfAvailable(ObjectMapper::new));
    }

    @Bean
    @ConditionalOnMissingBean
    BrowserRuntime browserRuntime(
            BrowserRuntimeProperties properties,
            ObjectProvider<ObjectMapper> mappers) {
        return new BrowserRuntime(properties, mappers.getIfAvailable(ObjectMapper::new));
    }

    @Bean
    @ConditionalOnMissingBean(RemoteBrowser.class)
    BrowserSessionManager browserSessionManager(
            BrowserRuntime runtime,
            BrowserRuntimeProperties properties,
            BrowserEnvironmentStore environments,
            ObjectProvider<ObjectMapper> mappers) {
        return new BrowserSessionManager(
                runtime, properties, environments, mappers.getIfAvailable(ObjectMapper::new));
    }
}
