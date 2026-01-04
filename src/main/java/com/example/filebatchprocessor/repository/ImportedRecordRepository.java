package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.ImportedRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ImportedRecordRepository extends JpaRepository<ImportedRecord, Long> {
}


