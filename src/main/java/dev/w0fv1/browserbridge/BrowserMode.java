package dev.w0fv1.browserbridge;

public enum BrowserMode {
    HEADED(false),
    HEADLESS(true);

    private final boolean headless;

    BrowserMode(boolean headless) {
        this.headless = headless;
    }

    public boolean headless() {
        return headless;
    }
}
