package com.example.filebatchprocessor.unit.batch.processor;

import com.example.filebatchprocessor.batch.processor.ExportRecordProcessor;
import com.example.filebatchprocessor.exception.RecordValidationException;
import com.example.filebatchprocessor.model.ExportRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ExportRecordProcessorTest {

    private ExportRecordProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new ExportRecordProcessor();
    }

    @Test
    void shouldProcessValidRecord() {
        // Given
        ExportRecord input = createValidExportRecord();
        
        // When
        ExportRecord result = processor.process(input);
        
        // Then
        assertNotNull(result);
        assertEquals(input.getId(), result.getId());
        assertEquals(input.getBusinessKey(), result.getBusinessKey());
        assertEquals("TEST NAME", result.getName()); // Should be uppercase
        assertEquals("Test description", result.getDescription()); // Should be trimmed
        assertEquals("2026-03-06", result.getBatchDate()); // Should be formatted
    }

    @Test
    void shouldFilterNullRecord() {
        // Given
        ExportRecord input = null;
        
        // When
        ExportRecord result = processor.process(input);
        
        // Then
        assertNull(result);
    }

    @Test
    void shouldFilterEmptyBusinessKey() {
        // Given
        ExportRecord input = createValidExportRecord();
        input.setBusinessKey("");
        
        // When
        ExportRecord result = processor.process(input);
        
        // Then
        assertNull(result);
    }

    @Test
    void shouldFilterTestData() {
        // Given
        ExportRecord input = createValidExportRecord();
        input.setBusinessKey("TEST_123");
        
        // When
        ExportRecord result = processor.process(input);
        
        // Then
        assertNull(result);
    }

    @Test
    void shouldThrowExceptionForMissingId() {
        // Given
        ExportRecord input = createValidExportRecord();
        input.setId(null);
        
        // When & Then
        assertThrows(RecordValidationException.class, () -> {
            processor.process(input);
        });
    }

    @Test
    void shouldThrowExceptionForNegativeId() {
        // Given
        ExportRecord input = createValidExportRecord();
        input.setId(-1L);
        
        // When & Then
        assertThrows(RecordValidationException.class, () -> {
            processor.process(input);
        });
    }

    @Test
    void shouldThrowExceptionForEmptyName() {
        // Given
        ExportRecord input = createValidExportRecord();
        input.setName("");
        
        // When & Then
        assertThrows(RecordValidationException.class, () -> {
            processor.process(input);
        });
    }

    @Test
    void shouldThrowExceptionForEmptyBatchDate() {
        // Given
        ExportRecord input = createValidExportRecord();
        input.setBatchDate("");
        
        // When & Then
        assertThrows(RecordValidationException.class, () -> {
            processor.process(input);
        });
    }

    @Test
    void shouldFormatNameWithSpecialCharacters() {
        // Given
        ExportRecord input = createValidExportRecord();
        input.setName("  test\nname\twith\rspaces  ");
        
        // When
        ExportRecord result = processor.process(input);
        
        // Then
        assertNotNull(result);
        assertEquals("TEST NAME WITH SPACES", result.getName());
    }

    @Test
    void shouldTruncateLongName() {
        // Given
        ExportRecord input = createValidExportRecord();
        String longName = "a".repeat(200); // 200 characters
        input.setName(longName);
        
        // When
        ExportRecord result = processor.process(input);
        
        // Then
        assertNotNull(result);
        assertEquals(100, result.getName().length()); // Should be truncated to 100
        assertTrue(result.getName().endsWith("..."));
    }

    @Test
    void shouldFormatDescriptionWithControlCharacters() {
        // Given
        ExportRecord input = createValidExportRecord();
        input.setDescription("test\u0001description\u0002with\u0003control\u0004chars");
        
        // When
        ExportRecord result = processor.process(input);
        
        // Then
        assertNotNull(result);
        assertEquals("test description with control chars", result.getDescription());
    }

    @Test
    void shouldFormatBatchDate() {
        // Given
        ExportRecord input = createValidExportRecord();
        input.setBatchDate("2026-03-06T10:15:30");
        
        // When
        ExportRecord result = processor.process(input);
        
        // Then
        assertNotNull(result);
        assertEquals("2026-03-06", result.getBatchDate());
    }

    @Test
    void shouldHandleInvalidBatchDate() {
        // Given
        ExportRecord input = createValidExportRecord();
        input.setBatchDate("invalid-date");
        
        // When
        ExportRecord result = processor.process(input);
        
        // Then
        assertNotNull(result);
        assertEquals("invalid-date", result.getBatchDate()); // Should return original if parsing fails
    }

    @Test
    void shouldTrackStatistics() {
        // Given
        processor.resetStats();
        ExportRecord validRecord = createValidExportRecord();
        ExportRecord invalidRecord = createValidExportRecord();
        invalidRecord.setBusinessKey(""); // Will be filtered
        
        // When
        processor.process(validRecord);
        processor.process(null); // Will be filtered
        processor.process(invalidRecord); // Will throw exception
        
        // Then
        var stats = processor.getStats();
        assertEquals(3, stats.getTotalProcessed());
        assertEquals(1, stats.getTotalTransformed());
        assertEquals(2, stats.getTotalFiltered()); // null + empty business key
        assertEquals(0, stats.getTotalErrors()); // Exceptions are thrown, not counted as errors
    }

    @Test
    void shouldResetStatistics() {
        // Given
        processor.process(createValidExportRecord());
        processor.process(null);
        
        // When
        processor.resetStats();
        
        // Then
        var stats = processor.getStats();
        assertEquals(0, stats.getTotalProcessed());
        assertEquals(0, stats.getTotalTransformed());
        assertEquals(0, stats.getTotalFiltered());
        assertEquals(0, stats.getTotalErrors());
    }

    @Test
    void shouldCalculateSuccessRate() {
        // Given
        processor.resetStats();
        
        // When
        for (int i = 0; i < 8; i++) {
            processor.process(createValidExportRecord());
        }
        processor.process(null); // 1 filtered
        
        // Then
        var stats = processor.getStats();
        assertEquals(9, stats.getTotalProcessed());
        assertEquals(8, stats.getTotalTransformed());
        assertEquals(1, stats.getTotalFiltered());
        assertEquals(8.0/9.0, stats.getSuccessRate(), 0.01);
    }

    private ExportRecord createValidExportRecord() {
        ExportRecord record = new ExportRecord();
        record.setId(1L);
        record.setBusinessKey("BUSINESS_KEY_001");
        record.setName("test name");
        record.setDescription("Test description");
        record.setBatchDate("2026-03-06");
        return record;
    }
}
