package dev.w0fv1.browserbridge;

import java.util.regex.Pattern;

public record BrowserProfileId(String value) {
    private static final Pattern VALID_VALUE = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    public static final BrowserProfileId DEFAULT = new BrowserProfileId("default");

    public BrowserProfileId {
        if (!isValid(value)) {
            throw new IllegalArgumentException("浏览器配置标识无效");
        }
    }

    public static boolean isValid(String value) {
        return value != null && VALID_VALUE.matcher(value).matches();
    }

    @Override
    public String toString() {
        return value;
    }
}
