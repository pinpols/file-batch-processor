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
            return false;
        }

        // 桶边界修复:固定时间桶 epoch/window 会让"刚好跨桶边界"的相邻请求落入不同 bucket 各自抢到。
        // 抢到当前桶后,再查上一桶是否已有同键近期记录;有则说明是边界重复,回滚本次抢占并判重。
        Long prevCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM execution_dedup_records "
                        + "WHERE dedup_key = ? AND batch_date = ? AND rerun_id = ? AND window_bucket = ?",
                Long.class,
                dedupKey,
                batchDate,
                safeRerunId,
                bucket - 1);
        if (prevCount != null && prevCount > 0) {
            jdbcTemplate.update(
                    "DELETE FROM execution_dedup_records "
                            + "WHERE dedup_key = ? AND batch_date = ? AND rerun_id = ? AND window_bucket = ?",
                    dedupKey,
                    batchDate,
                    safeRerunId,
                    bucket);
            log.info(
                    "Duplicate rejected at bucket boundary: dedupKey={}, batchDate={}, rerunId={}, bucket={}",
                    dedupKey,
                    batchDate,
                    rerunId,
                    bucket);
            return false;
        }
        return true;
    }
}
