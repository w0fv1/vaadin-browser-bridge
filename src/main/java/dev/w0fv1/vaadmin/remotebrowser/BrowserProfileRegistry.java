package dev.w0fv1.vaadmin.remotebrowser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

final class BrowserProfileRegistry {
    private static final Logger log = LoggerFactory.getLogger(BrowserProfileRegistry.class);
    private static final String PRIVATE_DIRECTORY = "private";

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

    BrowserProfileLease acquirePrivate(BrowserEnvironmentProfile template) {
        BrowserProfileId identifier = new BrowserProfileId("private-" + UUID.randomUUID());
        Path directory = root.resolve(PRIVATE_DIRECTORY).resolve(identifier.value()).normalize();
        if (!directory.startsWith(root)) {
            throw new IllegalArgumentException("浏览器配置路径无效");
        }
        if (!activeProfiles.add(directory)) {
            throw new IllegalStateException("浏览器配置目录正在使用: " + identifier.value());
        }
        try {
            Files.createDirectories(directory);
            return new BrowserProfileLease(
                    directory,
                    copyFor(identifier, template),
                    () -> {
                        activeProfiles.remove(directory);
                        delete(directory);
                    });
        } catch (IOException | RuntimeException failure) {
            activeProfiles.remove(directory);
            throw new IllegalStateException("无法创建浏览器私密配置目录: " + identifier.value(), failure);
        }
    }

    private static BrowserEnvironmentProfile copyFor(
            BrowserProfileId identifier,
            BrowserEnvironmentProfile template) {
        if (template.mode() == BrowserEnvironmentMode.NATIVE) {
            return BrowserEnvironmentProfile.nativeProfile(identifier);
        }
        return BrowserEnvironmentProfile.managedProfile(
                identifier,
                template.locale(),
                template.timezone(),
                template.colorScheme(),
                template.deviceScaleFactor(),
                template.permissions());
    }

    private static void delete(Path directory) {
        if (!Files.isDirectory(directory)) return;
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException failure) {
                    log.warn("无法清理浏览器私密配置文件: {}", path);
                }
            });
        } catch (IOException failure) {
            log.warn("无法清理浏览器私密配置目录: {}", directory);
        }
    }
}
