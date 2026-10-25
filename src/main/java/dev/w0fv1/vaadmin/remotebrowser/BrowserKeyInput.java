package dev.w0fv1.vaadmin.remotebrowser;

import tools.jackson.databind.JsonNode;

import java.util.Set;

record BrowserKeyInput(
        String key,
        String code,
        int keyCode,
        int location,
        boolean repeat,
        Set<BrowserModifier> modifiers) {
    static BrowserKeyInput parse(JsonNode input) {
        String key = input.path("key").asString("");
        String code = input.path("code").asString("");
        int keyCode = input.path("keyCode").asInt(0);
        int location = input.path("location").asInt(0);
        if (key.isEmpty() || key.length() > 32 || code.length() > 32
                || keyCode < 0 || keyCode > 255 || location < 0 || location > 3) {
            throw new IllegalArgumentException("键盘事件字段无效");
        }
        return new BrowserKeyInput(
                key,
                code,
                keyCode,
                location,
                input.path("repeat").asBoolean(false),
                BrowserModifier.parse(input));
    }

    boolean printable() {
        return key.codePointCount(0, key.length()) == 1
                && !modifiers.contains(BrowserModifier.CTRL)
                && !modifiers.contains(BrowserModifier.META);
    }

    boolean keypad() {
        return location == 3;
    }
}
