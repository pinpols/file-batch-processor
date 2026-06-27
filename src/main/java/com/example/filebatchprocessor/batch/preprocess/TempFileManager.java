package com.example.filebatchprocessor.batch.preprocess;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
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
        Files.createDirectories(baseDir);
        return baseDir.resolve(UUID.randomUUID() + (suffix == null ? "" : suffix));
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
