package com.example.filebatchprocessor.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "file_record", indexes = {
        @Index(name = "uk_file_record_file_no", columnList = "file_no", unique = true),
        @Index(name = "idx_file_record_status", columnList = "status"),
        @Index(name = "idx_file_record_source_biz_date", columnList = "source_system,biz_date"),
        @Index(name = "idx_file_record_hash", columnList = "file_hash"),
        @Index(name = "idx_file_record_parent", columnList = "parent_file_id"),
        @Index(name = "idx_file_record_tenant_domain", columnList = "tenant_id,biz_domain"),
        @Index(name = "idx_file_record_stored_path_latest", columnList = "stored_path,latest_version")
})
public class FileAssetRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_no", nullable = false, length = 64)
    private String fileNo;

    @Column(name = "source_system", length = 100)
    private String sourceSystem;

    @Column(name = "biz_type", length = 64)
    private String bizType;

    @Column(name = "file_direction", nullable = false, length = 16)
    private String fileDirection = "INBOUND";

    @Column(name = "original_name", nullable = false, length = 500)
    private String originalName;

    @Column(name = "stored_name", nullable = false, length = 500)
    private String storedName;

    @Column(name = "stored_path", nullable = false, length = 1000)
    private String storedPath;

    @Column(name = "storage_type", nullable = false, length = 32)
    private String storageType = "LOCAL";

    @Column(name = "file_size", nullable = false)
    private Long fileSize = 0L;

    @Column(name = "file_hash", length = 128)
    private String fileHash;

    @Column(name = "idempotency_key", length = 256)
    private String idempotencyKey;

    @Column(name = "hash_algorithm", nullable = false, length = 32)
    private String hashAlgorithm = "MD5";

    @Column(name = "integrity_verified", nullable = false)
    private Boolean integrityVerified = Boolean.FALSE;

    @Column(name = "file_ext", length = 32)
    private String fileExt;

    @Column(name = "mime_type", length = 128)
    private String mimeType;

    @Column(length = 32)
    private String charset;

    @Column(name = "biz_date", length = 32)
    private String bizDate;

    @Column(name = "batch_no", length = 64)
    private String batchNo;

    @Column(name = "tenant_id", length = 64)
    private String tenantId;

    @Column(name = "biz_domain", length = 64)
    private String bizDomain;

    @Column(name = "parent_file_id")
    private Long parentFileId;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo = 1;

    @Column(name = "latest_version", nullable = false)
    private Boolean latestVersion = Boolean.TRUE;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "archive_required", nullable = false)
    private Boolean archiveRequired = Boolean.FALSE;

    @Column(nullable = false)
    private Boolean archived = Boolean.FALSE;

    @Column(nullable = false)
    private Boolean deletable = Boolean.FALSE;

    @Column(name = "deleted_flag", nullable = false)
    private Boolean deletedFlag = Boolean.FALSE;

    @Column(name = "arrived_time")
    private LocalDateTime arrivedTime;

    @Column(name = "ready_time")
    private LocalDateTime readyTime;

    @Column(name = "processing_start_time")
    private LocalDateTime processingStartTime;

    @Column(name = "processed_time")
    private LocalDateTime processedTime;

    @Column(name = "archived_time")
    private LocalDateTime archivedTime;

    @Column(name = "deleted_time")
    private LocalDateTime deletedTime;

    @Column(name = "retention_until")
    private LocalDateTime retentionUntil;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
