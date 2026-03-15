package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.FileAssetRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FileAssetRecordRepository extends JpaRepository<FileAssetRecord, Long> {

    Optional<FileAssetRecord> findByFileNo(String fileNo);

    Optional<FileAssetRecord> findByIdempotencyKey(String idempotencyKey);

    Optional<FileAssetRecord> findFirstByStoredPathAndLatestVersionTrueOrderByCreatedAtDesc(String storedPath);

    Page<FileAssetRecord> findByStatus(String status, Pageable pageable);

    Page<FileAssetRecord> findBySourceSystem(String sourceSystem, Pageable pageable);

    Page<FileAssetRecord> findByBizDate(String bizDate, Pageable pageable);

    Page<FileAssetRecord> findBySourceSystemAndBizDate(String sourceSystem, String bizDate, Pageable pageable);

    List<FileAssetRecord> findByFileNoIn(List<String> fileNos);
}
