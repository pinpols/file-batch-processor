package com.example.filebatchprocessor.batch.preprocess;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 临时明文文件管理:专用目录 + UUID 名 + 显式删除。 */
@Slf4j
@Component
public class TempFileManager {

    private final Path baseDir;

    public TempFileManager(@Value("${batch.io.temp-dir:}") String tempDir) {
        this.baseDir = StringUtils.hasText(tempDir)
                ? Paths.get(tempDir)
                : Paths.get(System.getProperty("java.io.tmpdir"), "fbp-import-tmp");
    }

    public Path newTempFile(String suffix) throws IOException {
        ensureBaseDir();
        Path p = baseDir.resolve(UUID.randomUUID() + (suffix == null ? "" : suffix));
        try {
            return Files.createFile(
                    p,
                    PosixFilePermissions.asFileAttribute(
                            Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)));
        } catch (UnsupportedOperationException e) {
            return Files.createFile(p);
        }
    }

    @PostConstruct
    public void cleanupOnStartup() {
        cleanupStaleFiles(Duration.ofHours(24));
    }

    public void cleanupStaleFiles(Duration maxAge) {
        try {
            ensureBaseDir();
            Instant cutoff = Instant.now().minus(maxAge == null ? Duration.ofHours(24) : maxAge);
            try (Stream<Path> files = Files.list(baseDir)) {
                files.filter(Files::isRegularFile)
                        .filter(p ->
                                isManagedTempFile(p) && modifiedAtOrEpoch(p).compareTo(cutoff) <= 0)
                        .forEach(this::delete);
            }
        } catch (IOException e) {
            log.warn("failed to cleanup temp dir {}", baseDir, e);
        }
    }

    private void ensureBaseDir() throws IOException {
        try {
            Files.createDirectories(
                    baseDir,
                    PosixFilePermissions.asFileAttribute(Set.of(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE,
                            PosixFilePermission.OWNER_EXECUTE)));
            Files.setPosixFilePermissions(
                    baseDir,
                    Set.of(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE,
                            PosixFilePermission.OWNER_EXECUTE));
        } catch (UnsupportedOperationException e) {
            Files.createDirectories(baseDir);
        }
    }

    private boolean isManagedTempFile(Path p) {
        Path fileName = p.getFileName();
        if (fileName == null) {
            return false;
        }
        String name = fileName.toString();
        return name.endsWith(".dec") || name.endsWith(".plain");
    }

    private Instant modifiedAtOrEpoch(Path p) {
        try {
            return Files.getLastModifiedTime(p).toInstant();
        } catch (IOException e) {
            return Instant.EPOCH;
        }
    }

    public void delete(Path p) {
        if (p == null) {
            return;
        }
        try {
            Files.deleteIfExists(p);
        } catch (IOException e) {
            log.warn("failed to delete temp file {}", p, e);
        }
    }
}
