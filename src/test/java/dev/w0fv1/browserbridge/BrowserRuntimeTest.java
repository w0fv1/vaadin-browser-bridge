package dev.w0fv1.browserbridge;

import com.microsoft.playwright.BrowserType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

final class BrowserRuntimeTest {
    @TempDir
    Path profileRoot;

    @Test
    void usesStandardChromeFlagsInHeadlessMode() {
        BrowserRuntimeProperties properties = new BrowserRuntimeProperties(
                BrowserMode.HEADLESS, BrowserChannel.CHROME, profileRoot);

        try (BrowserRuntime runtime = new BrowserRuntime(properties)) {
            BrowserType.LaunchPersistentContextOptions options = runtime.launchOptions();

            assertThat(options.headless).isTrue();
            assertThat(options.channel).isEqualTo("chrome");
            assertThat(options.chromiumSandbox).isTrue();
            assertThat(options.ignoreDefaultArgs).isNull();
            assertThat(options.args).containsExactly("--no-first-run", "--no-default-browser-check");
            assertThat(options.viewportSize).isEmpty();
            runtime.close();
            runtime.close();
        }
    }

    @Test
    void mapsEdgeChannelToItsPlaywrightName() {
        BrowserRuntimeProperties properties = new BrowserRuntimeProperties(
                BrowserMode.HEADED, BrowserChannel.MSEDGE, profileRoot);

        try (BrowserRuntime runtime = new BrowserRuntime(properties)) {
            assertThat(runtime.launchOptions().channel).isEqualTo("msedge");
            assertThat(runtime.launchOptions().headless).isFalse();
        }
    }
}
