package com.example.filebatchprocessor.e2e;

import com.example.filebatchprocessor.model.ImportedRecordPartitioned;
import com.example.filebatchprocessor.repository.ImportedRecordPartitionedRepository;
import com.example.filebatchprocessor.repository.RecordTraceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.batch.core.BatchStatus.COMPLETED;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MixedScenarioE2ETest {

    @Autowired
    @Qualifier("asyncJobLauncher")
    private JobLauncher jobLauncher;

    @Autowired
    @Qualifier("fileImportJob")
    private Job fileImportJob;

    @Autowired
    private ImportedRecordPartitionedRepository importedRecordPartitionedRepository;

    @Autowired
    private RecordTraceRepository recordTraceRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        importedRecordPartitionedRepository.deleteAll();
        recordTraceRepository.deleteAll();
        purgeBatchMetadata();
    }

    @Test
    void shouldHandleConcurrentImports() throws Exception {
        String batchDate = LocalDate.now().toString();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Path fileA = tempDir.resolve("concurrent_a.csv");
        String contentA = "id,name,description\n"
                + "1,Concurrent A1,Desc A1\n"
                + "2,Concurrent A2,Desc A2\n";
        Files.writeString(fileA, contentA);

        Path fileB = tempDir.resolve("concurrent_b.csv");
        String contentB = "id,name,description\n"
                + "3,Concurrent B1,Desc B1\n"
                + "4,Concurrent B2,Desc B2\n";
        Files.writeString(fileB, contentB);

        CompletableFuture<JobExecution> futureA = CompletableFuture.supplyAsync(() -> runImport(fileA, batchDate), executor);
        CompletableFuture<JobExecution> futureB = CompletableFuture.supplyAsync(() -> runImport(fileB, batchDate), executor);

        JobExecution executionA = futureA.get(120, TimeUnit.SECONDS);
        JobExecution executionB = futureB.get(120, TimeUnit.SECONDS);

        assertEquals(COMPLETED, executionA.getStatus());
        assertEquals(COMPLETED, executionB.getStatus());

        List<ImportedRecordPartitioned> imported = importedRecordPartitionedRepository.findByBatchDate(batchDate);
        assertEquals(4, imported.size());

        assertTrue(recordTraceRepository.findAll().stream().anyMatch(t -> "importJob".equals(t.getJobName())));

        executor.shutdownNow();
    }

    @Test
    void shouldBeIdempotentForSameBusinessKeys() throws Exception {
        String batchDate = LocalDate.now().toString();
        Path inputFile = tempDir.resolve("idempotent.csv");
        String content = "id,name,description\n"
                + "1,Idempotent 1,Desc 1\n"
                + "2,Idempotent 2,Desc 2\n";
        Files.writeString(inputFile, content);

        JobExecution first = runImport(inputFile, batchDate);
        JobExecution second = runImport(inputFile, batchDate);

        assertEquals(COMPLETED, first.getStatus());
        assertEquals(COMPLETED, second.getStatus());

        List<ImportedRecordPartitioned> imported = importedRecordPartitionedRepository.findByBatchDate(batchDate);
        assertEquals(2, imported.size());
    }

    private JobExecution runImport(Path file, String batchDate) {
        try {
            JobParameters params = new JobParametersBuilder().addString("test.run", UUID.randomUUID().toString()).addLong("run.ts", System.nanoTime())
                    .addString("input.file.name", file.toString())
                    .addString("batchDate", batchDate)
                    .toJobParameters();
            return jobLauncher.run(fileImportJob, params);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void purgeBatchMetadata() {
        truncateIfExists("batch_step_execution_context");
        truncateIfExists("batch_step_execution");
        truncateIfExists("batch_job_execution_context");
        truncateIfExists("batch_job_execution_params");
        truncateIfExists("batch_job_execution");
        truncateIfExists("batch_job_instance");
    }

    private void truncateIfExists(String table) {
        String exists = jdbcTemplate.queryForObject("select to_regclass(?)", String.class, table);
        if (exists != null) {
            jdbcTemplate.execute("truncate table " + table);
        }
    }
}
