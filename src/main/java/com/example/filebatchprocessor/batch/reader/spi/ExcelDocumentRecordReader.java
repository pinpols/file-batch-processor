package com.example.filebatchprocessor.batch.reader.spi;

import cn.hutool.poi.excel.ExcelReader;
import cn.hutool.poi.excel.ExcelUtil;
import com.example.filebatchprocessor.model.FileRecord;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.Resource;

/** Excel(.xlsx)读取:首行表头按列名(id/name/description,大小写不敏感)映射。Hutool 全量读(单租户够用)。 */
public class ExcelDocumentRecordReader implements DocumentRecordReader {

    private final DocumentReadOptions options;
    private InputStream in;
    private ExcelReader excelReader;

    public ExcelDocumentRecordReader(DocumentReadOptions options) {
        this.options = options;
    }

    @Override
    public Iterator<FileRecord> open(Resource resource) throws Exception {
        this.in = resource.getInputStream();
        int sheetIndex = options != null && options.sheetIndex() != null ? options.sheetIndex() : 0;
        this.excelReader = (options != null
                        && options.sheetName() != null
                        && !options.sheetName().isBlank())
                ? ExcelUtil.getReader(in, options.sheetName())
                : ExcelUtil.getReader(in, sheetIndex);
        List<Map<String, Object>> rows = excelReader.readAll();
        List<FileRecord> records = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            records.add(toRecord(row));
        }
        return records.iterator();
    }

    private FileRecord toRecord(Map<String, Object> row) {
        FileRecord r = new FileRecord();
        Object id = get(row, "id");
        Object name = get(row, "name");
        Object desc = get(row, "description");
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

    private static Object get(Map<String, Object> row, String key) {
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (e.getKey() != null && e.getKey().trim().equalsIgnoreCase(key)) {
                return e.getValue();
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

    @Override
    public void close() throws java.io.IOException {
        if (excelReader != null) {
            excelReader.close();
        }
        if (in != null) {
            in.close();
        }
    }
}
