package com.example.filebatchprocessor.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "target_system_circuit_state",
        indexes = {
            @Index(name = "idx_target_system_circuit_status", columnList = "status"),
            @Index(name = "idx_target_system_circuit_cooldown_until", columnList = "cooldown_until")
        })
public class TargetSystemCircuitState {

    @Id
    @Column(name = "target_system", length = 128)
    private String targetSystem;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "last_failure_at")
    private LocalDateTime lastFailureAt;

    @Column(name = "window_failure_count", nullable = false)
    private long windowFailureCount;

    @Column(name = "window_size", nullable = false)
    private long windowSize;

    @Column(name = "failure_rate_threshold", nullable = false)
    private double failureRateThreshold;

    @Column(name = "cooldown_duration_ms", nullable = false)
    private long cooldownDurationMs;

    @Column(name = "cooldown_until")
    private LocalDateTime cooldownUntil;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
