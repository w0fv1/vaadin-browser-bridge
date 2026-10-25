package dev.w0fv1.vaadmin.remotebrowser;

enum BrowserMouseButton {
    NONE("none", -1, 0),
    LEFT("left", 0, 1),
    MIDDLE("middle", 1, 4),
    RIGHT("right", 2, 2);

    private final String cdpName;
    private final int index;
    private final int bitmask;

    BrowserMouseButton(String cdpName, int index, int bitmask) {
        this.cdpName = cdpName;
        this.index = index;
        this.bitmask = bitmask;
    }

    String cdpName() {
        return cdpName;
    }

    int bitmask() {
        return bitmask;
    }

    static BrowserMouseButton parse(int index) {
        for (BrowserMouseButton button : values()) {
            if (button.index == index) return button;
        }
        throw new IllegalArgumentException("未知鼠标按钮");
    }
}
