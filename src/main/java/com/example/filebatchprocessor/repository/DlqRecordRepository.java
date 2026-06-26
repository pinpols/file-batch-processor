package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.DlqRecord;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DlqRecordRepository extends JpaRepository<DlqRecord, Long> {
    List<DlqRecord> findTop100ByHandledFalseOrderByCreatedAtAsc();

    List<DlqRecord>
            findTop100ByHandledFalseAndManualRequiredFalseAndRetryableTrueAndNextRetryAtBeforeOrderByCreatedAtAsc(
                    LocalDateTime now);

    long countByHandledFalse();

    long countByHandledFalseAndManualRequiredTrue();

    long countByHandledFalseAndCompensationStatus(String compensationStatus);

    long deleteByHandledTrueAndHandledAtBefore(LocalDateTime cutoffTime);

    List<DlqRecord> findTop50ByParamsContainingOrderByCreatedAtDesc(String params);
}
