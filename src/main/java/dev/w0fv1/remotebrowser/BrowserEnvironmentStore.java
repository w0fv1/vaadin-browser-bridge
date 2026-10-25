package dev.w0fv1.remotebrowser;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

final class BrowserEnvironmentStore {
    private static final String MANIFEST_FILE = "environment.json";

    private final Path profileRoot;
    private final ObjectMapper mapper;

    public BrowserEnvironmentStore(BrowserRuntimeProperties properties, ObjectMapper mapper) {
        this.profileRoot = properties.profileRoot();
        this.mapper = mapper;
    }

    public List<BrowserProfileId> profileIds() {
        Set<String> identifiers = new TreeSet<>();
        identifiers.add(BrowserProfileId.DEFAULT.value());
        Path root = profileRoot;
        if (!Files.isDirectory(root)) return identifiers.stream().map(BrowserProfileId::new).toList();
        try (Stream<Path> directories = Files.list(root)) {
            directories.filter(Files::isDirectory)
                    .map(directory -> directory.getFileName().toString())
                    .filter(BrowserProfileId::isValid)
                    .filter(name -> Files.isRegularFile(manifestPath(new BrowserProfileId(name))))
                    .forEach(identifiers::add);
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取浏览器配置目录", exception);
        }
        return identifiers.stream().map(BrowserProfileId::new).toList();
    }

    public BrowserEnvironmentProfile read(BrowserProfileId profileId) {
        Path manifest = manifestPath(profileId);
        if (!Files.isRegularFile(manifest)) return BrowserEnvironmentProfile.nativeProfile(profileId);
        StoredEnvironmentProfile stored;
        try {
            stored = mapper.readValue(Files.readString(manifest), StoredEnvironmentProfile.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("浏览器环境画像 JSON 无效：" + manifest, exception);
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取浏览器环境画像：" + manifest, exception);
        }
        if (!profileId.value().equals(stored.profileId())) {
            throw new IllegalArgumentException("浏览器环境画像标识与目录不一致：" + manifest);
        }
        if (stored.mode() == null) throw new IllegalArgumentException("浏览器环境画像缺少运行模式：" + manifest);
        if (stored.mode() == BrowserEnvironmentMode.NATIVE) {
            if (stored.locale() != null || stored.timezone() != null || stored.colorScheme() != null
                    || stored.deviceScaleFactor() != 0
                    || (stored.permissions() != null && !stored.permissions().isEmpty())) {
                throw new IllegalArgumentException("原生模式不能覆盖浏览器环境：" + manifest);
            }
            return BrowserEnvironmentProfile.nativeProfile(profileId);
        }
        if (stored.locale() == null || stored.locale().isBlank()) {
            throw new IllegalArgumentException("浏览器环境画像缺少语言：" + manifest);
        }
        Locale locale = Locale.forLanguageTag(stored.locale());
        if (locale.getLanguage().isEmpty() || "und".equals(locale.getLanguage())) {
            throw new IllegalArgumentException("浏览器环境画像语言无效：" + manifest);
        }
        if (stored.timezone() == null || stored.timezone().isBlank()) {
            throw new IllegalArgumentException("浏览器环境画像缺少时区：" + manifest);
        }
        ZoneId timezone;
        try {
            timezone = ZoneId.of(stored.timezone());
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("浏览器环境画像时区无效：" + manifest, exception);
        }
        if (stored.colorScheme() == null || stored.colorScheme().isBlank()) {
            throw new IllegalArgumentException("浏览器环境画像缺少色彩方案：" + manifest);
        }
        BrowserColorScheme colorScheme;
        try {
            colorScheme = BrowserColorScheme.valueOf(stored.colorScheme());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("浏览器环境画像色彩方案无效：" + manifest, exception);
        }
        Set<BrowserPermission> permissions = stored.permissions() == null
                ? Set.of()
                : Set.copyOf(stored.permissions());
        return BrowserEnvironmentProfile.managedProfile(
                profileId, locale, timezone, colorScheme, stored.deviceScaleFactor(), permissions);
    }

    public void save(BrowserEnvironmentProfile profile) {
        Path directory = profileRoot.resolve(profile.profileId().value());
        Path manifest = directory.resolve(MANIFEST_FILE);
        byte[] content;
        try {
            content = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(toStored(profile));
        } catch (JacksonException exception) {
            throw new IllegalStateException("无法序列化浏览器环境画像", exception);
        }
        Path temporary;
        try {
            Files.createDirectories(directory);
            temporary = Files.createTempFile(directory, "environment-", ".json");
        } catch (IOException exception) {
            throw new IllegalStateException("无法创建浏览器环境画像目录", exception);
        }
        try {
            Files.write(temporary, content);
            try {
                Files.move(temporary, manifest,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, manifest, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("无法写入浏览器环境画像：" + manifest, exception);
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
            }
        }
    }

    private Path manifestPath(BrowserProfileId profileId) {
        return profileRoot.resolve(profileId.value()).resolve(MANIFEST_FILE);
    }

    private static StoredEnvironmentProfile toStored(BrowserEnvironmentProfile profile) {
        return new StoredEnvironmentProfile(
                profile.profileId().value(),
                profile.mode(),
                profile.locale() == null ? null : profile.locale().toLanguageTag(),
                profile.timezone() == null ? null : profile.timezone().getId(),
                profile.colorScheme() == null ? null : profile.colorScheme().name(),
                profile.deviceScaleFactor(),
                profile.permissions());
    }

    private record StoredEnvironmentProfile(
            String profileId,
            BrowserEnvironmentMode mode,
            String locale,
            String timezone,
            String colorScheme,
            double deviceScaleFactor,
            Set<BrowserPermission> permissions) {
    }
}
