package com.example.filebatchprocessor.batch.writer;

import com.example.filebatchprocessor.model.ExportRecord;
import com.example.filebatchprocessor.model.RecordTrace;
import com.example.filebatchprocessor.repository.RecordTraceRepository;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStream;
import org.springframework.batch.infrastructure.item.ItemStreamException;
import org.springframework.batch.infrastructure.item.ItemWriter;

import java.util.List;

public class ExportRecordTraceWriter implements ItemWriter<ExportRecord>, ItemStream, StepExecutionListener {

    private final ItemWriter<ExportRecord> delegate;
    private final RecordTraceRepository recordTraceRepository;
    private final String jobName;

    private Long jobExecutionId;
    private String outputFileName;

    public ExportRecordTraceWriter(ItemWriter<ExportRecord> delegate,
                                  RecordTraceRepository recordTraceRepository,
                                  String jobName) {
        this.delegate = delegate;
        this.recordTraceRepository = recordTraceRepository;
        this.jobName = jobName;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        this.jobExecutionId = stepExecution.getJobExecutionId();
        try {
            this.outputFileName = stepExecution.getJobParameters().getString("output.file.name");
        } catch (Exception ignored) {
            this.outputFileName = null;
        }
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        return stepExecution.getExitStatus();
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        if (delegate instanceof ItemStream itemStream) {
            itemStream.open(executionContext);
        }
    }

    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {
        if (delegate instanceof ItemStream itemStream) {
            itemStream.update(executionContext);
        }
    }

    @Override
    public void close() throws ItemStreamException {
        if (delegate instanceof ItemStream itemStream) {
            itemStream.close();
        }
    }

    @Override
    public void write(Chunk<? extends ExportRecord> chunk) throws Exception {
        write(chunk.getItems());
    }

    public void write(List<? extends ExportRecord> items) throws Exception {
        Chunk<ExportRecord> out = new Chunk<>();
        for (ExportRecord r : items) {
            out.add(r);
        }
        delegate.write(out);
        for (ExportRecord r : items) {
            if (r == null || r.getBusinessKey() == null || r.getBusinessKey().isBlank()) {
                continue;
            }
            RecordTrace trace = new RecordTrace();
            trace.setBusinessKey(r.getBusinessKey());
            trace.setBatchDate(r.getBatchDate());
            trace.setJobName(jobName);
            trace.setJobExecutionId(jobExecutionId);
            trace.setOutputFileName(outputFileName);
            trace.setEventType("EXPORT");
            trace.setStatus("SUCCESS");
            recordTraceRepository.save(trace);
        }
    }
}
