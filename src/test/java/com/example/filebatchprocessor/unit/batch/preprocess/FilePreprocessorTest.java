package com.example.filebatchprocessor.unit.batch.preprocess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.filebatchprocessor.batch.preprocess.Decompressor;
import com.example.filebatchprocessor.batch.preprocess.FilePreprocessor;
import com.example.filebatchprocessor.batch.preprocess.FileTypeDetector;
import com.example.filebatchprocessor.batch.preprocess.PreprocessResult;
import com.example.filebatchprocessor.batch.preprocess.TempFileManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
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

    @Test
    void decryptFailureLeavesNoTempResidue(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("data.enc");
        Files.writeString(src, "not-real-pgp-bytes");
        Path fakeKey = dir.resolve("key.asc");
        Files.writeString(fakeKey, "fake-key");
        Path tmpDir = dir.resolve("tmp");
        TempFileManager tfm = new TempFileManager(tmpDir.toString());
        FilePreprocessor pre = new FilePreprocessor(
                new FileTypeDetector(),
                new Decompressor(),
                (in, key, pass, out) -> {
                    throw new RuntimeException("bad pass");
                },
                tfm,
                fakeKey.toString(),
                "x");

        assertThrows(Exception.class, () -> pre.prepare(src.toString(), true, "none"));

        try (Stream<Path> files = Files.list(tmpDir)) {
            List<Path> residue = files.toList();
            assertTrue(residue.isEmpty(), "temp dir should have no .dec/.plain residue but found: " + residue);
        }
    }
}
