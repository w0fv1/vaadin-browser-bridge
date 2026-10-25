package dev.w0fv1.remotebrowser;

import tools.jackson.databind.JsonNode;

import java.util.EnumSet;
import java.util.Set;

enum BrowserModifier {
    ALT(1, "alt"),
    CTRL(2, "ctrl"),
    META(4, "meta"),
    SHIFT(8, "shift");

    private final int bitmask;
    private final String field;

    BrowserModifier(int bitmask, String field) {
        this.bitmask = bitmask;
        this.field = field;
    }

    int bitmask() {
        return bitmask;
    }

    static Set<BrowserModifier> parse(JsonNode input) {
        EnumSet<BrowserModifier> modifiers = EnumSet.noneOf(BrowserModifier.class);
        for (BrowserModifier modifier : values()) {
            if (input.path(modifier.field).asBoolean(false)) modifiers.add(modifier);
        }
        return modifiers;
    }

    static int mask(Set<BrowserModifier> modifiers) {
        int mask = 0;
        for (BrowserModifier modifier : modifiers) mask |= modifier.bitmask();
        return mask;
    }
}
