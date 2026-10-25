package dev.w0fv1.browserbridge;

import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.options.ColorScheme;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

final class BrowserRuntimeTest {
    @TempDir
    Path profileRoot;

    @Test
    void usesStandardChromeFlagsInHeadlessMode() {
        BrowserRuntimeProperties properties = new BrowserRuntimeProperties(
                BrowserMode.HEADLESS, BrowserChannel.CHROME, profileRoot);
        BrowserEnvironmentProfile environment = BrowserEnvironmentProfile.nativeProfile(BrowserProfileId.DEFAULT);

        try (BrowserRuntime runtime = new BrowserRuntime(properties)) {
            BrowserType.LaunchPersistentContextOptions options = runtime.launchOptions(environment);

            assertThat(options.headless).isTrue();
            assertThat(options.channel).isEqualTo("chrome");
            assertThat(options.chromiumSandbox).isTrue();
            assertThat(options.ignoreDefaultArgs).isNull();
            assertThat(options.args).containsExactly("--no-first-run", "--no-default-browser-check");
            assertThat(options.viewportSize).isEmpty();
            assertThat(options.locale).isNull();
            assertThat(options.timezoneId).isNull();
            assertThat(options.colorScheme).isNull();
            assertThat(options.deviceScaleFactor).isNull();
            assertThat(options.permissions).isNull();
            runtime.close();
            runtime.close();
        }
    }

    @Test
    void mapsEdgeChannelToItsPlaywrightName() {
        BrowserRuntimeProperties properties = new BrowserRuntimeProperties(
                BrowserMode.HEADED, BrowserChannel.MSEDGE, profileRoot);
        BrowserEnvironmentProfile environment = BrowserEnvironmentProfile.nativeProfile(BrowserProfileId.DEFAULT);

        try (BrowserRuntime runtime = new BrowserRuntime(properties)) {
            assertThat(runtime.launchOptions(environment).channel).isEqualTo("msedge");
            assertThat(runtime.launchOptions(environment).headless).isFalse();
        }
    }

    @Test
    void appliesManagedEnvironmentWithoutOverridingRealViewport() {
        BrowserRuntimeProperties properties = new BrowserRuntimeProperties(
                BrowserMode.HEADED, BrowserChannel.CHROME, profileRoot);
        BrowserEnvironmentProfile environment = BrowserEnvironmentProfile.managedProfile(
                new BrowserProfileId("managed"),
                Locale.forLanguageTag("fr-CA"),
                ZoneId.of("America/Toronto"),
                ColorScheme.DARK,
                1.25,
                Set.of(BrowserPermission.GEOLOCATION, BrowserPermission.CLIPBOARD_READ));

        try (BrowserRuntime runtime = new BrowserRuntime(properties)) {
            BrowserType.LaunchPersistentContextOptions options = runtime.launchOptions(environment);

            assertThat(options.locale).isEqualTo("fr-CA");
            assertThat(options.timezoneId).isEqualTo("America/Toronto");
            assertThat(options.colorScheme).contains(ColorScheme.DARK);
            assertThat(options.deviceScaleFactor).isNull();
            assertThat(options.permissions).containsExactly("geolocation", "clipboard-read");
            assertThat(options.args).contains("--force-device-scale-factor=1.25");
            assertThat(options.viewportSize).isEmpty();
        }
    }
}
