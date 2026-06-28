package com.example.filebatchprocessor.batch.writer;

import com.example.filebatchprocessor.batch.writer.strategy.BatchChunkImportStrategy;
import com.example.filebatchprocessor.batch.writer.strategy.ChunkImportStrategy;
import com.example.filebatchprocessor.batch.writer.strategy.ImportContext;
import com.example.filebatchprocessor.exception.TransientImportException;
import com.example.filebatchprocessor.model.FileRecord;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.util.StringUtils;

/**
 * 导入文件写入器(Strategy 模式的 Context)。
 *
 * <p>对每个 chunk:先批内去重(有界,防超大文件 OOM),再优先走 {@link ChunkImportStrategy 批量快路径};
 * 批量失败(非瞬时)时降级到逐条韧性路径,坏记录落 DLQ 而不连累整批。瞬时异常上抛交给 Spring Batch 重试。
 *
 * <p>落库幂等最终由唯一索引 (business_key, batch_date, partition_key) 保证。
 */
public class FileImportRecordWriter implements ItemWriter<FileRecord>, StepExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(FileImportRecordWriter.class);
    private static final DateTimeFormatter BATCH_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private final String batchDate;
    private final ChunkImportStrategy batchStrategy;
    private final ChunkImportStrategy fallbackStrategy;
    /** 批内去重集合的上限:达到后不再缓存新键,仅靠 DB 唯一约束兜底,避免超大文件把整张键表读进内存。 */
    private final int maxDedupKeys;
    /** feed 模式的业务键字段列表;默认路径为 null(退回 name:batchDate)。 */
    private final List<String> businessKeyFields;

    private final Set<String> seenKeys = new HashSet<>();
    private long writeCount = 0L;

    private ImportContext context;

    public FileImportRecordWriter(
            String batchDate,
            ChunkImportStrategy batchStrategy,
            ChunkImportStrategy fallbackStrategy,
            int maxDedupKeys) {
        this(batchDate, batchStrategy, fallbackStrategy, maxDedupKeys, null);
    }

    public FileImportRecordWriter(
            String batchDate,
            ChunkImportStrategy batchStrategy,
            ChunkImportStrategy fallbackStrategy,
            int maxDedupKeys,
            List<String> businessKeyFields) {
        this.batchDate =
                StringUtils.hasText(batchDate) ? batchDate : LocalDate.now().format(BATCH_DATE_FORMATTER);
        this.batchStrategy = batchStrategy;
        this.fallbackStrategy = fallbackStrategy;
        this.maxDedupKeys = maxDedupKeys > 0 ? maxDedupKeys : Integer.MAX_VALUE;
        this.businessKeyFields = businessKeyFields;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        writeCount = 0L;
        seenKeys.clear();

        Long jobExecutionId = stepExecution.getJobExecutionId();
        String inputFileName;
        try {
            inputFileName = stepExecution.getJobParameters().getString("input.file.name");
        } catch (Exception e) {
            log.warn("Failed to get input.file.name from job parameters", e);
            inputFileName = null;
        }
        this.context = new ImportContext(batchDate, jobExecutionId, inputFileName, businessKeyFields);
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
        List<FileRecord> fresh = dedup(items);
        if (fresh.isEmpty()) {
            return;
        }
        log.info("Writing {} records for batchDate={}", fresh.size(), batchDate);

        try {
            writeCount += batchStrategy.persist(fresh, context);
        } catch (TransientImportException e) {
            // 瞬时故障:上抛由 Spring Batch 重试,不降级
            throw e;
        } catch (Exception e) {
            log.warn(
                    "Batch import failed for {} records, falling back to per-record path: {}",
                    fresh.size(),
                    e.getMessage());
            writeCount += fallbackStrategy.persist(fresh, context);
        }
    }

    /** 批内去重:同 businessKey 只保留一条;去重集合有界。被跳过的重复记录计入 writeCount(视为已处理)。 */
    private List<FileRecord> dedup(List<? extends FileRecord> items) {
        List<FileRecord> fresh = new ArrayList<>(items.size());
        for (FileRecord item : items) {
            // 去重口径必须与 strategy 落库口径逐字一致,否则去重与唯一索引错位。
            String bizKey = BatchChunkImportStrategy.businessKeyOf(item, context);
            if (seenKeys.size() < maxDedupKeys) {
                if (!seenKeys.add(bizKey)) {
                    log.debug("Skipping duplicate record in current batch: {}", bizKey);
                    writeCount++; // 视为已处理
                    continue;
                }
            }
            fresh.add(item);
        }
        return fresh;
    }
}
