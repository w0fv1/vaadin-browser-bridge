package dev.w0fv1.browserbridge;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class BrowserRuntimePropertiesTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void defaultsToHeadedChromeAndPersistentProfileRoot() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            BrowserRuntimeProperties properties = context.getBean(BrowserRuntimeProperties.class);

            assertThat(properties.mode()).isEqualTo(BrowserMode.HEADED);
            assertThat(properties.channel()).isEqualTo(BrowserChannel.CHROME);
            assertThat(properties.profileRoot()).isEqualTo(Path.of(
                    System.getProperty("user.home"), ".browser-bridge", "profiles"));
        });
    }

    @Test
    void bindsHeadlessEdgeAndNormalizedProfileRoot() {
        contextRunner
                .withPropertyValues(
                        "browser.bridge.mode=headless",
                        "browser.bridge.channel=msedge",
                        "browser.bridge.profile-root=build/test-profiles")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    BrowserRuntimeProperties properties = context.getBean(BrowserRuntimeProperties.class);

                    assertThat(properties.mode()).isEqualTo(BrowserMode.HEADLESS);
                    assertThat(properties.channel()).isEqualTo(BrowserChannel.MSEDGE);
                    assertThat(properties.profileRoot())
                            .isEqualTo(Path.of("build/test-profiles").toAbsolutePath().normalize());
                });
    }

    @Test
    void rejectsUnsupportedMode() {
        contextRunner
                .withPropertyValues("browser.bridge.mode=stealth")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsUnsupportedChannel() {
        contextRunner
                .withPropertyValues("browser.bridge.channel=chromium")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsBlankProfileRoot() {
        assertThatThrownBy(() -> new BrowserRuntimeProperties(
                BrowserMode.HEADED, BrowserChannel.CHROME, Path.of("")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(BrowserRuntimeProperties.class)
    static class PropertiesConfiguration {
    }
}
