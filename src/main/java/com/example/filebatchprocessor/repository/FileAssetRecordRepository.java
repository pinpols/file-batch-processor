package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.FileAssetRecord;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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

    @Query(
            "SELECT f FROM FileAssetRecord f WHERE f.status = :status AND f.arrivedTime < :threshold ORDER BY f.arrivedTime ASC LIMIT :limit")
    List<FileAssetRecord> findTimeoutFiles(
            @Param("status") String status, @Param("threshold") LocalDateTime threshold, @Param("limit") int limit);

    @Query("SELECT COUNT(f) FROM FileAssetRecord f WHERE f.status IN :statuses AND f.createdAt < :threshold")
    long countPendingFiles(@Param("statuses") List<String> statuses, @Param("threshold") LocalDateTime threshold);

    long countByStatusAndFileDirection(String status, String direction);

    long countByStatusInAndFileDirection(List<String> statuses, String direction);

    @Query(
            "SELECT f FROM FileAssetRecord f WHERE f.fileDirection = :category AND f.processedTime < :threshold AND f.deletedFlag = false AND f.archived = false ORDER BY f.processedTime ASC LIMIT :limit")
    List<FileAssetRecord> findFilesForArchive(
            @Param("category") String category, @Param("threshold") LocalDateTime threshold, @Param("limit") int limit);
}
