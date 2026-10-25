package dev.w0fv1.browserbridge;

public enum BrowserChannel {
    CHROME("chrome", "Google Chrome"),
    MSEDGE("msedge", "Microsoft Edge");

    private final String playwrightName;
    private final String displayName;

    BrowserChannel(String playwrightName, String displayName) {
        this.playwrightName = playwrightName;
        this.displayName = displayName;
    }

    public String playwrightName() {
        return playwrightName;
    }

    public String displayName() {
        return displayName;
    }
}
