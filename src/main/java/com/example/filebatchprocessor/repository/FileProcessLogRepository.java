package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.FileProcessLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FileProcessLogRepository extends JpaRepository<FileProcessLog, Long> {
}
