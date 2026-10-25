package dev.w0fv1.browserbridge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class BrowserProfileRegistry {
    private final Path root;
    private final BrowserEnvironmentStore environments;
    private final Set<Path> activeProfiles = ConcurrentHashMap.newKeySet();

    BrowserProfileRegistry(Path root, BrowserEnvironmentStore environments) {
        this.root = root.toAbsolutePath().normalize();
        this.environments = environments;
    }

    BrowserProfileLease acquire(String profileId) {
        BrowserProfileId identifier = new BrowserProfileId(profileId);
        Path directory = root.resolve(identifier.value()).normalize();
        if (!directory.startsWith(root) || directory.equals(root)) {
            throw new IllegalArgumentException("浏览器配置路径无效");
        }
        BrowserEnvironmentProfile environment = environments.read(identifier);
        if (!activeProfiles.add(directory)) {
            throw new IllegalStateException("浏览器配置目录正在使用: " + profileId);
        }
        try {
            Files.createDirectories(directory);
            return new BrowserProfileLease(
                    directory, environment, () -> activeProfiles.remove(directory));
        } catch (IOException | RuntimeException failure) {
            activeProfiles.remove(directory);
            throw new IllegalStateException("无法创建浏览器配置目录: " + profileId, failure);
        }
    }
}
