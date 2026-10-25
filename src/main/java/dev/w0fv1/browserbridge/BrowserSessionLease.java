package dev.w0fv1.browserbridge;

import java.util.concurrent.atomic.AtomicBoolean;

final class BrowserSessionLease implements AutoCloseable {
    private final BrowserSession session;
    private final Runnable release;
    private final AtomicBoolean closed = new AtomicBoolean();

    BrowserSessionLease(BrowserSession session, Runnable release) {
        this.session = session;
        this.release = release;
    }

    BrowserSession session() {
        return session;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            release.run();
        }
    }
}
