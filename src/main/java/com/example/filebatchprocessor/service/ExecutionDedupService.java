package com.example.filebatchprocessor.service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 基于数据库唯一约束的去重服务，支持多实例并发下的强一致去重。
 */
@Slf4j
@Service
public class ExecutionDedupService {

    private final JdbcTemplate jdbcTemplate;

    public ExecutionDedupService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public boolean tryAcquire(String dedupKey, String batchDate, String rerunId, long windowSeconds) {
        long safeWindow = Math.max(windowSeconds, 1L);
        long bucket = System.currentTimeMillis() / 1000L / safeWindow;
        String safeRerunId = rerunId == null ? "" : rerunId;

        // 用 INSERT ... ON CONFLICT DO NOTHING 抢占去重锁:重复键不抛异常(不会把事务标记成
        // rollback-only 而在提交时抛 UnexpectedRollbackException),并发下后到者阻塞到先到者提交后 DO NOTHING。
        // 受影响行数 == 1 表示本次抢到、0 表示已有人抢占。
        int inserted = jdbcTemplate.update(
                "INSERT INTO execution_dedup_records (dedup_key, batch_date, rerun_id, window_bucket, created_at) "
                        + "VALUES (?, ?, ?, ?, ?) "
                        + "ON CONFLICT (dedup_key, batch_date, rerun_id, window_bucket) DO NOTHING",
                dedupKey,
                batchDate,
                safeRerunId,
                bucket,
                Timestamp.valueOf(LocalDateTime.now()));

        if (inserted != 1) {
            log.info(
                    "Duplicate request rejected by DB dedup lock: dedupKey={}, batchDate={}, rerunId={}, bucket={}",
                    dedupKey,
                    batchDate,
                    rerunId,
                    bucket);
        }
        return inserted == 1;
    }
}
