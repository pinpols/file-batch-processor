package com.example.filebatchprocessor.unit.batch.preprocess;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.filebatchprocessor.batch.preprocess.Decompressor;
import com.example.filebatchprocessor.batch.preprocess.FileKind;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class DecompressorTest {

    private final Decompressor decompressor = new Decompressor();

    @Test
    void gunzipsToOriginal() throws Exception {
        byte[] original = "id,name\n1,alice\n".getBytes();
        ByteArrayOutputStream gz = new ByteArrayOutputStream();
        try (GZIPOutputStream g = new GZIPOutputStream(gz)) {
            g.write(original);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        decompressor.decompress(FileKind.GZIP, new ByteArrayInputStream(gz.toByteArray()), out);
        assertEquals(new String(original), out.toString());
    }

    @Test
    void unzipsFirstEntry() throws Exception {
        byte[] original = "id,name\n2,bob\n".getBytes();
        ByteArrayOutputStream zip = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(zip)) {
            z.putNextEntry(new ZipEntry("data.csv"));
            z.write(original);
            z.closeEntry();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        decompressor.decompress(FileKind.ZIP, new ByteArrayInputStream(zip.toByteArray()), out);
        assertEquals(new String(original), out.toString());
    }
}
