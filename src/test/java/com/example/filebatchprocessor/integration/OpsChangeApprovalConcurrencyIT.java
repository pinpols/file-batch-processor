package com.example.filebatchprocessor.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.filebatchprocessor.model.OpsChangeRequest;
import com.example.filebatchprocessor.model.TaskDefinition;
import com.example.filebatchprocessor.repository.OpsChangeRequestRepository;
import com.example.filebatchprocessor.repository.TaskDefinitionRepository;
import com.example.filebatchprocessor.service.OpsChangeManagementService;
import com.example.filebatchprocessor.support.PostgresContainerSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class OpsChangeApprovalConcurrencyIT extends PostgresContainerSupport {

    @Autowired
    private OpsChangeManagementService opsChangeManagementService;

    @Autowired
    private TaskDefinitionRepository taskDefinitionRepository;

    @Autowired
    private OpsChangeRequestRepository opsChangeRequestRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        opsChangeRequestRepository.deleteAll();
        // Flyway 给 task_definition 种了关联子表数据(trigger/parameter/dependency/dag_node),
        // 直接删会撞外键;用 TRUNCATE ... CASCADE 级联清空,顺序无关。
        jdbcTemplate.execute("TRUNCATE task_definition CASCADE");
    }

    @Test
    void shouldHandleConcurrentApproveRequestsAndKeepFinalStateConsistent() throws Exception {
        TaskDefinition task = new TaskDefinition();
        task.setTaskId("ops-approve-concurrent-task");
        task.setJobName("processFileJob");
        task.setEnabled(true);
        task.setAllowParallel(true);
        task.setPriority("NORMAL");
        taskDefinitionRepository.save(task);

        OpsChangeRequest request = opsChangeManagementService.createRequest(
                "alice",
                "TASK_DEFINITION",
                task.getTaskId(),
                "enabled",
                "false",
                "concurrency test",
                null,
                null,
                "none",
                "LOW",
                "rollback enabled=true");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(1);
        List<Callable<String>> jobs = List.of(
                () -> approveWithBarrier(request.getId(), "approver-1", latch),
                () -> approveWithBarrier(request.getId(), "approver-2", latch));

        List<Future<String>> futures = new ArrayList<>();
        for (Callable<String> job : jobs) {
            futures.add(pool.submit(job));
        }
        latch.countDown();

        int success = 0;
        int failed = 0;
        for (Future<String> future : futures) {
            try {
                String result = future.get(10, TimeUnit.SECONDS);
                if ("OK".equals(result)) {
                    success++;
                } else {
                    failed++;
                }
            } catch (ExecutionException ex) {
                failed++;
            }
        }
        pool.shutdownNow();

        OpsChangeRequest latest =
                opsChangeRequestRepository.findById(request.getId()).orElseThrow();
        assertEquals("APPROVED", latest.getStatus());
        assertTrue(success >= 1, "at least one approval should succeed");
        assertEquals(2, success + failed, "both concurrent requests should finish");
    }

    private String approveWithBarrier(Long requestId, String actor, CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
            opsChangeManagementService.approve(requestId, actor);
            return "OK";
        } catch (Exception ex) {
            return "ERR:" + ex.getClass().getSimpleName();
        }
    }
}
