package com.example.filebatchprocessor.unit.batch.preprocess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.example.filebatchprocessor.batch.preprocess.Decompressor;
import com.example.filebatchprocessor.batch.preprocess.FilePreprocessor;
import com.example.filebatchprocessor.batch.preprocess.FileTypeDetector;
import com.example.filebatchprocessor.batch.preprocess.PreprocessResult;
import com.example.filebatchprocessor.batch.preprocess.TempFileManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FilePreprocessorTest {

    private FilePreprocessor newPreprocessor(Path tempDir) {
        TempFileManager tfm = new TempFileManager(tempDir.toString());
        return new FilePreprocessor(new FileTypeDetector(), new Decompressor(), null, tfm, "", "");
    }

    @Test
    void plainPassthroughReturnsOriginalNoTempFile(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("data.csv");
        Files.writeString(src, "id,name\n1,a\n");
        FilePreprocessor pre = newPreprocessor(dir.resolve("tmp"));
        PreprocessResult r = pre.prepare(src.toString(), null, null);
        assertEquals(src.toString(), r.plaintextPath().toString());
        assertNull(r.tempFileOrNull());
    }

    @Test
    void gzipDecompressedToTempFile(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("data.csv.gz");
        try (GZIPOutputStream g = new GZIPOutputStream(Files.newOutputStream(src))) {
            g.write("id,name\n2,b\n".getBytes());
        }
        FilePreprocessor pre = newPreprocessor(dir.resolve("tmp"));
        PreprocessResult r = pre.prepare(src.toString(), null, null);
        assertNotNull(r.tempFileOrNull());
        assertEquals("id,name\n2,b\n", Files.readString(r.plaintextPath()));
    }
}
