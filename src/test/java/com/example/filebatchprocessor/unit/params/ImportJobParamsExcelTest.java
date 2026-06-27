package com.example.filebatchprocessor.unit.params;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.example.filebatchprocessor.params.ImportJobParams;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ImportJobParamsExcelTest {

    @Test
    void parsesExcelSheetParams() {
        ImportJobParams p = ImportJobParams.from(Map.of(
                "input.file.name", "x.xlsx",
                "file.format", "EXCEL",
                "excel.sheet.index", "2",
                "excel.sheet.name", "Data"));
        assertEquals(2, p.getExcelSheetIndex());
        assertEquals("Data", p.getExcelSheetName());
    }

    @Test
    void excelSheetDefaultsWhenAbsent() {
        ImportJobParams p = ImportJobParams.from(Map.of("input.file.name", "x.csv", "file.format", "CSV"));
        assertEquals(0, p.getExcelSheetIndex());
        assertNull(p.getExcelSheetName());
    }
}
