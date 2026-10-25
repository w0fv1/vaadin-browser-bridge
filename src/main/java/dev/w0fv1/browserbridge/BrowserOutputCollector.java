package dev.w0fv1.browserbridge;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

final class BrowserOutputCollector implements AutoCloseable {
    private static final int MAX_LINES = 200;

    private final Deque<String> lines = new ConcurrentLinkedDeque<>();
    private final BufferedReader reader;
    private final Thread pump;

    BrowserOutputCollector(InputStream stream) {
        this.reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        this.pump = Thread.ofVirtual().name("browser-output").start(this::drain);
    }

    String tail() {
        return String.join("\n", lines);
    }

    private void drain() {
        try (reader) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.addLast(line);
                while (lines.size() > MAX_LINES) lines.pollFirst();
            }
        } catch (IOException ignored) {
        }
    }

    @Override
    public void close() {
        pump.interrupt();
    }
}
