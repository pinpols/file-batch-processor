package com.example.filebatchprocessor.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 清单驱动入库:到达组成员(reception_group_member)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "reception_group_member",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_group_member_file",
                        columnNames = {"group_id", "expected_file_name"}),
        indexes = {@Index(name = "idx_group_member_group", columnList = "group_id")})
public class ReceptionGroupMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "expected_file_name", nullable = false, length = 500)
    private String expectedFileName;

    @Column(name = "expected_record_count")
    private Long expectedRecordCount;

    @Column(name = "expected_checksum", length = 128)
    private String expectedChecksum;

    @Column(name = "checksum_algorithm", length = 20)
    private String checksumAlgorithm = "MD5";

    @Column(name = "required", nullable = false)
    private boolean required = true;

    @Column(name = "actual_queue_id")
    private Long actualQueueId;

    @Column(name = "actual_record_count")
    private Long actualRecordCount;

    @Column(name = "reconcile_status", length = 20)
    private String reconcileStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
