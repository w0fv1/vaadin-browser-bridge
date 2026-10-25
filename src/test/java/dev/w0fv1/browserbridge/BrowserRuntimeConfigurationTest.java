package dev.w0fv1.browserbridge;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BrowserRuntimeConfigurationTest {
    @Test
    void defaultsToRealChromeWithPersistentProfile() {
        BrowserRuntimeConfiguration configuration = BrowserRuntimeConfiguration.from(Map.of());

        assertFalse(configuration.headless());
        assertEquals("chrome", configuration.channel());
        assertEquals(
                Path.of(System.getProperty("user.home"), ".browser-bridge", "profiles")
                        .toAbsolutePath().normalize(),
                configuration.profileRoot());
        assertFalse(configuration.launchOptions().headless);
        assertEquals("chrome", configuration.launchOptions().channel);
        assertEquals(
                List.of("--enable-automation", "--no-sandbox", "--enable-unsafe-swiftshader"),
                configuration.launchOptions().ignoreDefaultArgs);
        assertEquals(
                List.of("--no-first-run", "--no-default-browser-check"),
                configuration.launchOptions().args);
        assertTrue(configuration.launchOptions().viewportSize.isEmpty());
    }

    @Test
    void supportsHeadlessRealChromeWhenExplicitlyRequested() {
        BrowserRuntimeConfiguration configuration = BrowserRuntimeConfiguration.from(Map.of(
                "browser.bridge.mode", "headless",
                "browser.bridge.channel", "chrome"));

        assertTrue(configuration.headless());
        assertTrue(configuration.launchOptions().headless);
    }

    @Test
    void rejectsBundledChromiumAndUnsupportedValues() {
        assertThrows(IllegalArgumentException.class, () -> BrowserRuntimeConfiguration.from(Map.of(
                "browser.bridge.mode", "stealth")));
        assertThrows(IllegalArgumentException.class, () -> BrowserRuntimeConfiguration.from(Map.of(
                "browser.bridge.channel", "chromium")));
    }

    @Test
    void resolvesOnlySafeProfileIdentifiers() {
        BrowserRuntimeConfiguration configuration = BrowserRuntimeConfiguration.from(Map.of(
                "browser.bridge.profile-root", "build/profiles"));

        assertEquals(
                Path.of("build/profiles/account-1").toAbsolutePath().normalize(),
                configuration.profileDirectory("account-1"));
        assertThrows(IllegalArgumentException.class, () -> configuration.profileDirectory("../escape"));
        assertThrows(IllegalArgumentException.class, () -> configuration.profileDirectory(""));
        assertThrows(IllegalArgumentException.class, () -> configuration.profileDirectory("a".repeat(65)));
    }
}
