package com.example.filebatchprocessor.unit.batch.preprocess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.filebatchprocessor.batch.preprocess.DetectedType;
import com.example.filebatchprocessor.batch.preprocess.FileKind;
import com.example.filebatchprocessor.batch.preprocess.FileTypeDetector;
import org.junit.jupiter.api.Test;

class FileTypeDetectorTest {

    private final FileTypeDetector detector = new FileTypeDetector();

    @Test
    void detectsBySuffix() {
        DetectedType t = detector.detect("data.csv.gz.pgp", null, null);
        assertTrue(t.encrypted());
        assertEquals(FileKind.GZIP, t.compression());
    }

    @Test
    void plainWhenNoSuffix() {
        DetectedType t = detector.detect("data.csv", null, null);
        assertFalse(t.encrypted());
        assertEquals(FileKind.PLAIN, t.compression());
    }

    @Test
    void explicitOverridesSuffix() {
        DetectedType t = detector.detect("data.csv", Boolean.TRUE, "zip");
        assertTrue(t.encrypted());
        assertEquals(FileKind.ZIP, t.compression());
    }
}
