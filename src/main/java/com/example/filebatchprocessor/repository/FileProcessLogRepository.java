package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.FileProcessLog;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FileProcessLogRepository extends JpaRepository<FileProcessLog, Long> {

    List<FileProcessLog> findByFileRecordIdOrderByCreatedAtDesc(Long fileRecordId);

    Page<FileProcessLog> findByFileRecordId(Long fileRecordId, Pageable pageable);

    List<FileProcessLog> findByTaskIdOrderByCreatedAtDesc(String taskId);

    Page<FileProcessLog> findByResult(String result, Pageable pageable);
}
