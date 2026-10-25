package dev.w0fv1.vaadmin.remotebrowser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

final class DirectoryTree {
    private static final Logger log = LoggerFactory.getLogger(DirectoryTree.class);

    private DirectoryTree() {
    }

    static void delete(Path root) {
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    remove(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException failure) {
                    remove(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path directory, IOException failure) {
                    remove(directory);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException failure) {
            log.warn("无法遍历目录 {}: {}", root, failure.getMessage());
        }
    }

    private static void remove(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException failure) {
            log.warn("无法删除 {}: {}", path, failure.getMessage());
        }
    }
}
