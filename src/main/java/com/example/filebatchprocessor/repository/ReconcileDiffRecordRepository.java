package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.ReconcileDiffRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReconcileDiffRecordRepository extends JpaRepository<ReconcileDiffRecord, Long> {

    List<ReconcileDiffRecord> findTop200ByReconcileRunIdOrderByIdAsc(Long reconcileRunId);
}
