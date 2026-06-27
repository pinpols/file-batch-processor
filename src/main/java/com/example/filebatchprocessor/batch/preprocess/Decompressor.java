package com.example.filebatchprocessor.batch.preprocess;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 解压:gzip 单流 / zip 取首个普通 entry(其余忽略)。 */
@Slf4j
@Component
public class Decompressor {

    private static final long MAX_DECOMPRESSED_BYTES = 2L * 1024 * 1024 * 1024; // 2GB 防解压炸弹

    public void decompress(FileKind kind, InputStream in, OutputStream out) throws Exception {
        switch (kind) {
            case GZIP -> copyBounded(new GZIPInputStream(in), out);
            case ZIP -> unzipFirst(in, out);
            case PLAIN -> copyBounded(in, out);
        }
    }

    private void unzipFirst(InputStream in, OutputStream out) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                copyBounded(zis, out);
                if (zis.getNextEntry() != null) {
                    log.warn("zip 含多个 entry,仅导入首个,忽略其余");
                }
                return;
            }
            throw new IllegalArgumentException("zip has no file entry");
        }
    }

    private void copyBounded(InputStream in, OutputStream out) throws Exception {
        byte[] buf = new byte[8192];
        long total = 0;
        int n;
        while ((n = in.read(buf)) != -1) {
            total += n;
            if (total > MAX_DECOMPRESSED_BYTES) {
                throw new IllegalStateException("decompressed size exceeds limit (zip bomb?)");
            }
            out.write(buf, 0, n);
        }
    }
}
