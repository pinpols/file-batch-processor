package com.example.filebatchprocessor.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "scheduler_queue_records")
public class SchedulerQueueRecord {

    @Id
    @Column(name = "run_key", length = 256)
    private String runKey;

    @Column(name = "task_id", nullable = false, length = 128)
    private String taskId;

    @Column(name = "batch_date", nullable = false, length = 32)
    private String batchDate;

    @Column(name = "rerun_id", nullable = false, length = 128)
    private String rerunId = "";

    @Column(name = "enqueued_at", nullable = false)
    private LocalDateTime enqueuedAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
