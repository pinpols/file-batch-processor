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

    private Boolean handled = false;

    private LocalDateTime createdAt = LocalDateTime.now();
}
