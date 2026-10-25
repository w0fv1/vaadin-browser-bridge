package dev.w0fv1.browserbridge;

public enum BrowserPermission {
    GEOLOCATION("geolocation"),
    CLIPBOARD_READ("clipboardReadWrite"),
    CLIPBOARD_WRITE("clipboardSanitizedWrite"),
    NOTIFICATIONS("notifications"),
    CAMERA("videoCapture"),
    MICROPHONE("audioCapture");

    private final String cdpName;

    BrowserPermission(String cdpName) {
        this.cdpName = cdpName;
    }

    public String cdpName() {
        return cdpName;
    }
}
