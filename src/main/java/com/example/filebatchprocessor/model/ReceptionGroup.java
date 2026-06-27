package com.example.filebatchprocessor.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 清单驱动入库:到达组(reception_group)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "reception_group",
        indexes = {@Index(name = "idx_reception_group_status", columnList = "status")})
public class ReceptionGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "manifest_id", nullable = false, unique = true, length = 200)
    private String manifestId;

    @Column(name = "source_system", length = 100)
    private String sourceSystem;

    @Column(name = "biz_date", length = 32)
    private String bizDate;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "WAITING_FILES";

    @Column(name = "total_members", nullable = false)
    private Integer totalMembers = 0;

    @Column(name = "arrived_members", nullable = false)
    private Integer arrivedMembers = 0;

    @Column(name = "deadline")
    private LocalDateTime deadline;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
