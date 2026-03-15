package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.FileAssetRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FileAssetRecordRepository extends JpaRepository<FileAssetRecord, Long> {

    Optional<FileAssetRecord> findByFileNo(String fileNo);

    Optional<FileAssetRecord> findByIdempotencyKey(String idempotencyKey);

    Optional<FileAssetRecord> findFirstByStoredPathAndLatestVersionTrueOrderByCreatedAtDesc(String storedPath);
}
