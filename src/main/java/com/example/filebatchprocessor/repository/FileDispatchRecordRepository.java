package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.FileDispatchRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FileDispatchRecordRepository extends JpaRepository<FileDispatchRecord, Long> {

    Optional<FileDispatchRecord> findByLegacyDistributionTaskId(Long legacyDistributionTaskId);

    Optional<FileDispatchRecord> findByDispatchKey(String dispatchKey);

    Optional<FileDispatchRecord> findByDispatchNo(String dispatchNo);

    List<FileDispatchRecord> findByAckRequiredTrueAndAckStatus(String ackStatus);
}
