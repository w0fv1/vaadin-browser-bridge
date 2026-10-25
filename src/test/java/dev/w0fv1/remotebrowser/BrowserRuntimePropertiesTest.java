package dev.w0fv1.remotebrowser;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class BrowserRuntimePropertiesTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void defaultsToHeadedChromeAndPersistentProfileRoot() {
        BrowserRuntimeProperties properties = new BrowserRuntimeProperties(null, null, null, null);

        assertThat(properties.mode()).isEqualTo(BrowserMode.HEADED);
        assertThat(properties.channel()).isEqualTo(BrowserChannel.CHROME);
        assertThat(properties.profileRoot()).isEqualTo(Path.of(
                System.getProperty("user.home"), ".vaadin-remote-browser", "profiles"));
        assertThat(properties.executable()).isNull();
    }

    @Test
    void bindsHeadlessEdgeAndNormalizedProfileRoot() {
        contextRunner
                .withPropertyValues(
                        "browser.remote.mode=headless",
                        "browser.remote.channel=msedge",
                        "browser.remote.profile-root=build/test-profiles")
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
                .withPropertyValues("browser.remote.mode=stealth")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsUnsupportedChannel() {
        contextRunner
                .withPropertyValues("browser.remote.channel=chromium")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsBlankProfileRoot() {
        assertThatThrownBy(() -> new BrowserRuntimeProperties(
                BrowserMode.HEADED, BrowserChannel.CHROME, Path.of(""), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingExecutable() {
        assertThatThrownBy(() -> new BrowserRuntimeProperties(
                BrowserMode.HEADED, BrowserChannel.CHROME, null, Path.of("missing-browser.exe")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("可执行文件");
    }

    @Test
    void normalizesConfiguredExecutable(@org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        Path executable = Files.createFile(directory.resolve("browser.exe"));

        BrowserRuntimeProperties properties = new BrowserRuntimeProperties(
                BrowserMode.HEADED, BrowserChannel.CHROME, null, executable);

        assertThat(properties.executable()).isEqualTo(executable.toAbsolutePath().normalize());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(BrowserRuntimeProperties.class)
    static class PropertiesConfiguration {
    }
}
