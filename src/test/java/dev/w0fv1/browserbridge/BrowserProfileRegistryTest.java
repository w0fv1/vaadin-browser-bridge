package dev.w0fv1.browserbridge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class BrowserProfileRegistryTest {
    @TempDir
    Path root;

    @Test
    void leasesNamedProfileExclusivelyAndReleasesOnClose() throws Exception {
        BrowserProfileRegistry registry = new BrowserProfileRegistry(root);

        try (BrowserProfileLease lease = registry.acquire("account-1")) {
            assertThat(lease.directory()).isEqualTo(root.resolve("account-1").toAbsolutePath().normalize());
            assertThat(Files.isDirectory(lease.directory())).isTrue();
            assertThatThrownBy(() -> registry.acquire("account-1"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("正在使用");
        }

        try (BrowserProfileLease ignored = registry.acquire("account-1")) {
            assertThat(Files.isDirectory(root.resolve("account-1"))).isTrue();
        }
    }

    @Test
    void rejectsUnsafeProfileIdentifiers() {
        BrowserProfileRegistry registry = new BrowserProfileRegistry(root);

        assertThatThrownBy(() -> registry.acquire("../escape"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registry.acquire(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registry.acquire("a".repeat(65)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
