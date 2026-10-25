package dev.w0fv1.vaadmin.remotebrowser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public enum BrowserChannel {
    CHROME(
            "Google Chrome",
            List.of("Google", "Chrome", "Application", "chrome.exe"),
            List.of(
                    "/usr/bin/google-chrome",
                    "/usr/bin/google-chrome-stable",
                    "/usr/bin/chromium",
                    "/usr/bin/chromium-browser",
                    "/opt/google/chrome/chrome",
                    "/snap/bin/chromium",
                    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome")),
    MSEDGE(
            "Microsoft Edge",
            List.of("Microsoft", "Edge", "Application", "msedge.exe"),
            List.of(
                    "/usr/bin/microsoft-edge",
                    "/usr/bin/microsoft-edge-stable",
                    "/opt/microsoft/msedge/msedge",
                    "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge"));

    private static final List<String> WINDOWS_INSTALL_ROOTS = List.of(
            "ProgramFiles", "ProgramFiles(x86)", "LOCALAPPDATA");

    private final String displayName;
    private final List<String> windowsRelativePath;
    private final List<String> posixPaths;

    BrowserChannel(String displayName, List<String> windowsRelativePath, List<String> posixPaths) {
        this.displayName = displayName;
        this.windowsRelativePath = windowsRelativePath;
        this.posixPaths = posixPaths;
    }

    public String displayName() {
        return displayName;
    }

    public Path executable() {
        for (Path candidate : candidates()) {
            if (Files.isRegularFile(candidate)) return candidate.toAbsolutePath().normalize();
        }
        throw new IllegalStateException("未找到 " + displayName + " 安装，请配置 browser.remote.executable");
    }

    private List<Path> candidates() {
        if (!isWindows()) {
            return posixPaths.stream().map(Path::of).toList();
        }
        List<Path> resolved = new ArrayList<>(WINDOWS_INSTALL_ROOTS.size());
        for (String root : WINDOWS_INSTALL_ROOTS) {
            String directory = System.getenv(root);
            if (directory == null || directory.isBlank()) continue;
            Path candidate = Path.of(directory);
            for (String segment : windowsRelativePath) candidate = candidate.resolve(segment);
            resolved.add(candidate);
        }
        return resolved;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().startsWith("windows");
    }
}
