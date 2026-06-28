package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.FileReceptionQueue;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface FileReceptionQueueRepository extends JpaRepository<FileReceptionQueue, Long> {

    /**
     * 根据文件名查找
     */
    Optional<FileReceptionQueue> findByFileName(String fileName);

    /**
     * 查找指定状态的文件
     */
    List<FileReceptionQueue> findByStatus(String status);

    /**
     * 查找等待中的文件
     */
    List<FileReceptionQueue> findByStatusOrderByCreatedAtAsc(String status);

    /**
     * 查找超时的文件（未按预期时间处理）
     */
    @Query(
            "SELECT f FROM FileReceptionQueue f WHERE f.expectedProcessTime < :now AND f.status IN ('RECEIVED', 'WAITING')")
    List<FileReceptionQueue> findOverdueFiles(LocalDateTime now);

    /**
     * 查找需要重试的文件（失败且重试次数未超限）
     */
    @Query(
            "SELECT f FROM FileReceptionQueue f WHERE f.status = 'FAILED' AND f.retryCount < 3 AND f.updatedAt < :beforeTime")
    List<FileReceptionQueue> findRetriableFiles(LocalDateTime beforeTime);

    /**
     * 按来源系统查找
     */
    List<FileReceptionQueue> findBySourceSystem(String sourceSystem);

    List<FileReceptionQueue> findByFileRecordId(Long fileRecordId);

    /**
     * 统计指定时间范围内接收的文件数
     */
    long countByCreatedAtBetween(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 按状态统计
     */
    long countByStatus(String status);

    long countByFileRecordIdAndStatusIn(Long fileRecordId, List<String> statuses);
}
