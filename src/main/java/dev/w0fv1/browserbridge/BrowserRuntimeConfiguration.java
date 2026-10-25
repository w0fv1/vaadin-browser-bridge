package dev.w0fv1.browserbridge;

import com.microsoft.playwright.BrowserType;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

record BrowserRuntimeConfiguration(boolean headless, String channel, Path profileRoot) {
    private static final Set<String> CHANNELS = Set.of("chrome", "msedge");
    private static final Pattern PROFILE_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    static BrowserRuntimeConfiguration fromSystem() {
        return from(Map.of(
                "browser.bridge.mode", configuredValue(
                        "browser.bridge.mode", "BROWSER_BRIDGE_MODE", "headed"),
                "browser.bridge.channel", configuredValue(
                        "browser.bridge.channel", "BROWSER_BRIDGE_CHANNEL", "chrome"),
                "browser.bridge.profile-root", configuredValue(
                        "browser.bridge.profile-root", "BROWSER_BRIDGE_PROFILE_ROOT",
                        Path.of(System.getProperty("user.home"), ".browser-bridge", "profiles").toString())));
    }

    static BrowserRuntimeConfiguration from(Map<String, String> properties) {
        String mode = properties.getOrDefault("browser.bridge.mode", "headed")
                .trim()
                .toLowerCase(Locale.ROOT);
        boolean headless = switch (mode) {
            case "headless" -> true;
            case "headed" -> false;
            default -> throw new IllegalArgumentException("不支持的浏览器运行模式");
        };
        String channel = properties.getOrDefault("browser.bridge.channel", "chrome")
                .trim()
                .toLowerCase(Locale.ROOT);
        if (!CHANNELS.contains(channel)) throw new IllegalArgumentException("不支持的浏览器通道");
        String profileRoot = properties.getOrDefault(
                        "browser.bridge.profile-root",
                        Path.of(System.getProperty("user.home"), ".browser-bridge", "profiles").toString())
                .trim();
        if (profileRoot.isEmpty()) throw new IllegalArgumentException("浏览器配置目录不能为空");
        return new BrowserRuntimeConfiguration(headless, channel, Path.of(profileRoot).toAbsolutePath().normalize());
    }

    BrowserType.LaunchPersistentContextOptions launchOptions() {
        BrowserType.LaunchPersistentContextOptions options = new BrowserType.LaunchPersistentContextOptions()
                .setHeadless(headless)
                .setChannel(channel)
                .setChromiumSandbox(true)
                .setIgnoreDefaultArgs(List.of("--enable-automation", "--no-sandbox", "--enable-unsafe-swiftshader"))
                .setArgs(List.of("--no-first-run", "--no-default-browser-check"));
        options.viewportSize = Optional.empty();
        return options;
    }

    Path profileDirectory(String profileId) {
        if (profileId == null || !PROFILE_ID.matcher(profileId).matches()) {
            throw new IllegalArgumentException("浏览器配置标识无效");
        }
        return profileRoot.resolve(profileId);
    }

    private static String configuredValue(String property, String environment, String fallback) {
        String propertyValue = System.getProperty(property);
        if (propertyValue != null && !propertyValue.isBlank()) return propertyValue;
        String environmentValue = System.getenv(environment);
        return environmentValue == null || environmentValue.isBlank() ? fallback : environmentValue;
    }
}
