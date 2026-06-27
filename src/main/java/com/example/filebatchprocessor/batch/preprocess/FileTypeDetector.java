package com.example.filebatchprocessor.batch.preprocess;

import java.util.Locale;
import org.springframework.stereotype.Component;

/** 判定文件是否 PGP 加密 + 压缩类型。显式参数优先于后缀。 */
@Component
public class FileTypeDetector {

    public DetectedType detect(String fileName, Boolean explicitEncrypted, String explicitCompression) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);

        boolean encrypted = explicitEncrypted != null
                ? explicitEncrypted
                : (lower.endsWith(".pgp") || lower.endsWith(".gpg"));

        String afterDecrypt = lower;
        if (afterDecrypt.endsWith(".pgp")) {
            afterDecrypt = afterDecrypt.substring(0, afterDecrypt.length() - 4);
        } else if (afterDecrypt.endsWith(".gpg")) {
            afterDecrypt = afterDecrypt.substring(0, afterDecrypt.length() - 4);
        }

        FileKind compression;
        if (explicitCompression != null && !explicitCompression.isBlank()) {
            compression = switch (explicitCompression.trim().toLowerCase(Locale.ROOT)) {
                case "gz", "gzip" -> FileKind.GZIP;
                case "zip" -> FileKind.ZIP;
                default -> FileKind.PLAIN;
            };
        } else if (afterDecrypt.endsWith(".gz")) {
            compression = FileKind.GZIP;
        } else if (afterDecrypt.endsWith(".zip")) {
            compression = FileKind.ZIP;
        } else {
            compression = FileKind.PLAIN;
        }

        return new DetectedType(encrypted, compression);
    }
}
