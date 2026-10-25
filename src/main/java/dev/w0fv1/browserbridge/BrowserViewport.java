package dev.w0fv1.browserbridge;

import com.google.gson.JsonObject;
import com.microsoft.playwright.CDPSession;
import com.microsoft.playwright.Page;

final class BrowserViewport {
    private final BrowserMode mode;
    private final Page page;
    private final CDPSession cdp;
    private int clientWidth;
    private int clientHeight;
    private int appliedWidth;
    private int appliedHeight;
    private int lockedOuterWidth;
    private int lockedOuterHeight;

    BrowserViewport(BrowserMode mode, Page page, CDPSession cdp) {
        this.mode = mode;
        this.page = page;
        this.cdp = cdp;
    }

    void resize(int targetWidth, int targetHeight) {
        if (mode.headless()) {
            resizeHeadless(targetWidth, targetHeight);
            return;
        }
        resizeHeaded(targetWidth, targetHeight);
    }

    double mapX(double x, int viewportWidth) {
        if (viewportWidth <= 0 || clientWidth <= 0) {
            return x;
        }
        return x * clientWidth / viewportWidth;
    }

    double mapY(double y, int viewportHeight) {
        if (viewportHeight <= 0 || clientHeight <= 0) {
            return y;
        }
        return y * clientHeight / viewportHeight;
    }

    private void resizeHeadless(int targetWidth, int targetHeight) {
        if (appliedWidth == targetWidth && appliedHeight == targetHeight) return;
        page.setViewportSize(targetWidth, targetHeight);
        readViewportMetrics();
        appliedWidth = targetWidth;
        appliedHeight = targetHeight;
    }

    private void resizeHeaded(int targetWidth, int targetHeight) {
        readViewportMetrics();
        JsonObject window = cdp.send("Browser.getWindowForTarget");
        int windowId = window.get("windowId").getAsInt();
        JsonObject bounds = window.getAsJsonObject("bounds");
        String windowState = "normal";
        if (bounds.has("windowState") && !bounds.get("windowState").isJsonNull()) {
            windowState = bounds.get("windowState").getAsString();
        }
        int outerWidth = bounds.get("width").getAsInt();
        int outerHeight = bounds.get("height").getAsInt();
        boolean viewportChanged = appliedWidth != targetWidth || appliedHeight != targetHeight;
        boolean windowDrifted = !"normal".equals(windowState)
                || (lockedOuterWidth > 0 && Math.abs(outerWidth - lockedOuterWidth) > 2)
                || (lockedOuterHeight > 0 && Math.abs(outerHeight - lockedOuterHeight) > 2);
        if (!viewportChanged && !windowDrifted) return;

        if (!"normal".equals(windowState)) {
            JsonObject normalBounds = new JsonObject();
            normalBounds.addProperty("windowState", "normal");
            setWindowBounds(windowId, normalBounds);
            bounds = cdp.send("Browser.getWindowForTarget").getAsJsonObject("bounds");
            outerWidth = bounds.get("width").getAsInt();
            outerHeight = bounds.get("height").getAsInt();
            readViewportMetrics();
        }

        int insetWidth = 16;
        if (clientWidth > 0) {
            insetWidth = Math.max(0, outerWidth - clientWidth);
        }
        int insetHeight = 88;
        if (clientHeight > 0) {
            insetHeight = Math.max(0, outerHeight - clientHeight);
        }
        JsonObject targetBounds = new JsonObject();
        targetBounds.addProperty("windowState", "normal");
        targetBounds.addProperty("width", targetWidth + insetWidth);
        targetBounds.addProperty("height", targetHeight + insetHeight);
        setWindowBounds(windowId, targetBounds);
        readViewportMetrics();
        if (clientWidth != targetWidth || clientHeight != targetHeight) {
            targetBounds.addProperty(
                    "width",
                    targetBounds.get("width").getAsInt() + targetWidth - clientWidth);
            targetBounds.addProperty(
                    "height",
                    targetBounds.get("height").getAsInt() + targetHeight - clientHeight);
            setWindowBounds(windowId, targetBounds);
        }

        JsonObject applied = cdp.send("Browser.getWindowForTarget").getAsJsonObject("bounds");
        lockedOuterWidth = applied.get("width").getAsInt();
        lockedOuterHeight = applied.get("height").getAsInt();
        appliedWidth = targetWidth;
        appliedHeight = targetHeight;
        readViewportMetrics();
    }

    private void readViewportMetrics() {
        JsonObject cssViewport = cdp.send("Page.getLayoutMetrics").getAsJsonObject("cssVisualViewport");
        int width = cssViewport.get("clientWidth").getAsInt();
        int height = cssViewport.get("clientHeight").getAsInt();
        if (width <= 0 || height <= 0) {
            throw new IllegalStateException("浏览器视口尺寸无效");
        }
        clientWidth = width;
        clientHeight = height;
    }

    private void setWindowBounds(int windowId, JsonObject bounds) {
        JsonObject parameters = new JsonObject();
        parameters.addProperty("windowId", windowId);
        parameters.add("bounds", bounds);
        cdp.send("Browser.setWindowBounds", parameters);
    }
}
