package com.example.filebatchprocessor.integration;

import com.example.filebatchprocessor.model.DlqRecord;
import com.example.filebatchprocessor.repository.DlqRecordRepository;
import com.example.filebatchprocessor.repository.ImportedRecordPartitionedRepository;
import com.example.filebatchprocessor.service.DlqCompensationService;
import com.example.filebatchprocessor.support.PostgresContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DlqReplayIdempotencyIT extends PostgresContainerSupport {

    @Autowired
    private DlqCompensationService dlqCompensationService;
    @Autowired
    private DlqRecordRepository dlqRecordRepository;
    @Autowired
    private ImportedRecordPartitionedRepository importedRecordPartitionedRepository;

    @BeforeEach
    void setUp() {
        importedRecordPartitionedRepository.deleteAll();
        dlqRecordRepository.deleteAll();
    }

    @Test
    void shouldReplayDlqConcurrentlyWithoutDuplicateBusinessRows() throws ExecutionException, InterruptedException {
        String batchDate = "2026-03-14";
        String businessKey = "DLQ-IDEMPOTENT-KEY:" + batchDate;
        String payload = "businessKey=" + businessKey
                + "&name=CONCURRENT_USER"
                + "&description=from-dlq"
                + "&batchDate=" + batchDate
                + "&source=record-writer";

        DlqRecord r1 = createPending(payload);
        DlqRecord r2 = createPending(payload);
        dlqRecordRepository.saveAll(List.of(r1, r2));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Integer>> calls = List.of(
                    () -> dlqCompensationService.replayPending(10),
                    () -> dlqCompensationService.replayPending(10)
            );
            List<Future<Integer>> futures = new ArrayList<>();
            for (Callable<Integer> call : calls) {
                futures.add(pool.submit(call));
            }
            for (Future<Integer> f : futures) {
                f.get();
            }
        } finally {
            pool.shutdownNow();
        }

        long imported = importedRecordPartitionedRepository.countByBatchDate(batchDate);
        assertEquals(1L, imported, "same business key should be idempotent under concurrent replay");

        List<DlqRecord> records = dlqRecordRepository.findAll();
        assertEquals(2, records.size());
        assertTrue(records.stream().allMatch(r -> Boolean.TRUE.equals(r.getHandled())),
                "all replayed DLQ messages should be marked handled");
    }

    private DlqRecord createPending(String payload) {
        DlqRecord record = new DlqRecord();
        record.setJobName("importJob");
        record.setParams(payload);
        record.setErrorMessage("synthetic replay test");
        record.setRetryable(true);
        record.setManualRequired(false);
        record.setHandled(false);
        record.setReplayCount(0L);
        record.setCompensationStatus("PENDING");
        record.setNextRetryAt(LocalDateTime.now().minusSeconds(5));
        return record;
    }
}
