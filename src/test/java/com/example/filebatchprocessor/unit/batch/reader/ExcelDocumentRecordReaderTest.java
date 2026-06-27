package com.example.filebatchprocessor.unit.batch.reader;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;
import com.example.filebatchprocessor.batch.reader.spi.DocumentReadOptions;
import com.example.filebatchprocessor.batch.reader.spi.ExcelDocumentRecordReader;
import com.example.filebatchprocessor.model.FileRecord;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;

class ExcelDocumentRecordReaderTest {

    @Test
    void parsesXlsxHeaderMappedRows(@TempDir Path dir) throws Exception {
        Path xlsx = dir.resolve("in.xlsx");
        try (ExcelWriter writer = ExcelUtil.getWriter(xlsx.toFile())) {
            writer.writeHeadRow(List.of("id", "name", "description"));
            writer.writeRow(List.of(1, "alice", "first"), false);
            writer.writeRow(List.of(2, "bob", "second"), false);
            writer.flush();
        }

        ExcelDocumentRecordReader reader = new ExcelDocumentRecordReader(new DocumentReadOptions(0, null));
        Iterator<FileRecord> it = reader.open(new FileSystemResource(xlsx.toFile()));
        List<FileRecord> out = new ArrayList<>();
        it.forEachRemaining(out::add);
        reader.close();

        assertEquals(2, out.size());
        assertEquals(1L, out.get(0).getId());
        assertEquals("alice", out.get(0).getName());
        assertEquals("first", out.get(0).getDescription());
        assertEquals("bob", out.get(1).getName());
    }
}
