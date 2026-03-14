package com.example.filebatchprocessor.batch.writer;

import com.example.filebatchprocessor.model.FileRecord;
import com.example.filebatchprocessor.model.DlqRecord;
import com.example.filebatchprocessor.model.ImportedRecordPartitioned;
import com.example.filebatchprocessor.model.RecordTrace;
import com.example.filebatchprocessor.exception.TransientImportException;
import com.example.filebatchprocessor.exception.ErrorCodeClassifier;
import com.example.filebatchprocessor.repository.DlqRecordRepository;
import com.example.filebatchprocessor.repository.RecordTraceRepository;
import com.example.filebatchprocessor.service.PartitionedImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;

import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 导入文件写入器：写表 imported_records，依赖唯一索引保证落库幂等。
 */
public class FileImportRecordWriter implements ItemWriter<FileRecord>, StepExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(FileImportRecordWriter.class);
    private static final DateTimeFormatter BATCH_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private final String batchDate;
    // 分区导入服务，负责写入分区表并保证幂等
    private final PartitionedImportService partitionedImportService;
    private final DlqRecordRepository dlqRecordRepository;
    private final RecordTraceRepository recordTraceRepository;
    private final Set<String> seenKeys = new HashSet<>();
    private long writeCount = 0L;

    private Long jobExecutionId;
    private String inputFileName;

    // 使用 TransactionTemplate 在每条记录上开启 REQUIRES_NEW 事务，避免单条写入失败影响外层 chunk 事务
    private final TransactionTemplate txTemplate;

    public FileImportRecordWriter(String batchDate, PartitionedImportService partitionedImportService,
                                  DlqRecordRepository dlqRecordRepository,
                                  RecordTraceRepository recordTraceRepository,
                                  PlatformTransactionManager transactionManager) {
        this.batchDate = StringUtils.hasText(batchDate)
                ? batchDate
                : LocalDate.now().format(BATCH_DATE_FORMATTER);
        this.partitionedImportService = partitionedImportService;
        this.dlqRecordRepository = dlqRecordRepository;
        this.recordTraceRepository = recordTraceRepository;
        this.txTemplate = new TransactionTemplate(transactionManager);
        this.txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }



    private String buildBusinessKey(FileRecord item) {
        String name = item.getName() == null ? "unknown" : item.getName();
        return name + ":" + batchDate;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        writeCount = 0L;
        seenKeys.clear();

        this.jobExecutionId = stepExecution.getJobExecutionId();
        try {
            this.inputFileName = stepExecution.getJobParameters().getString("input.file.name");
        } catch (Exception e) {
            log.warn("Failed to get input.file.name from job parameters", e);
            this.inputFileName = null;
        }
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        stepExecution.getExecutionContext().putLong("write.count", writeCount);
        return stepExecution.getExitStatus();
    }

    @Override
    public void write(Chunk<? extends FileRecord> chunk) throws Exception {
        write(chunk.getItems());
    }

    public void write(List<? extends FileRecord> items) {
        log.info("Writing {} records for batchDate={}", items.size(), batchDate);

        for (FileRecord item : items) {
            String bizKey = buildBusinessKey(item);
            
            // 内存去重：跳过本批次中已处理的记录
            if (!seenKeys.add(bizKey)) {
                log.debug("Skipping duplicate record in current batch: {}", bizKey);
                writeCount++; // 视为已处理
                continue;
            }
            
            // 使用分区导入服务在独立事务中写入分区表，服务负责幂等性检查
            try {
                ImportedRecordPartitioned saved = txTemplate.execute(status ->
                        partitionedImportService.importRecord(
                                bizKey, item.getName(), item.getDescription(), batchDate, inputFileName, null
                        )
                );
                persistTrace(bizKey, item.getLineNo(), saved == null ? null : saved.getId());
                log.debug("Persisted or reused record key={} data={}", bizKey, item);
                writeCount++;
            } catch (TransientImportException e) {
                log.warn("Transient error for key={}, will trigger retry", bizKey, e);
                throw e;
            } catch (Exception e) {
                log.error("Failed to persist record key={} for batchDate={}", bizKey, batchDate, e);
                persistDlq(bizKey, item, e);
                throw e;
            }
        }
    }

    private void persistTrace(String businessKey, Long lineNo, Long importedRecordPartitionId) {
        try {
            RecordTrace trace = new RecordTrace();
            trace.setBusinessKey(businessKey);
            trace.setBatchDate(batchDate);
            trace.setJobName("importJob");
            trace.setJobExecutionId(jobExecutionId);
            trace.setSourceFileName(inputFileName);
            trace.setLineNo(lineNo);
            trace.setImportedRecordPartitionId(importedRecordPartitionId);
            recordTraceRepository.save(trace);
        } catch (Exception ex) {
            log.error("Failed to persist record trace for key={}", businessKey, ex);
        }
    }

    private void persistDlq(String bizKey, FileRecord item, Exception e) {
        try {
            DlqRecord record = new DlqRecord();
            record.setJobName("importJob");
            String encodedName = item.getName() == null ? "" : item.getName().replace("&", "%26").replace("=", "%3D");
            String encodedDesc = item.getDescription() == null ? "" : item.getDescription().replace("&", "%26").replace("=", "%3D");
            record.setParams("businessKey=" + bizKey
                    + "&name=" + encodedName
                    + "&description=" + encodedDesc
                    + "&batchDate=" + batchDate
                    + "&source=record-writer");
            record.setErrorMessage(truncate("Record persist failed: " + e.getMessage(), 1000));
            record.setErrorCode(ErrorCodeClassifier.classify(e).name());
            record.setHandled(false);
            record.setRetryable(true);
            record.setManualRequired(false);
            record.setCompensationStatus("PENDING");
            record.setNextRetryAt(java.time.LocalDateTime.now());
            dlqRecordRepository.save(record);
        } catch (Exception ex) {
            log.error("Failed to persist record-level DLQ message for key={}", bizKey, ex);
        }
    }

    private String truncate(String value, int maxLen) {
        if (value == null || value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen);
    }
}

