package com.example.filebatchprocessor.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "record_trace", indexes = {
        @Index(name = "idx_record_trace_business_key", columnList = "business_key"),
        @Index(name = "idx_record_trace_business_batch", columnList = "business_key,batch_date"),
        @Index(name = "idx_record_trace_created_at", columnList = "created_at")
})
public class RecordTrace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_key", nullable = false, length = 200)
    private String businessKey;

    @Column(name = "batch_date", length = 20)
    private String batchDate;

    @Column(name = "job_name", nullable = false, length = 128)
    private String jobName;

    @Column(name = "job_execution_id")
    private Long jobExecutionId;

    @Column(name = "source_file_name", length = 1024)
    private String sourceFileName;

    @Column(name = "output_file_name", length = 1024)
    private String outputFileName;

    @Column(name = "line_no")
    private Long lineNo;

    @Column(name = "imported_record_partition_id")
    private Long importedRecordPartitionId;

    @Column(name = "event_type", length = 64)
    private String eventType;

    @Column(name = "status", length = 32)
    private String status;

    @Column(name = "message", length = 1000)
    private String message;

    @Column(name = "target_system", length = 64)
    private String targetSystem;

    @Column(name = "target_address", length = 1024)
    private String targetAddress;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
