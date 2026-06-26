package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.FileAlertLog;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FileAlertLogRepository extends JpaRepository<FileAlertLog, Long> {

    List<FileAlertLog> findByResolvedFalseOrderByCreatedAtDesc();

    Page<FileAlertLog> findByResolvedFalse(Pageable pageable);

    Page<FileAlertLog> findByAcknowledgedFalse(Pageable pageable);

    List<FileAlertLog> findByFileRecordIdAndResolvedFalse(Long fileRecordId);

    long countByResolvedFalse();

    long countByAcknowledgedFalseAndResolvedFalse();
}
