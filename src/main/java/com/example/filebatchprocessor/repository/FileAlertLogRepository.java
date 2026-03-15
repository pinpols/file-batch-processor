package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.FileAlertLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FileAlertLogRepository extends JpaRepository<FileAlertLog, Long> {

    List<FileAlertLog> findByResolvedFalseOrderByCreatedAtDesc();

    Page<FileAlertLog> findByResolvedFalse(Pageable pageable);

    Page<FileAlertLog> findByAcknowledgedFalse(Pageable pageable);

    List<FileAlertLog> findByFileRecordIdAndResolvedFalse(Long fileRecordId);

    long countByResolvedFalse();

    long countByAcknowledgedFalseAndResolvedFalse();
}
