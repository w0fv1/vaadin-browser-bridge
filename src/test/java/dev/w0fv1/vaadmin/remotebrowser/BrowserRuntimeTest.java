package dev.w0fv1.vaadmin.remotebrowser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

final class BrowserRuntimeTest {
    @TempDir
    Path profileRoot;

    @Test
    void launchesNativeChromeWithoutAutomationFlags() {
        BrowserRuntime runtime = runtime(BrowserMode.HEADED);
        Path directory = profileRoot.resolve("default");

        List<String> arguments = runtime.arguments(
                directory, BrowserEnvironmentProfile.nativeProfile(BrowserProfileId.DEFAULT));

        assertThat(arguments).containsExactly(
                "--user-data-dir=" + directory,
                "--remote-debugging-port=0",
                "--remote-allow-origins=*",
                "--no-first-run",
                "--no-default-browser-check",
                "--disable-backgrounding-occluded-windows",
                "--disable-renderer-backgrounding",
                "--disable-background-timer-throttling",
                "--disable-blink-features=AutomationControlled",
                "--window-size=1280,720");
        assertThat(arguments).noneMatch(argument -> argument.startsWith("--enable-automation"));
        assertThat(arguments).noneMatch(argument -> argument.contains("no-sandbox"));
    }

    @Test
    void headlessModeAddsNativeHeadlessFlag() {
        List<String> arguments = runtime(BrowserMode.HEADLESS).arguments(
                profileRoot.resolve("headless"),
                BrowserEnvironmentProfile.nativeProfile(BrowserProfileId.DEFAULT));

        assertThat(arguments).contains("--headless=new");
        assertThat(arguments).noneMatch(argument -> argument.startsWith("--accept-lang="));
    }

    @Test
    void managedEnvironmentDrivesLocaleAndScaleFactor() {
        BrowserEnvironmentProfile environment = BrowserEnvironmentProfile.managedProfile(
                new BrowserProfileId("managed"),
                Locale.forLanguageTag("fr-CA"),
                ZoneId.of("America/Toronto"),
                BrowserColorScheme.DARK,
                1.25,
                Set.of(BrowserPermission.GEOLOCATION));

        List<String> arguments = runtime(BrowserMode.HEADED).arguments(
                profileRoot.resolve("managed"), environment);

        assertThat(arguments).contains("--accept-lang=fr-CA,fr", "--force-device-scale-factor=1.25");
        assertThat(arguments).noneMatch(argument -> argument.startsWith("--enable-automation"));
    }

    @Test
    void keepsChromiumSandboxUnlessExplicitlyDisabled() {
        assertThat(runtime(BrowserMode.HEADLESS).arguments(
                        profileRoot.resolve("sandboxed"),
                        BrowserEnvironmentProfile.nativeProfile(BrowserProfileId.DEFAULT)))
                .noneMatch(argument -> argument.equals("--no-sandbox"));

        BrowserRuntimeProperties properties = new BrowserRuntimeProperties(
                BrowserMode.HEADLESS, BrowserChannel.CHROME, profileRoot, null, true, false, null, null);
        assertThat(new BrowserRuntime(properties, new ObjectMapper()).arguments(
                        profileRoot.resolve("unsandboxed"),
                        BrowserEnvironmentProfile.nativeProfile(BrowserProfileId.DEFAULT)))
                .contains("--no-sandbox");
    }

    @Test
    void resolvesConfiguredExecutable() {
        Path chrome = BrowserChannel.CHROME.executable();
        BrowserRuntimeProperties properties = new BrowserRuntimeProperties(
                BrowserMode.HEADED, BrowserChannel.MSEDGE, profileRoot, chrome);
        BrowserRuntime runtime = new BrowserRuntime(properties, new ObjectMapper());

        assertThat(runtime.executable()).isEqualTo(chrome);
        assertThat(chrome).isRegularFile();
    }

    private BrowserRuntime runtime(BrowserMode mode) {
        BrowserRuntimeProperties properties = new BrowserRuntimeProperties(
                mode, BrowserChannel.CHROME, profileRoot, null);
        return new BrowserRuntime(properties, new ObjectMapper());
    }
}
