package com.example.filebatchprocessor.unit.batch.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.filebatchprocessor.batch.config.FileImportJobConfig;
import com.example.filebatchprocessor.batch.processor.FileImportRecordProcessor;
import com.example.filebatchprocessor.batch.processor.MappingImportProcessor;
import com.example.filebatchprocessor.mapping.MappingEngine;
import com.example.filebatchprocessor.mapping.TransformOp;
import com.example.filebatchprocessor.model.FieldMapping;
import com.example.filebatchprocessor.model.FileRecord;
import com.example.filebatchprocessor.repository.FieldMappingRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.transaction.PlatformTransactionManager;

/** importProcessor feed 路由:有 feedId 走 MappingImportProcessor,无 feedId 退回 defaultProcessor。 */
class ImportProcessorRoutingTest {

    private FileImportJobConfig config;

    @BeforeEach
    void setUp() {
        config = new FileImportJobConfig(mock(JobRepository.class), mock(PlatformTransactionManager.class));
    }

    @Test
    void noFeedIdReturnsDefaultProcessor() {
        Map<String, Object> jobParameters = new HashMap<>();
        FileImportRecordProcessor defaultProcessor = new FileImportRecordProcessor();
        FieldMappingRepository fieldMappingRepository = mock(FieldMappingRepository.class);

        ItemProcessor<FileRecord, FileRecord> result =
                config.importProcessor(jobParameters, defaultProcessor, new MappingEngine(), fieldMappingRepository);

        assertSame(defaultProcessor, result);
        verifyNoInteractions(fieldMappingRepository);
    }

    @Test
    void feedIdReturnsMappingImportProcessor() {
        Map<String, Object> jobParameters = new HashMap<>();
        jobParameters.put("feedId", "default-csv");

        FileImportRecordProcessor defaultProcessor = new FileImportRecordProcessor();
        FieldMappingRepository fieldMappingRepository = mock(FieldMappingRepository.class);

        FieldMapping mapping = new FieldMapping();
        mapping.setFeedId("default-csv");
        mapping.setSourceColumn("name");
        mapping.setTargetField("name");
        mapping.setTransformOp(TransformOp.UPPER);
        mapping.setRequired(true);
        mapping.setOrderNo(1);
        mapping.setEnabled(true);
        when(fieldMappingRepository.findByFeedIdAndEnabledTrueOrderByOrderNoAsc("default-csv"))
                .thenReturn(List.of(mapping));

        ItemProcessor<FileRecord, FileRecord> result =
                config.importProcessor(jobParameters, defaultProcessor, new MappingEngine(), fieldMappingRepository);

        assertInstanceOf(MappingImportProcessor.class, result);
    }
}
