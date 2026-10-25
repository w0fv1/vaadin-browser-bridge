package dev.w0fv1.vaadmin.remotebrowser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

final class TestProfileRoot {
    private TestProfileRoot() {
    }

    static Path create(String name) {
        Path root = Path.of("build", "test-profiles", name + "-" + UUID.randomUUID())
                .toAbsolutePath()
                .normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException failure) {
            throw new IllegalStateException("无法创建测试用浏览器配置目录: " + root, failure);
        }
        return root;
    }
}
