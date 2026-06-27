package com.example.filebatchprocessor.batch.preprocess;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 编排:detect → (PGP 解密) → (解压) → 落临时明文文件;PLAIN 透传原文件。 */
@Slf4j
@Component
public class FilePreprocessor {

    private final FileTypeDetector detector;
    private final Decompressor decompressor;
    private final PgpDecryptor pgpDecryptor; // 可能为 null
    private final TempFileManager tempFileManager;
    private final String privateKeyPath;
    private final String passphrase;

    public FilePreprocessor(
            FileTypeDetector detector,
            Decompressor decompressor,
            PgpDecryptor pgpDecryptor,
            TempFileManager tempFileManager,
            @Value("${batch.pgp.private-key-path:}") String privateKeyPath,
            @Value("${batch.pgp.passphrase:}") String passphrase) {
        this.detector = detector;
        this.decompressor = decompressor;
        this.pgpDecryptor = pgpDecryptor;
        this.tempFileManager = tempFileManager;
        this.privateKeyPath = privateKeyPath;
        this.passphrase = passphrase;
    }

    public PreprocessResult prepare(String fileName, Boolean explicitEncrypted, String explicitCompression)
            throws Exception {
        DetectedType type = detector.detect(fileName, explicitEncrypted, explicitCompression);
        if (!type.encrypted() && type.compression() == FileKind.PLAIN) {
            return new PreprocessResult(Paths.get(fileName), null);
        }

        Path source = Paths.get(fileName);
        Path decryptTemp = null;
        Path plainTemp = null;
        try {
            Path afterDecrypt = source;
            if (type.encrypted()) {
                if (pgpDecryptor == null || !StringUtils.hasText(privateKeyPath)) {
                    throw new IllegalStateException("file is encrypted but PGP decryptor/key not configured");
                }
                decryptTemp = tempFileManager.newTempFile(".dec");
                try (InputStream in = Files.newInputStream(source);
                        InputStream key = Files.newInputStream(Paths.get(privateKeyPath));
                        OutputStream out = Files.newOutputStream(decryptTemp)) {
                    pgpDecryptor.decrypt(in, key, passphrase.toCharArray(), out);
                }
                afterDecrypt = decryptTemp;
            }

            plainTemp = tempFileManager.newTempFile(".plain");
            try (InputStream in = Files.newInputStream(afterDecrypt);
                    OutputStream out = Files.newOutputStream(plainTemp)) {
                decompressor.decompress(type.compression(), in, out);
            }
            PreprocessResult result = new PreprocessResult(plainTemp, plainTemp);
            plainTemp = null; // 成功:不在 catch 删
            return result;
        } catch (Exception e) {
            tempFileManager.delete(plainTemp); // 失败:删部分明文
            throw e;
        } finally {
            tempFileManager.delete(decryptTemp); // 始终删中间 .dec
        }
    }
}
