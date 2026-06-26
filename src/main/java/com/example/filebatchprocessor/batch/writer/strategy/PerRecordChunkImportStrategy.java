package com.example.filebatchprocessor.batch.writer.strategy;

import com.example.filebatchprocessor.exception.ErrorCodeClassifier;
import com.example.filebatchprocessor.exception.TransientImportException;
import com.example.filebatchprocessor.model.DlqRecord;
import com.example.filebatchprocessor.model.FileRecord;
import com.example.filebatchprocessor.model.ImportedRecordPartitioned;
import com.example.filebatchprocessor.model.RecordTrace;
import com.example.filebatchprocessor.repository.DlqRecordRepository;
import com.example.filebatchprocessor.repository.RecordTraceRepository;
import com.example.filebatchprocessor.service.PartitionedImportService;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 逐条韧性路径(批量失败时的降级)。
 *
 * <p>每条记录在 REQUIRES_NEW 独立事务中写入,使单条失败不连累整批;坏记录落 DLQ 后上抛,
 * 交给 Spring Batch 的 skip/retry 机制处理。落库幂等由 {@link PartitionedImportService#importRecord} 保证。
 */
public class PerRecordChunkImportStrategy implements ChunkImportStrategy {

    private static final Logger log = LoggerFactory.getLogger(PerRecordChunkImportStrategy.class);
    private static final String JOB_NAME = "importJob";

    private final PartitionedImportService partitionedImportService;
    private final DlqRecordRepository dlqRecordRepository;
    private final RecordTraceRepository recordTraceRepository;
    private final TransactionTemplate txTemplate;

    public PerRecordChunkImportStrategy(
            PartitionedImportService partitionedImportService,
            DlqRecordRepository dlqRecordRepository,
            RecordTraceRepository recordTraceRepository,
            PlatformTransactionManager transactionManager) {
        this.partitionedImportService = partitionedImportService;
        this.dlqRecordRepository = dlqRecordRepository;
        this.recordTraceRepository = recordTraceRepository;
        this.txTemplate = new TransactionTemplate(transactionManager);
        this.txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public int persist(List<? extends FileRecord> records, ImportContext context) {
        int written = 0;
        for (FileRecord item : records) {
            String bizKey = context.buildBusinessKey(item.getName());
            try {
                ImportedRecordPartitioned saved = txTemplate.execute(status -> partitionedImportService.importRecord(
                        bizKey,
                        item.getName(),
                        item.getDescription(),
                        context.batchDate(),
                        context.inputFileName(),
                        null));
                persistTrace(bizKey, item.getLineNo(), saved == null ? null : saved.getId(), context);
                written++;
            } catch (TransientImportException e) {
                log.warn("Transient error for key={}, will trigger retry", bizKey, e);
                throw e;
            } catch (Exception e) {
                log.error("Failed to persist record key={} for batchDate={}", bizKey, context.batchDate(), e);
                persistDlq(bizKey, item, context, e);
                throw e;
            }
        }
        return written;
    }

    private void persistTrace(String businessKey, Long lineNo, Long importedRecordPartitionId, ImportContext context) {
        try {
            RecordTrace trace = new RecordTrace();
            trace.setBusinessKey(businessKey);
            trace.setBatchDate(context.batchDate());
            trace.setJobName(JOB_NAME);
            trace.setJobExecutionId(context.jobExecutionId());
            trace.setSourceFileName(context.inputFileName());
            trace.setLineNo(lineNo);
            trace.setImportedRecordPartitionId(importedRecordPartitionId);
            recordTraceRepository.save(trace);
        } catch (Exception ex) {
            log.error("Failed to persist record trace for key={}", businessKey, ex);
        }
    }

    private void persistDlq(String bizKey, FileRecord item, ImportContext context, Exception e) {
        try {
            DlqRecord record = new DlqRecord();
            record.setJobName(JOB_NAME);
            String encodedName = item.getName() == null
                    ? ""
                    : item.getName().replace("&", "%26").replace("=", "%3D");
            String encodedDesc = item.getDescription() == null
                    ? ""
                    : item.getDescription().replace("&", "%26").replace("=", "%3D");
            record.setParams("businessKey=" + bizKey
                    + "&name=" + encodedName
                    + "&description=" + encodedDesc
                    + "&batchDate=" + context.batchDate()
                    + "&source=record-writer");
            record.setErrorMessage(truncate("Record persist failed: " + e.getMessage(), 1000));
            record.setErrorCode(ErrorCodeClassifier.classify(e).name());
            record.setHandled(false);
            record.setRetryable(true);
            record.setManualRequired(false);
            record.setCompensationStatus("PENDING");
            record.setNextRetryAt(LocalDateTime.now());
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
