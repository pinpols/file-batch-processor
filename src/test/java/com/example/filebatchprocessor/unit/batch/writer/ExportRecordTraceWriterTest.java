package com.example.filebatchprocessor.unit.batch.writer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.filebatchprocessor.batch.writer.ExportRecordTraceWriter;
import com.example.filebatchprocessor.model.ExportRecord;
import com.example.filebatchprocessor.model.RecordTrace;
import com.example.filebatchprocessor.repository.RecordTraceRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.test.MetaDataInstanceFactory;

class ExportRecordTraceWriterTest {

    @Test
    void shouldDelegateWriteAndPersistExportTraces() throws Exception {
        @SuppressWarnings("unchecked")
        ItemWriter<ExportRecord> delegate = mock(ItemWriter.class);
        RecordTraceRepository recordTraceRepository = mock(RecordTraceRepository.class);

        ExportRecordTraceWriter writer = new ExportRecordTraceWriter(delegate, recordTraceRepository, "dataExportJob");

        JobParameters jobParameters = new JobParametersBuilder()
                .addString("output.file.name", "/tmp/out.csv")
                .toJobParameters();

        StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution(jobParameters);
        writer.beforeStep(stepExecution);

        ExportRecord r1 = new ExportRecord();
        r1.setBusinessKey("Alice:2026-03-01");
        r1.setBatchDate("2026-03-01");

        ExportRecord r2 = new ExportRecord();
        r2.setBusinessKey("Bob:2026-03-01");
        r2.setBatchDate("2026-03-01");

        writer.write(List.of(r1, r2));

        ArgumentCaptor<Chunk<ExportRecord>> chunkCaptor = ArgumentCaptor.forClass(Chunk.class);
        verify(delegate, times(1)).write(chunkCaptor.capture());
        assertEquals(2, chunkCaptor.getValue().getItems().size());

        ArgumentCaptor<RecordTrace> traceCaptor = ArgumentCaptor.forClass(RecordTrace.class);
        verify(recordTraceRepository, times(2)).save(traceCaptor.capture());

        List<RecordTrace> saved = traceCaptor.getAllValues();
        assertTrue(saved.stream().allMatch(t -> "EXPORT".equals(t.getEventType())));
        assertTrue(saved.stream().allMatch(t -> "SUCCESS".equals(t.getStatus())));
        assertTrue(saved.stream().allMatch(t -> t.getJobExecutionId() != null));
        assertEquals(
                2, saved.stream().map(RecordTrace::getBusinessKey).distinct().count());
    }

    @Test
    void shouldSkipBlankBusinessKey() throws Exception {
        @SuppressWarnings("unchecked")
        ItemWriter<ExportRecord> delegate = mock(ItemWriter.class);
        RecordTraceRepository recordTraceRepository = mock(RecordTraceRepository.class);

        ExportRecordTraceWriter writer = new ExportRecordTraceWriter(delegate, recordTraceRepository, "dataExportJob");
        StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution();
        writer.beforeStep(stepExecution);

        ExportRecord blank = new ExportRecord();
        blank.setBusinessKey(" ");

        writer.write(List.of(blank));

        verify(delegate, times(1)).write(any());
        verify(recordTraceRepository, never()).save(any());
    }
}
