package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.JobExecution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务执行记录 Repository
 */
@Repository
public interface JobExecutionRepository extends JpaRepository<JobExecution, Long> {

    /**
     * 按任务ID查询执行记录（按开始时间倒序）
     */
    Page<JobExecution> findByTaskIdOrderByStartTimeDesc(String taskId, Pageable pageable);

    /**
     * 查询今日执行记录
     */
    @Query("SELECT e FROM JobExecution e WHERE FUNCTION('DATE', e.startTime) = CURRENT_DATE")
    List<JobExecution> findTodayExecutions();

    /**
     * 按状态查询执行记录
     */
    List<JobExecution> findByStatus(String status);

    /**
     * 按任务ID和状态查询执行记录
     */
    List<JobExecution> findByTaskIdAndStatus(String taskId, String status);

    /**
     * 查询正在运行的执行记录
     */
    List<JobExecution> findByStatusIn(List<String> statuses);

    /**
     * 按任务ID和触发者查询执行记录
     */
    List<JobExecution> findByTaskIdAndTriggeredByOrderByStartTimeDesc(String taskId, String triggeredBy);

    /**
     * 查询指定时间范围内的执行记录
     */
    @Query("SELECT e FROM JobExecution e WHERE e.startTime >= :startTime AND e.startTime <= :endTime")
    List<JobExecution> findByStartTimeBetween(@Param("startTime") LocalDateTime startTime, 
                                           @Param("endTime") LocalDateTime endTime);

    /**
     * 统计任务执行次数
     */
    @Query("SELECT COUNT(e) FROM JobExecution e WHERE e.taskId = :taskId")
    Long countByTaskId(@Param("taskId") String taskId);

    /**
     * 统计任务成功执行次数
     */
    @Query("SELECT COUNT(e) FROM JobExecution e WHERE e.taskId = :taskId AND e.status = 'COMPLETED'")
    Long countSuccessfulExecutionsByTaskId(@Param("taskId") String taskId);

    /**
     * 统计任务失败执行次数
     */
    @Query("SELECT COUNT(e) FROM JobExecution e WHERE e.taskId = :taskId AND e.status = 'FAILED'")
    Long countFailedExecutionsByTaskId(@Param("taskId") String taskId);
}
