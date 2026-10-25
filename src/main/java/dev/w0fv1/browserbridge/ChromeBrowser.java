package dev.w0fv1.browserbridge;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

final class ChromeBrowser implements AutoCloseable {
    private static final Duration GRACEFUL_EXIT = Duration.ofSeconds(20);
    private final ChromeProcess process;
    private final CdpConnection connection;
    private final String product;
    private final String protocolVersion;
    private final AtomicBoolean closed = new AtomicBoolean();

    ChromeBrowser(ChromeProcess process, CdpConnection connection) {
        this.process = process;
        this.connection = connection;
        JsonNode version = connection.call("Browser.getVersion", null);
        this.product = version.path("product").asString("");
        this.protocolVersion = version.path("protocolVersion").asString("");
    }

    CdpConnection connection() {
        return connection;
    }

    String product() {
        return product;
    }

    String protocolVersion() {
        return protocolVersion;
    }

    JsonNode call(String method, ObjectNode parameters) {
        return connection.call(method, parameters);
    }

    boolean isOpen() {
        return !closed.get() && connection.isOpen() && process.isAlive();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        try {
            connection.send("Browser.close", null);
        } catch (RuntimeException ignored) {
        }
        process.awaitExit(GRACEFUL_EXIT);
        process.close();
        connection.close();
    }
}
