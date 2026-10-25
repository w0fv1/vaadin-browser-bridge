package dev.w0fv1.remotebrowser;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

final class BrowserProfileLease implements AutoCloseable {
    private final Path directory;
    private final BrowserEnvironmentProfile environment;
    private final Runnable release;
    private final AtomicBoolean closed = new AtomicBoolean();

    BrowserProfileLease(
            Path directory,
            BrowserEnvironmentProfile environment,
            Runnable release) {
        this.directory = directory;
        this.environment = environment;
        this.release = release;
    }

    String profileId() {
        return environment.profileId().value();
    }

    Path directory() {
        return directory;
    }

    BrowserEnvironmentProfile environment() {
        return environment;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            release.run();
        }
    }
}
