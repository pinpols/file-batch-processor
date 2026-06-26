package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.model.ExecutionDedupRecord;
import com.example.filebatchprocessor.repository.ExecutionDedupRecordRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 基于数据库唯一约束的去重服务，支持多实例并发下的强一致去重。
 */
@Slf4j
@Service
public class ExecutionDedupService {

    private final ExecutionDedupRecordRepository dedupRecordRepository;

    public ExecutionDedupService(ExecutionDedupRecordRepository dedupRecordRepository) {
        this.dedupRecordRepository = dedupRecordRepository;
    }

    @Transactional
    public boolean tryAcquire(String dedupKey, String batchDate, String rerunId, long windowSeconds) {
        long safeWindow = Math.max(windowSeconds, 1L);
        long bucket = System.currentTimeMillis() / 1000L / safeWindow;

        ExecutionDedupRecord record = new ExecutionDedupRecord();
        record.setDedupKey(dedupKey);
        record.setBatchDate(batchDate);
        record.setRerunId(rerunId == null ? "" : rerunId);
        record.setWindowBucket(bucket);

        try {
            dedupRecordRepository.save(record);
            return true;
        } catch (DataIntegrityViolationException ex) {
            log.info(
                    "Duplicate request rejected by DB dedup lock: dedupKey={}, batchDate={}, rerunId={}, bucket={}",
                    dedupKey,
                    batchDate,
                    rerunId,
                    bucket);
            return false;
        }
    }
}
