package dev.w0fv1.vaadmin.remotebrowser;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.Base64;

final class BrowserTab {
    private final String id;
    private final String targetId;
    private final CdpSession session;
    private final CdpConnection connection;
    private final BrowserViewport viewport;
    private final ObjectMapper mapper;
    private String title = "";
    private String url = "about:blank";
    private boolean loading;
    private String error = "";

    BrowserTab(
            String id,
            String targetId,
            CdpSession session,
            CdpConnection connection,
            ObjectMapper mapper,
            BrowserMode mode,
            double deviceScaleFactor) {
        this.id = id;
        this.targetId = targetId;
        this.session = session;
        this.connection = connection;
        this.mapper = mapper;
        this.viewport = new BrowserViewport(mode, targetId, session, connection, mapper, deviceScaleFactor);
        session.call("Page.enable", null);
        session.on("Page.javascriptDialogOpening", event -> dismissDialog());
    }

    String id() {
        return id;
    }

    String targetId() {
        return targetId;
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

    void applyEnvironment(BrowserEnvironmentProfile environment) {
        BrowserPageEnvironment.apply(session, mapper, environment);
    }

    void onLoad(Runnable handler) {
        session.on("Page.loadEventFired", event -> handler.run());
    }

    boolean refresh() {
        if (loading) return false;
        String previousUrl = url;
        String previousTitle = title;
        try {
            applyTargetInfo(session.call("Target.getTargetInfo", null).path("targetInfo"));
        } catch (RuntimeException ignored) {
        }
        return !previousUrl.equals(url) || !previousTitle.equals(title);
    }

    void applyTargetInfo(JsonNode info) {
        url = info.path("url").asString(url);
        title = info.path("title").asString(title);
    }

    void startNavigation(String target) {
        url = target;
        title = "";
        loading = true;
        error = "";
    }

    void navigate(String target) {
        startNavigation(target);
        ObjectNode parameters = mapper.createObjectNode();
        parameters.put("url", target);
        String failure = session.call("Page.navigate", parameters).path("errorText").asString("");
        if (!failure.isEmpty()) markNavigationFailure(failure);
    }

    void markLoaded() {
        loading = false;
        error = "";
    }

    void markNavigationFailure(String message) {
        loading = false;
        error = message;
    }

    void activate() {
        ObjectNode parameters = mapper.createObjectNode();
        parameters.put("targetId", targetId);
        connection.call("Target.activateTarget", parameters);
    }

    void close() {
        ObjectNode parameters = mapper.createObjectNode();
        parameters.put("targetId", targetId);
        connection.call("Target.closeTarget", parameters);
    }

    void resize(int width, int height) {
        viewport.resize(width, height);
    }

    double mapX(double x, int viewportWidth) {
        return viewport.mapX(x, viewportWidth);
    }

    double mapY(double y, int viewportHeight) {
        return viewport.mapY(y, viewportHeight);
    }

    byte[] capture(int quality) {
        ObjectNode parameters = mapper.createObjectNode();
        parameters.put("format", "jpeg");
        parameters.put("quality", quality);
        parameters.put("fromSurface", true);
        JsonNode result = session.call("Page.captureScreenshot", parameters);
        return Base64.getDecoder().decode(result.path("data").asString(""));
    }

    void move(double x, double y, BrowserInput input) {
        dispatchMouse("mouseMoved", x, y, BrowserMouseButton.NONE, input.buttons(), 1, input);
    }

    void press(double x, double y, BrowserInput input) {
        dispatchMouse(
                "mousePressed",
                x,
                y,
                input.button(),
                input.buttons() == 0 ? input.button().bitmask() : input.buttons(),
                input.clickCount(),
                input);
    }

    void release(double x, double y, BrowserInput input) {
        dispatchMouse("mouseReleased", x, y, input.button(), input.buttons(), input.clickCount(), input);
    }

    void click(double x, double y, BrowserInput input) {
        dispatchMouse(
                "mousePressed",
                x,
                y,
                input.button(),
                input.button().bitmask(),
                input.clickCount(),
                input);
        dispatchMouse("mouseReleased", x, y, input.button(), 0, input.clickCount(), input);
    }

    void wheel(double x, double y, double deltaX, double deltaY) {
        ObjectNode parameters = mapper.createObjectNode();
        parameters.put("type", "mouseWheel");
        parameters.put("x", x);
        parameters.put("y", y);
        parameters.put("deltaX", deltaX);
        parameters.put("deltaY", deltaY);
        parameters.put("pointerType", "mouse");
        session.call("Input.dispatchMouseEvent", parameters);
    }

    void key(BrowserKeyInput input) {
        int modifiers = BrowserModifier.mask(input.modifiers());
        boolean printable = input.printable();
        ObjectNode parameters = mapper.createObjectNode();
        parameters.put("type", printable ? "keyDown" : "rawKeyDown");
        parameters.put("modifiers", modifiers);
        parameters.put("key", input.key());
        parameters.put("code", input.code());
        parameters.put("windowsVirtualKeyCode", input.keyCode());
        parameters.put("nativeVirtualKeyCode", input.keyCode());
        parameters.put("autoRepeat", input.repeat());
        parameters.put("isKeypad", input.keypad());
        parameters.put("location", input.location());
        if (printable) {
            parameters.put("text", input.key());
            parameters.put("unmodifiedText", input.key());
        }
        session.call("Input.dispatchKeyEvent", parameters);
        parameters.put("type", "keyUp");
        parameters.remove("text");
        parameters.remove("unmodifiedText");
        session.call("Input.dispatchKeyEvent", parameters);
    }

    void insertText(String text) {
        ObjectNode parameters = mapper.createObjectNode();
        parameters.put("text", text);
        session.call("Input.insertText", parameters);
    }

    private void dispatchMouse(
            String type,
            double x,
            double y,
            BrowserMouseButton button,
            int buttons,
            int clickCount,
            BrowserInput input) {
        ObjectNode parameters = mapper.createObjectNode();
        parameters.put("type", type);
        parameters.put("x", x);
        parameters.put("y", y);
        parameters.put("button", button.cdpName());
        parameters.put("buttons", buttons);
        parameters.put("clickCount", clickCount);
        parameters.put("modifiers", BrowserModifier.mask(input.modifiers()));
        parameters.put("pointerType", "mouse");
        session.call("Input.dispatchMouseEvent", parameters);
    }

    private void dismissDialog() {
        ObjectNode parameters = mapper.createObjectNode();
        parameters.put("accept", false);
        session.call("Page.handleJavaScriptDialog", parameters);
    }
}
