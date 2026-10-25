package dev.w0fv1.browserbridge;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties(prefix = "browser.bridge")
public record BrowserRuntimeProperties(
        BrowserMode mode,
        BrowserChannel channel,
        Path profileRoot) {
    public BrowserRuntimeProperties {
        mode = mode == null ? BrowserMode.HEADED : mode;
        channel = channel == null ? BrowserChannel.CHROME : channel;
        Path defaultProfileRoot = Path.of(
                System.getProperty("user.home"), ".browser-bridge", "profiles");
        Path configuredProfileRoot = profileRoot == null ? defaultProfileRoot : profileRoot;
        if (configuredProfileRoot.toString().isBlank()) {
            throw new IllegalArgumentException("浏览器配置目录不能为空");
        }
        profileRoot = configuredProfileRoot.toAbsolutePath().normalize();
    }
}
