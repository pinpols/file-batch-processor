package com.example.filebatchprocessor.unit.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.example.filebatchprocessor.util.PathSafety;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 符号链接逃逸硬化:base 内的 symlink 指向外部不得逃逸,待写出文件不被误拒。 */
class PathSafetySymlinkTest {

    @Test
    void allowsRealFileInsideBase(@TempDir Path tmp) throws IOException {
        Path base = tmp.resolve("base");
        Files.createDirectories(base);
        Files.createFile(base.resolve("f.csv"));

        String result = PathSafety.confine(base.toString(), "f.csv");

        assertThat(result).contains(base.toRealPath().toString());
    }

    @Test
    void rejectsSymlinkEscape(@TempDir Path tmp) throws IOException {
        Path base = tmp.resolve("base");
        Files.createDirectories(base);
        Path outside = tmp.resolve("outside");
        Files.createDirectories(outside);
        Path secret = outside.resolve("secret.txt");
        Files.createFile(secret);

        Path link = base.resolve("link.csv");
        try {
            Files.createSymbolicLink(link, secret);
        } catch (UnsupportedOperationException | IOException e) {
            assumeTrue(false, "symlink not supported on this OS: " + e.getMessage());
        }

        assertThatThrownBy(() -> PathSafety.confine(base.toString(), "link.csv"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("symlink");
    }

    @Test
    void allowsNotYetExistingOutputFile(@TempDir Path tmp) throws IOException {
        Path base = tmp.resolve("base");
        Files.createDirectories(base);

        String result = PathSafety.confine(base.toString(), "not-yet.csv");

        assertThat(result).contains("not-yet.csv");
        assertThat(result).startsWith(base.toAbsolutePath().normalize().toString());
    }

    @Test
    void rejectsAbsolutePathEscape(@TempDir Path tmp) throws IOException {
        Path base = tmp.resolve("base");
        Files.createDirectories(base);

        assertThatThrownBy(() -> PathSafety.confine(base.toString(), "/etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAbsolutePathWhenBaseDirIsNotConfigured(@TempDir Path tmp) throws IOException {
        Path absolute = tmp.resolve("outside.csv").toAbsolutePath();
        Files.createFile(absolute);

        assertThatThrownBy(() -> PathSafety.confine("", absolute.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("base dir");
    }
}
