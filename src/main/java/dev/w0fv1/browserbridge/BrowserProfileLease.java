package dev.w0fv1.browserbridge;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

final class BrowserProfileLease implements AutoCloseable {
    private final String profileId;
    private final Path directory;
    private final Runnable release;
    private final AtomicBoolean closed = new AtomicBoolean();

    BrowserProfileLease(String profileId, Path directory, Runnable release) {
        this.profileId = profileId;
        this.directory = directory;
        this.release = release;
    }

    String profileId() {
        return profileId;
    }

    Path directory() {
        return directory;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            release.run();
        }
    }
}
