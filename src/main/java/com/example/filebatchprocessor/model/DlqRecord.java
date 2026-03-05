package com.example.filebatchprocessor.model;


import jakarta.persistence.*;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "dlq_records")
public class DlqRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String jobName;

    @Column(length = 2000)
    private String params;

    @Column(length = 1000)
    private String errorMessage;

    @Column(length = 64)
    private String errorCode;

    private Boolean retryable = true;
    private Boolean manualRequired = false;

    @Column(length = 32)
    private String compensationStatus = "PENDING";

    private LocalDateTime nextRetryAt;

    private Boolean handled = false;
    private Long replayCount = 0L;

    @Column(length = 1000)
    private String lastReplayError;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime handledAt;
}
