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

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "batch_day_instance")
public class BatchDayInstance {

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
    private BatchDayStatus status = BatchDayStatus.OPEN;

    @Column(name = "frozen_at")
    private LocalDateTime frozenAt;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    @Column(name = "last_replay_session_id")
    private Long lastReplaySessionId;

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
