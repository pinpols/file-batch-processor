package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.TaskExecutionState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TaskExecutionStateRepository extends JpaRepository<TaskExecutionState, Long> {
    Optional<TaskExecutionState> findByTaskIdAndBatchDateAndRerunId(String taskId, String batchDate, String rerunId);
    List<TaskExecutionState> findTop200ByOrderByUpdatedAtDesc();
    List<TaskExecutionState> findTop200ByStatusInAndUpdatedAtBefore(List<String> statuses, LocalDateTime cutoff);
    long countByStatusIn(List<String> statuses);
    long countByStatusAndUpdatedAtAfter(String status, LocalDateTime after);
    long deleteByUpdatedAtBeforeAndStatusIn(LocalDateTime cutoffTime, List<String> statuses);
}
