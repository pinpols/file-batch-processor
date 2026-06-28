package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.TaskExecutionState;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TaskExecutionStateRepository extends JpaRepository<TaskExecutionState, Long> {
    Optional<TaskExecutionState> findByTaskIdAndBatchDateAndRerunId(String taskId, String batchDate, String rerunId);

    List<TaskExecutionState> findTop200ByOrderByUpdatedAtDesc();

    List<TaskExecutionState> findTop200ByStatusInAndUpdatedAtBefore(List<String> statuses, LocalDateTime cutoff);

    // #16:misfire 检测——按 next_retry_at 索引直接查,替代全表 findAll 后内存过滤
    List<TaskExecutionState> findTop100ByStatusAndNextRetryAtBeforeOrderByNextRetryAtAsc(
            String status, LocalDateTime threshold);

    long countByStatusIn(List<String> statuses);

    long countByStatusAndUpdatedAtAfter(String status, LocalDateTime after);

    long deleteByUpdatedAtBeforeAndStatusIn(LocalDateTime cutoffTime, List<String> statuses);
}
