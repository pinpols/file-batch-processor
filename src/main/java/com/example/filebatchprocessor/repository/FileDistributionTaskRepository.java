package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.FileDistributionTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface FileDistributionTaskRepository extends JpaRepository<FileDistributionTask, Long> {

    /**
     * 查找待分发的任务
     */
    List<FileDistributionTask> findByStatus(String status);

    /**
     * 查找可重试任务（状态为 RETRY 且重试次数未超限）
     */
    @Query("SELECT t FROM FileDistributionTask t WHERE t.status = 'RETRY' AND t.retryCount < t.maxRetries AND t.updatedAt < :beforeTime")
    List<FileDistributionTask> findRetriableTasks(LocalDateTime beforeTime);

    /**
     * 查找指定目标系统的任务
     */
    List<FileDistributionTask> findByTargetSystem(String targetSystem);

    /**
     * 查找指定目标地址的任务
     */
    List<FileDistributionTask> findByTargetAddress(String targetAddress);

    /**
     * 查找超时的任务（未在指定时间内完成）
     */
    @Query("SELECT t FROM FileDistributionTask t WHERE t.status IN ('PENDING', 'IN_PROGRESS') AND t.createdAt < :timeout")
    List<FileDistributionTask> findTimeoutTasks(LocalDateTime timeout);

    /**
     * 统计指定状态的任务数
     */
    long countByStatus(String status);

    /**
     * 统计指定目标系统的任务数
     */
    long countByTargetSystem(String targetSystem);

    /**
     * 查找时间范围内完成的任务
     */
    @Query("SELECT t FROM FileDistributionTask t WHERE t.status = 'SUCCESS' AND t.completedTime BETWEEN :startTime AND :endTime")
    List<FileDistributionTask> findCompletedTasksBetween(LocalDateTime startTime, LocalDateTime endTime);
}
