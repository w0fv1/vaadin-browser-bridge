package dev.w0fv1.vaadmin.remotebrowser;

public enum BrowserMediaPolicy {
    ALLOW,
    MUTE,
    BLOCK;

    boolean mutesAudio() {
        return this != ALLOW;
    }

    boolean blocksPlayback() {
        return this == BLOCK;
    }
}
