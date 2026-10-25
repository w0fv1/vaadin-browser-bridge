package dev.w0fv1.browserbridge;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public enum BrowserChannel {
    CHROME("Google Chrome", List.of("Google", "Chrome", "Application", "chrome.exe")),
    MSEDGE("Microsoft Edge", List.of("Microsoft", "Edge", "Application", "msedge.exe"));

    private static final List<String> INSTALL_ROOTS = List.of(
            "ProgramFiles", "ProgramFiles(x86)", "LOCALAPPDATA");

    private final String displayName;
    private final List<String> relativePath;

    BrowserChannel(String displayName, List<String> relativePath) {
        this.displayName = displayName;
        this.relativePath = relativePath;
    }

    public String displayName() {
        return displayName;
    }

    public Path executable() {
        for (String root : INSTALL_ROOTS) {
            String directory = System.getenv(root);
            if (directory == null || directory.isBlank()) continue;
            Path candidate = Path.of(directory);
            for (String segment : relativePath) candidate = candidate.resolve(segment);
            if (Files.isRegularFile(candidate)) return candidate.toAbsolutePath().normalize();
        }
        throw new IllegalStateException("未找到 " + displayName + " 安装，请配置 browser.bridge.executable");
    }
}
