package com.example.filebatchprocessor.batch.reader.spi;

import cn.hutool.poi.excel.ExcelUtil;
import com.example.filebatchprocessor.model.FileRecord;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.core.io.Resource;

/** Excel(.xlsx)读取:首行表头按列名(id/name/description,大小写不敏感)映射。使用 SAX 流式解析避免大文件 OOM。 */
public class ExcelDocumentRecordReader implements DocumentRecordReader {

    private final DocumentReadOptions options;
    private InputStream in;
    private Path spoolFile;
    private BufferedReader spoolReader;

    public ExcelDocumentRecordReader(DocumentReadOptions options) {
        this.options = options;
    }

    @Override
    public Iterator<FileRecord> open(Resource resource) throws Exception {
        int requestedSheetIndex = options != null && options.sheetIndex() != null ? options.sheetIndex() : 0;
        boolean filterByIndex = options == null
                || options.sheetName() == null
                || options.sheetName().isBlank();
        this.spoolFile = Files.createTempFile("fbp-excel-", ".rows");
        AtomicReference<List<String>> headerRef = new AtomicReference<>();
        try (BufferedWriter writer = Files.newBufferedWriter(spoolFile, StandardCharsets.UTF_8)) {
            var handler = (cn.hutool.poi.excel.sax.handler.RowHandler) (sheet, rowIndex, rowList) -> {
                try {
                    if (filterByIndex && sheet != requestedSheetIndex) {
                        return;
                    }
                    if (rowIndex == 0) {
                        headerRef.set(rowList.stream()
                                .map(v -> v == null ? "" : v.toString().trim())
                                .toList());
                        return;
                    }
                    if (isBlank(rowList)) {
                        return;
                    }
                    List<String> header = headerRef.get();
                    FileRecord record = toRecord(header, rowList);
                    writer.write(encode(record));
                    writer.newLine();
                } catch (java.io.IOException e) {
                    throw new IllegalStateException("failed to spool excel row", e);
                }
            };
            readBySax(resource, handler);
        }
        this.spoolReader = Files.newBufferedReader(spoolFile, StandardCharsets.UTF_8);
        return new SpoolIterator(spoolReader);
    }

    private void readBySax(Resource resource, cn.hutool.poi.excel.sax.handler.RowHandler handler)
            throws java.io.IOException {
        String sheetName = options == null ? null : options.sheetName();
        try {
            java.io.File file = resource.getFile();
            if (sheetName != null && !sheetName.isBlank()) {
                ExcelUtil.readBySax(file, sheetName, handler);
            } else {
                ExcelUtil.readBySax(file, -1, handler);
            }
        } catch (java.io.FileNotFoundException | UnsupportedOperationException e) {
            this.in = resource.getInputStream();
            if (sheetName != null && !sheetName.isBlank()) {
                ExcelUtil.readBySax(in, sheetName, handler);
            } else {
                ExcelUtil.readBySax(in, -1, handler);
            }
        }
    }

    private FileRecord toRecord(List<String> header, List<Object> row) {
        FileRecord r = new FileRecord();
        Object id = get(header, row, "id");
        Object name = get(header, row, "name");
        Object desc = get(header, row, "description");
        if (id != null) {
            r.setId(toLong(id));
        }
        if (name != null) {
            r.setName(String.valueOf(name));
        }
        if (desc != null) {
            r.setDescription(String.valueOf(desc));
        }
        return r;
    }

    private static Object get(List<String> header, List<Object> row, String key) {
        if (header == null) {
            return null;
        }
        for (int i = 0; i < header.size(); i++) {
            String h = header.get(i);
            if (h != null && h.trim().equalsIgnoreCase(key)) {
                return i < row.size() ? row.get(i) : null;
            }
        }
        return null;
    }

    private static Long toLong(Object v) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) {
            return null;
        }
        return (long) Double.parseDouble(s);
    }

    private static boolean isBlank(List<Object> row) {
        if (row == null || row.isEmpty()) {
            return true;
        }
        for (Object value : row) {
            if (value != null && !value.toString().isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static String encode(FileRecord record) {
        return nullToEmpty(record.getId() == null ? null : record.getId().toString()) + "\t"
                + base64(record.getName()) + "\t"
                + base64(record.getDescription());
    }

    private static FileRecord decode(String line) {
        String[] parts = line.split("\t", -1);
        FileRecord record = new FileRecord();
        if (parts.length > 0 && !parts[0].isBlank()) {
            record.setId(Long.parseLong(parts[0]));
        }
        if (parts.length > 1) {
            record.setName(unbase64(parts[1]));
        }
        if (parts.length > 2) {
            record.setDescription(unbase64(parts[2]));
        }
        return record;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String base64(String value) {
        return value == null ? "" : Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String unbase64(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }

    @Override
    public void close() throws java.io.IOException {
        if (spoolReader != null) {
            spoolReader.close();
        }
        if (in != null) {
            in.close();
        }
        if (spoolFile != null) {
            Files.deleteIfExists(spoolFile);
        }
    }

    private static class SpoolIterator implements Iterator<FileRecord> {
        private final BufferedReader reader;
        private String nextLine;
        private boolean loaded;

        private SpoolIterator(BufferedReader reader) {
            this.reader = reader;
        }

        @Override
        public boolean hasNext() {
            load();
            return nextLine != null;
        }

        @Override
        public FileRecord next() {
            load();
            if (nextLine == null) {
                throw new NoSuchElementException();
            }
            String line = nextLine;
            nextLine = null;
            loaded = false;
            return decode(line);
        }

        private void load() {
            if (loaded) {
                return;
            }
            try {
                nextLine = reader.readLine();
                loaded = true;
            } catch (java.io.IOException e) {
                throw new IllegalStateException("failed to read spooled excel row", e);
            }
        }
    }
}
