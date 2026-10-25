package dev.w0fv1.remotebrowser;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;

import static org.assertj.core.api.Assertions.assertThat;

final class RemoteBrowserAutoConfigurationTest {
    private final ApplicationContextRunner nonWeb = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RemoteBrowserAutoConfiguration.class));

    private final WebApplicationContextRunner web = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    RemoteBrowserAutoConfiguration.class,
                    RemoteBrowserWebSocketAutoConfiguration.class));

    @Test
    void registersCoreBeansWithoutComponentScanning() {
        nonWeb.run(context -> {
            assertThat(context).hasSingleBean(RemoteBrowser.class);
            assertThat(context).hasSingleBean(BrowserRuntime.class);
            assertThat(context).hasSingleBean(BrowserEnvironmentStore.class);
            assertThat(context).doesNotHaveBean(BrowserStreamHandler.class);
        });
    }

    @Test
    void registersStreamEndpointInWebApplication() {
        web.run(context -> {
            assertThat(context).hasSingleBean(RemoteBrowser.class);
            assertThat(context).hasSingleBean(BrowserStreamHandler.class);
            assertThat(context).hasSingleBean(RemoteBrowserWebSocketConfigurer.class);
            assertThat(context.getBean(RemoteBrowser.class).streamPath()).isEqualTo("/browser-stream");
        });
    }

    @Test
    void appliesWebsocketAndProfileConfiguration() {
        web.withPropertyValues(
                        "browser.remote.websocket-path=stream/browser/",
                        "browser.remote.allowed-origin-patterns=https://example.org",
                        "browser.remote.profile-root=build/test-profiles/autoconfigure")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    BrowserRuntimeProperties properties = context.getBean(BrowserRuntimeProperties.class);
                    assertThat(properties.websocketPath()).isEqualTo("/stream/browser");
                    assertThat(properties.allowedOriginPatterns()).containsExactly("https://example.org");
                    assertThat(context.getBean(RemoteBrowser.class).streamPath())
                            .isEqualTo("/stream/browser");
                });
    }

    @Test
    void backsOffWhenDisabled() {
        web.withPropertyValues("browser.remote.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(RemoteBrowser.class);
            assertThat(context).doesNotHaveBean(WebSocketConfigurer.class);
        });
    }
}
