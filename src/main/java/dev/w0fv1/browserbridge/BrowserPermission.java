package dev.w0fv1.browserbridge;

public enum BrowserPermission {
    GEOLOCATION("geolocation"),
    CLIPBOARD_READ("clipboard-read"),
    CLIPBOARD_WRITE("clipboard-write"),
    NOTIFICATIONS("notifications"),
    CAMERA("camera"),
    MICROPHONE("microphone");

    private final String value;

    BrowserPermission(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
