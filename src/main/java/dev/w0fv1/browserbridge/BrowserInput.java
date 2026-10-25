package dev.w0fv1.browserbridge;

import tools.jackson.databind.JsonNode;

import java.util.Set;

record BrowserInput(
        String type,
        long sequence,
        double x,
        double y,
        BrowserMouseButton button,
        int buttons,
        int clickCount,
        Set<BrowserModifier> modifiers) {
    private static final Set<String> TYPES = Set.of("move", "down", "up", "cancel", "context");

    static BrowserInput parse(JsonNode input) {
        String type = input.path("type").asString("");
        if (!TYPES.contains(type)) throw new IllegalArgumentException("未知指针事件");
        long sequence = input.path("seq").asLong(-1);
        double x = input.path("x").asDouble(Double.NaN);
        double y = input.path("y").asDouble(Double.NaN);
        int buttons = input.path("buttons").asInt(0);
        int clickCount = input.path("clickCount").asInt(1);
        int buttonIndex = "move".equals(type) ? -1 : input.path("button").asInt(0);
        if (sequence < 0 || !Double.isFinite(x) || !Double.isFinite(y)
                || buttons < 0 || buttons > 7 || clickCount < 1 || clickCount > 3) {
            throw new IllegalArgumentException("指针事件字段无效");
        }
        return new BrowserInput(
                type,
                sequence,
                x,
                y,
                BrowserMouseButton.parse(buttonIndex),
                buttons,
                clickCount,
                BrowserModifier.parse(input));
    }
}
