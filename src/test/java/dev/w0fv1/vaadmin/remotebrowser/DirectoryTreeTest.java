package dev.w0fv1.vaadmin.remotebrowser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(60)
final class DirectoryTreeTest {
    @TempDir
    Path root;

    @Test
    void deletesNestedTreeDownToRoot() throws Exception {
        Path tree = Files.createDirectories(root.resolve("session/Default/Cache"));
        Files.writeString(tree.resolve("entry.bin"), "cache");
        Files.writeString(root.resolve("session/Default/Preferences"), "{}");

        DirectoryTree.delete(root.resolve("session"));

        assertThat(Files.exists(root.resolve("session"))).isFalse();
    }

    @Test
    void ignoresEntriesRemovedWhileWalking() throws Exception {
        Path tree = Files.createDirectories(root.resolve("session/Default"));
        List<Path> entries = new ArrayList<>();
        for (int index = 0; index < 500; index++) {
            Path entry = tree.resolve("entry-" + index + ".tmp");
            Files.writeString(entry, "payload");
            entries.add(entry);
        }

        ExecutorService remover = Executors.newSingleThreadExecutor();
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> racing = remover.submit(() -> {
                start.await();
                for (int index = entries.size() - 1; index >= 0; index--) {
                    try {
                        Files.deleteIfExists(entries.get(index));
                    } catch (IOException ignored) {
                    }
                }
                return null;
            });
            start.countDown();

            DirectoryTree.delete(root.resolve("session"));

            racing.get(30, TimeUnit.SECONDS);
        } finally {
            remover.shutdownNow();
        }

        assertThat(Files.exists(root.resolve("session"))).isFalse();
    }
}
