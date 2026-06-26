package com.example.filebatchprocessor.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.filebatchprocessor.service.ExecutionDedupService;
import com.example.filebatchprocessor.support.PostgresContainerSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ExecutionDedupServiceIT extends PostgresContainerSupport {

    @Autowired
    private ExecutionDedupService executionDedupService;

    @Test
    void shouldAllowOnlyOneAcquireUnderConcurrency() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            futures.add(pool.submit(() -> executionDedupService.tryAcquire("k1", "2026-03-01", "r1", 60)));
        }
        int success = 0;
        for (Future<Boolean> future : futures) {
            if (Boolean.TRUE.equals(future.get(5, TimeUnit.SECONDS))) {
                success++;
            }
        }
        pool.shutdownNow();
        assertEquals(1, success);
    }
}
