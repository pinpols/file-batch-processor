package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.ReconcileDiffRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReconcileDiffRecordRepository extends JpaRepository<ReconcileDiffRecord, Long> {

    List<ReconcileDiffRecord> findTop200ByReconcileRunIdOrderByIdAsc(Long reconcileRunId);
}
