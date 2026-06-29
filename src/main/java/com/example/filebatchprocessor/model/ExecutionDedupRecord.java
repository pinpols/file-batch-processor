package com.example.filebatchprocessor.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 跨实例去重锁记录：同一时间窗内，同一 dedupKey 只允许一个执行请求落地。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "execution_dedup_records")
public class ExecutionDedupRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dedup_key", nullable = false, length = 256)
    private String dedupKey;

    @Column(name = "batch_date", nullable = false, length = 32)
    private String batchDate;

    @Column(name = "rerun_id", nullable = false, length = 128)
    private String rerunId;

    @Column(name = "window_bucket", nullable = false)
    private Long windowBucket;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    // 显式访问器用于稳定 JPA/测试反射行为，避免依赖 Lombok 生成细节。

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDedupKey() {
        return dedupKey;
    }

    public void setDedupKey(String dedupKey) {
        this.dedupKey = dedupKey;
    }

    public String getBatchDate() {
        return batchDate;
    }

    public void setBatchDate(String batchDate) {
        this.batchDate = batchDate;
    }

    public String getRerunId() {
        return rerunId;
    }

    public void setRerunId(String rerunId) {
        this.rerunId = rerunId;
    }

    public Long getWindowBucket() {
        return windowBucket;
    }

    public void setWindowBucket(Long windowBucket) {
        this.windowBucket = windowBucket;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
