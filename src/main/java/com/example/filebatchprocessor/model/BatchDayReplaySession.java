package com.example.filebatchprocessor.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "batch_day_replay_session")
public class BatchDayReplaySession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "calendar_code", nullable = false, length = 64)
    private String calendarCode;

    @Column(name = "biz_date", nullable = false)
    private LocalDate bizDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BatchDayReplayScope scope;

    @Column(name = "scope_payload", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String scopePayload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BatchDayReplayStatus status = BatchDayReplayStatus.RUNNING;

    @Column(name = "total_count", nullable = false)
    private Integer totalCount = 0;

    @Column(name = "succeeded_count", nullable = false)
    private Integer succeededCount = 0;

    @Column(name = "failed_count", nullable = false)
    private Integer failedCount = 0;

    @Column(name = "in_flight_count", nullable = false)
    private Integer inFlightCount = 0;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(name = "requested_by", nullable = false, length = 128)
    private String requestedBy;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
