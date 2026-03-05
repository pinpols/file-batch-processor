package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.DagNodeRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DagNodeRunRepository extends JpaRepository<DagNodeRun, Long> {
    List<DagNodeRun> findByDagRunIdOrderByIdAsc(Long dagRunId);
    Optional<DagNodeRun> findByDagRunIdAndTaskId(Long dagRunId, String taskId);
}
