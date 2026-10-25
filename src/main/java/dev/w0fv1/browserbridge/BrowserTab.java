package dev.w0fv1.browserbridge;

import com.google.gson.JsonObject;
import com.microsoft.playwright.CDPSession;
import com.microsoft.playwright.Page;

import java.util.Base64;

final class BrowserTab {
    private final String id;
    private final Page page;
    private final CDPSession cdp;
    private final BrowserViewport viewport;
    private String title = "";
    private String url = "about:blank";
    private boolean loading;
    private String error = "";

    BrowserTab(String id, Page page, CDPSession cdp, BrowserMode mode) {
        this.id = id;
        this.page = page;
        this.cdp = cdp;
        this.viewport = new BrowserViewport(mode, page, cdp);
    }

    String id() {
        return id;
    }

    Page page() {
        return page;
    }

    String title() {
        return title;
    }

    String url() {
        return url;
    }

    boolean loading() {
        return loading;
    }

    String error() {
        return error;
    }

    boolean refresh() {
        String previousUrl = url;
        String previousTitle = title;
        try {
            url = page.url();
        } catch (RuntimeException ignored) {
        }
        try {
            title = page.title();
        } catch (RuntimeException ignored) {
        }
        return !previousUrl.equals(url) || !previousTitle.equals(title);
    }

    void startNavigation(String target) {
        url = target;
        title = "";
        loading = true;
        error = "";
    }

    void markLoaded() {
        loading = false;
        error = "";
    }

    void markNavigationFailure(String message) {
        loading = false;
        error = message;
    }

    void resize(int width, int height) {
        viewport.resize(width, height);
    }

    byte[] capture(int quality) {
        JsonObject parameters = new JsonObject();
        parameters.addProperty("format", "jpeg");
        parameters.addProperty("quality", quality);
        parameters.addProperty("fromSurface", true);
        JsonObject result = cdp.send("Page.captureScreenshot", parameters);
        return Base64.getDecoder().decode(result.get("data").getAsString());
    }

    double mapX(double x, int viewportWidth) {
        return viewport.mapX(x, viewportWidth);
    }

    double mapY(double y, int viewportHeight) {
        return viewport.mapY(y, viewportHeight);
    }
}
