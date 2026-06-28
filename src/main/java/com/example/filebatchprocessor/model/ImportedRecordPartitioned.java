package com.example.filebatchprocessor.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 导入记录分区表：支持按 batch_date 分区，提高查询性能
 * 物理表结构：imported_records_2025_01, imported_records_2025_02 ...
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "imported_records_partition",
        indexes = {
            @Index(
                    name = "uk_import_biz_batch_part",
                    columnList = "business_key,batch_date,partition_key",
                    unique = true),
            @Index(name = "idx_batch_date_part", columnList = "batch_date"),
            @Index(name = "idx_partition_key", columnList = "partition_key")
        })
public class ImportedRecordPartitioned {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_key", length = 200, nullable = false)
    private String businessKey;

    @Column(length = 200)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "batch_date", length = 20, nullable = false)
    private String batchDate;

    /**
     * 分区键：用于支持按年月分区
     * 格式：yyyy_MM
     */
    @Column(name = "partition_key", length = 10, nullable = false)
    private String partitionKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "checksum", length = 64)
    private String checksum; // MD5/SHA256 用于数据完整性校验

    @Column(name = "source_file_name")
    private String sourceFileName;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "attributes", columnDefinition = "jsonb")
    private Map<String, Object> attributes;

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Explicit getters and setters as workaround for Lombok annotation processing issue

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBusinessKey() {
        return businessKey;
    }

    public void setBusinessKey(String businessKey) {
        this.businessKey = businessKey;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getBatchDate() {
        return batchDate;
    }

    public void setBatchDate(String batchDate) {
        this.batchDate = batchDate;
    }

    public String getPartitionKey() {
        return partitionKey;
    }

    public void setPartitionKey(String partitionKey) {
        this.partitionKey = partitionKey;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public String getSourceFileName() {
        return sourceFileName;
    }

    public void setSourceFileName(String sourceFileName) {
        this.sourceFileName = sourceFileName;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }
}
