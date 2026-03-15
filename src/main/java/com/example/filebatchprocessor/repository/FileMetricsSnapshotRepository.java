package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.FileMetricsSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface FileMetricsSnapshotRepository extends JpaRepository<FileMetricsSnapshot, Long> {

    Optional<FileMetricsSnapshot> findFirstByMetricDateOrderBySnapshotTimeDesc(LocalDate metricDate);
}
