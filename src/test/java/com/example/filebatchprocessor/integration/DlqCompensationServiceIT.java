package com.example.filebatchprocessor.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.filebatchprocessor.model.DlqRecord;
import com.example.filebatchprocessor.repository.DlqRecordRepository;
import com.example.filebatchprocessor.repository.ImportedRecordPartitionedRepository;
import com.example.filebatchprocessor.service.DlqCompensationService;
import com.example.filebatchprocessor.support.PostgresContainerSupport;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
class DlqCompensationServiceIT extends PostgresContainerSupport {

    @Autowired
    private DlqCompensationService dlqCompensationService;

    @Autowired
    private DlqRecordRepository dlqRecordRepository;

    @Autowired
    private ImportedRecordPartitionedRepository importedRecordPartitionedRepository;

    @Test
    @Transactional
    void shouldReplayRecordWriterDlq() {
        DlqRecord record = new DlqRecord();
        record.setJobName("importJob");
        record.setParams(
                "businessKey=Alice:2026-03-01&name=Alice&description=test&batchDate=2026-03-01&source=record-writer");
        record.setErrorMessage("manual");
        record.setHandled(false);
        record.setRetryable(true);
        record.setManualRequired(false);
        record.setCompensationStatus("PENDING");
        record.setNextRetryAt(LocalDateTime.now().minusMinutes(1));
        dlqRecordRepository.save(record);

        int processed = dlqCompensationService.replayPending(10);
        assertTrue(processed >= 1);
        assertTrue(importedRecordPartitionedRepository
                .findByBusinessKeyAndBatchDate("Alice:2026-03-01", "2026-03-01")
                .isPresent());
    }

    @Test
    @Transactional
    void shouldMarkManualRequiredWhenReplayExceedsMaxCount() {
        DlqRecord record = new DlqRecord();
        record.setJobName("importJob");
        record.setParams("source=record-writer&businessKey=Bad:2026-03-01&batchDate=2026-03-01");
        record.setErrorMessage("manual");
        record.setHandled(false);
        record.setRetryable(true);
        record.setManualRequired(false);
        record.setCompensationStatus("RETRY_PENDING");
        record.setNextRetryAt(LocalDateTime.now().minusMinutes(1));
        record.setReplayCount(99L);
        DlqRecord saved = dlqRecordRepository.save(record);

        int processed = dlqCompensationService.replayPending(10);
        assertEquals(0, processed);

        DlqRecord updated = dlqRecordRepository.findById(saved.getId()).orElseThrow();
        assertTrue(Boolean.TRUE.equals(updated.getManualRequired()));
        assertEquals("MANUAL_REQUIRED", updated.getCompensationStatus());
    }
}
