package dev.w0fv1.vaadmin.remotebrowser;

import java.time.ZoneId;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public record BrowserEnvironmentProfile(
        BrowserProfileId profileId,
        BrowserEnvironmentMode mode,
        Locale locale,
        ZoneId timezone,
        BrowserColorScheme colorScheme,
        double deviceScaleFactor,
        Set<BrowserPermission> permissions) {
    private static final double MIN_DEVICE_SCALE_FACTOR = 0.5;
    private static final double MAX_DEVICE_SCALE_FACTOR = 4.0;

    public BrowserEnvironmentProfile {
        Objects.requireNonNull(profileId, "浏览器配置标识不能为空");
        Objects.requireNonNull(mode, "浏览器环境模式不能为空");
        if (permissions != null) permissions = Set.copyOf(permissions);
        if (mode == BrowserEnvironmentMode.NATIVE) {
            if (locale != null || timezone != null || colorScheme != null
                    || deviceScaleFactor != 0 || (permissions != null && !permissions.isEmpty())) {
                throw new IllegalArgumentException("原生模式不能覆盖浏览器环境");
            }
            permissions = Set.of();
        } else {
            if (locale == null || locale.getLanguage().isEmpty() || "und".equals(locale.getLanguage())) {
                throw new IllegalArgumentException("浏览器语言无效");
            }
            if (timezone == null) throw new IllegalArgumentException("浏览器时区不能为空");
            if (colorScheme == null) throw new IllegalArgumentException("浏览器色彩方案不能为空");
            if (!Double.isFinite(deviceScaleFactor)
                    || deviceScaleFactor < MIN_DEVICE_SCALE_FACTOR
                    || deviceScaleFactor > MAX_DEVICE_SCALE_FACTOR) {
                throw new IllegalArgumentException("浏览器设备像素比超出范围");
            }
            if (permissions == null) throw new IllegalArgumentException("浏览器权限集合不能为空");
        }
    }

    public static BrowserEnvironmentProfile nativeProfile(BrowserProfileId profileId) {
        return new BrowserEnvironmentProfile(
                profileId,
                BrowserEnvironmentMode.NATIVE,
                null,
                null,
                null,
                0,
                Set.of());
    }

    public static BrowserEnvironmentProfile managedProfile(
            BrowserProfileId profileId,
            Locale locale,
            ZoneId timezone,
            BrowserColorScheme colorScheme,
            double deviceScaleFactor,
            Set<BrowserPermission> permissions) {
        return new BrowserEnvironmentProfile(
                profileId,
                BrowserEnvironmentMode.MANAGED,
                locale,
                timezone,
                colorScheme,
                deviceScaleFactor,
                permissions);
    }
}
