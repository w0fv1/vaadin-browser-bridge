package dev.w0fv1.browserbridge;

import com.microsoft.playwright.options.MouseButton;
import tools.jackson.databind.JsonNode;

import java.util.Set;

record BrowserInput(
        String type,
        long sequence,
        double x,
        double y,
        int button,
        int buttons,
        int pointerId,
        long clientTimeMillis) {
    private static final Set<String> TYPES = Set.of("move", "down", "up", "cancel", "context");

    static BrowserInput parse(JsonNode input) {
        String type = input.path("type").asString("");
        if (!TYPES.contains(type)) throw new IllegalArgumentException("未知指针事件");
        long sequence = input.path("seq").asLong(-1);
        double x = input.path("x").asDouble(Double.NaN);
        double y = input.path("y").asDouble(Double.NaN);
        int buttonFallback = 0;
        if (type.equals("move")) {
            buttonFallback = -1;
        }
        int button = input.path("button").asInt(buttonFallback);
        int buttons = input.path("buttons").asInt(0);
        int pointerId = input.path("pointerId").asInt(-1);
        long clientTimeMillis = input.path("clientTime").asLong(-1);
        if (sequence < 0 || !Double.isFinite(x) || !Double.isFinite(y)
                || button < -1 || button > 2 || buttons < 0 || buttons > 7
                || pointerId < 1 || clientTimeMillis < 0) {
            throw new IllegalArgumentException("指针事件字段无效");
        }
        if (!type.equals("move") && button < 0) throw new IllegalArgumentException("动作事件缺少鼠标按钮");
        return new BrowserInput(type, sequence, x, y, button, buttons, pointerId, clientTimeMillis);
    }

    MouseButton mouseButton() {
        return switch (button) {
            case 0 -> MouseButton.LEFT;
            case 1 -> MouseButton.MIDDLE;
            case 2 -> MouseButton.RIGHT;
            default -> throw new IllegalStateException("移动事件没有可用鼠标按钮");
        };
    }
}
