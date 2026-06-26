package com.example.filebatchprocessor.batch.writer.strategy;

import com.example.filebatchprocessor.model.FileRecord;
import java.util.List;

/**
 * Chunk 持久化策略(Strategy 模式)。
 *
 * <p>两种实现可互换:
 *
 * <ul>
 *   <li>{@link BatchChunkImportStrategy}:快路径,一次批量 INSERT ... ON CONFLICT,吞吐高;
 *   <li>{@link PerRecordChunkImportStrategy}:韧性路径,逐条独立事务 + 坏记录落 DLQ,粒度细。
 * </ul>
 *
 * <p>{@link FileImportRecordWriter} 作为 Context:默认走批量,失败时降级到逐条,
 * 兼顾吞吐与「单条坏记录不连累整批」。
 */
public interface ChunkImportStrategy {

    /**
     * 持久化一批(已完成批内去重的)记录。
     *
     * @return 成功写入(含幂等命中)的条数
     */
    int persist(List<? extends FileRecord> records, ImportContext context);
}
