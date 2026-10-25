package dev.w0fv1.remotebrowser;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Files;
import java.nio.file.Path;

@ConfigurationProperties(prefix = "browser.remote")
public record BrowserRuntimeProperties(
        BrowserMode mode,
        BrowserChannel channel,
        Path profileRoot,
        Path executable) {
    public BrowserRuntimeProperties {
        if (mode == null) {
            mode = BrowserMode.HEADED;
        }
        if (channel == null) {
            channel = BrowserChannel.CHROME;
        }
        Path defaultProfileRoot = Path.of(
                System.getProperty("user.home"), ".vaadin-remote-browser", "profiles");
        Path configuredProfileRoot = defaultProfileRoot;
        if (profileRoot != null) {
            configuredProfileRoot = profileRoot;
        }
        if (configuredProfileRoot.toString().isBlank()) {
            throw new IllegalArgumentException("浏览器配置目录不能为空");
        }
        profileRoot = configuredProfileRoot.toAbsolutePath().normalize();
        if (executable != null) {
            Path resolved = executable.toAbsolutePath().normalize();
            if (!Files.isRegularFile(resolved)) {
                throw new IllegalArgumentException("浏览器可执行文件不存在: " + resolved);
            }
            executable = resolved;
        }
    }
}
