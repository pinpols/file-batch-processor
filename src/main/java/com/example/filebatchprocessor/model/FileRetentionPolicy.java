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
@Table(name = "file_retention_policy")
public class FileRetentionPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_name", nullable = false, length = 64)
    private String policyName;

    @Column(name = "file_category", nullable = false, length = 32)
    private String fileCategory;

    @Column(name = "retention_days", nullable = false)
    private Integer retentionDays;

    @Column(name = "archive_before_delete", nullable = false)
    private Boolean archiveBeforeDelete = true;

    @Column(name = "check_dependency_before_archive", nullable = false)
    private Boolean checkDependencyBeforeArchive = true;

    @Column(name = "allow_manual_override", nullable = false)
    private Boolean allowManualOverride = false;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
