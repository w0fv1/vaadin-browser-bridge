package dev.w0fv1.browserbridge;

public enum BrowserColorScheme {
    LIGHT("light"),
    DARK("dark"),
    NO_PREFERENCE("");

    private final String mediaValue;

    BrowserColorScheme(String mediaValue) {
        this.mediaValue = mediaValue;
    }

    String mediaValue() {
        return mediaValue;
    }

    boolean overriding() {
        return !mediaValue.isEmpty();
    }
}
