package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.FileDispatchRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FileDispatchRecordRepository extends JpaRepository<FileDispatchRecord, Long> {

    Optional<FileDispatchRecord> findByLegacyDistributionTaskId(Long legacyDistributionTaskId);

    Optional<FileDispatchRecord> findByDispatchKey(String dispatchKey);

    Optional<FileDispatchRecord> findByDispatchNo(String dispatchNo);

    List<FileDispatchRecord> findByAckRequiredTrueAndAckStatus(String ackStatus);

    List<FileDispatchRecord> findByFileRecordIdOrderByCreatedAtDesc(Long fileRecordId);

    Page<FileDispatchRecord> findByFileRecordId(Long fileRecordId, Pageable pageable);

    Page<FileDispatchRecord> findByTargetSystem(String targetSystem, Pageable pageable);

    Page<FileDispatchRecord> findByDispatchStatus(String dispatchStatus, Pageable pageable);

    Page<FileDispatchRecord> findByAckStatus(String ackStatus, Pageable pageable);

    @Query("SELECT d FROM FileDispatchRecord d WHERE d.dispatchStatus = :status AND d.ackRequired = true AND d.lastDispatchTime < :threshold ORDER BY d.lastDispatchTime ASC LIMIT :limit")
    List<FileDispatchRecord> findAckTimeoutDispatches(@Param("status") String status, 
                                                      @Param("threshold") LocalDateTime threshold,
                                                      @Param("limit") int limit);

    long countByDispatchStatus(String status);

    long countByAckStatus(String ackStatus);

    long countByDispatchStatusInAndAckRequired(List<String> statuses, boolean ackRequired);
}
