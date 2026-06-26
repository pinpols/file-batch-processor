package com.example.filebatchprocessor.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.example.filebatchprocessor.batch.scheduler.TaskSchedulerService;
import com.example.filebatchprocessor.repository.DlqRecordRepository;
import com.example.filebatchprocessor.scheduler.OrchestrationTaskDefinition;
import com.example.filebatchprocessor.service.SchedulerLeaderService;
import com.example.filebatchprocessor.support.PostgresContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class TaskSchedulerServiceDlqIT extends PostgresContainerSupport {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("file_batch")
            .withUsername("filebatch")
            .withPassword("filebatch");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        // Docker 不可用时不抛异常(避免上下文加载直接报错):退回父类 PostgresContainerSupport 的
        // 本地 fallback,context 仍能加载;具体测试方法由 @BeforeEach 的 assumeTrue 干净跳过。
        if (!isDockerAvailable()) {
            return;
        }
        if (!POSTGRES.isRunning()) {
            POSTGRES.start();
        }
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.quartz.job-store-type", () -> "jdbc");
        // PG 的 qrtz_*.job_data 是 BYTEA,JDBC JobStore 须用 PostgreSQLDelegate
        registry.add(
                "spring.quartz.properties.org.quartz.jobStore.driverDelegateClass",
                () -> "org.quartz.impl.jdbcjobstore.PostgreSQLDelegate");
        registry.add("spring.quartz.jdbc.initialize-schema", () -> "never");
        registry.add("orchestration.config-source", () -> "db");
    }

    @Autowired
    private TaskSchedulerService taskSchedulerService;

    @Autowired
    private DlqRecordRepository dlqRecordRepository;

    @Autowired
    private SchedulerLeaderService schedulerLeaderService;

    @BeforeEach
    void reset() {
        // 该用例需真实 Testcontainers(独立容器 + JDBC Quartz);本机无 Docker 时干净跳过,交 CI 跑。
        org.junit.jupiter.api.Assumptions.assumeTrue(
                isDockerAvailable(), "Docker required for Testcontainers; skipping (runs in CI)");
        dlqRecordRepository.deleteAll();
    }

    @Test
    void missingJobIsRoutedToDlq() throws Exception {
        OrchestrationTaskDefinition def = OrchestrationTaskDefinition.builder()
                .id("missing-job-task")
                .jobName("missingJob")
                .build();

        awaitLeader();
        taskSchedulerService.register(def);
        awaitDlqCountAtLeast(1);
        assertTrue(dlqRecordRepository.count() >= 1, "DLQ should contain failed schedule for missing job");
    }

    private void awaitDlqCountAtLeast(long expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000L;
        while (System.currentTimeMillis() < deadline) {
            if (dlqRecordRepository.count() >= expected) {
                return;
            }
            Thread.sleep(200L);
        }
        fail("Timed out waiting for DLQ records");
    }

    private void awaitLeader() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000L;
        while (System.currentTimeMillis() < deadline) {
            if (schedulerLeaderService.isLeader()) {
                return;
            }
            Thread.sleep(200L);
        }
        fail("Timed out waiting for scheduler leader");
    }

    private static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Exception ex) {
            return false;
        }
    }
}
