package dev.w0fv1.browserbridge;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.function.Consumer;

record CdpSession(CdpConnection connection, String sessionId) {
    JsonNode call(String method, ObjectNode parameters) {
        return connection.call(sessionId, method, parameters);
    }

    void on(String method, Consumer<JsonNode> handler) {
        connection.on(sessionId, method, handler);
    }
}
