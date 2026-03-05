package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.ReconcileRunRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReconcileRunRecordRepository extends JpaRepository<ReconcileRunRecord, Long> {

    List<ReconcileRunRecord> findTop50ByOrderByCreatedAtDesc();
}
