package com.example.filebatchprocessor.repository;


import com.example.filebatchprocessor.model.DlqRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DlqRecordRepository extends JpaRepository<DlqRecord, Long> {
}

