package com.example.filebatchprocessor.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "dag_definition")
public class DagDefinition {

    @Id
    @Column(name = "dag_id", length = 128)
    private String dagId;

    @Column(name = "dag_name", nullable = false, length = 256)
    private String dagName;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "fail_fast", nullable = false)
    private Boolean failFast = true;

    @Column(name = "max_duration_ms")
    private Long maxDurationMs;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
