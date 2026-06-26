package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.ReconcileRunRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReconcileRunRecordRepository extends JpaRepository<ReconcileRunRecord, Long> {

    List<ReconcileRunRecord> findTop50ByOrderByCreatedAtDesc();
}
