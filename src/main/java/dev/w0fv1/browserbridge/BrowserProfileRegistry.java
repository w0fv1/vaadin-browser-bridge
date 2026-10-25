package dev.w0fv1.browserbridge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

final class BrowserProfileRegistry {
    private static final Pattern PROFILE_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private final Path root;
    private final Set<Path> activeProfiles = ConcurrentHashMap.newKeySet();

    BrowserProfileRegistry(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    BrowserProfileLease acquire(String profileId) {
        if (profileId == null || !PROFILE_ID.matcher(profileId).matches()) {
            throw new IllegalArgumentException("浏览器配置标识无效");
        }
        Path directory = root.resolve(profileId).normalize();
        if (!directory.startsWith(root) || directory.equals(root)) {
            throw new IllegalArgumentException("浏览器配置路径无效");
        }
        if (!activeProfiles.add(directory)) {
            throw new IllegalStateException("浏览器配置目录正在使用: " + profileId);
        }
        try {
            Files.createDirectories(directory);
            return new BrowserProfileLease(profileId, directory, () -> activeProfiles.remove(directory));
        } catch (IOException | RuntimeException failure) {
            activeProfiles.remove(directory);
            throw new IllegalStateException("无法创建浏览器配置目录: " + profileId, failure);
        }
    }
}
