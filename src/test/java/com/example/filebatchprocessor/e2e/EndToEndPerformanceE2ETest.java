package com.example.filebatchprocessor.e2e;

import com.example.filebatchprocessor.model.ImportedRecordPartitioned;
import com.example.filebatchprocessor.repository.ImportedRecordPartitionedRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.batch.core.BatchStatus.COMPLETED;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class EndToEndPerformanceE2ETest {

    @Autowired
    @Qualifier("asyncJobLauncher")
    private JobLauncher jobLauncher;

    @Autowired
    @Qualifier("fileImportJob")
    private Job fileImportJob;

    @Autowired
    private ImportedRecordPartitionedRepository importedRecordPartitionedRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        importedRecordPartitionedRepository.deleteAll();
        purgeBatchMetadata();
    }

    @Test
    void shouldProcessBatchWithinReasonableTime() throws Exception {
        String batchDate = LocalDate.now().toString();
        Path inputFile = tempDir.resolve("performance_input.csv");
        StringBuilder content = new StringBuilder("id,name,description\n");
        for (int i = 1; i <= 200; i++) {
            content.append(i).append(",Perf ").append(i).append(",Desc ").append(i).append("\n");
        }
        Files.writeString(inputFile, content.toString());

        long start = System.currentTimeMillis();
        JobParameters params = new JobParametersBuilder().addString("test.run", UUID.randomUUID().toString()).addLong("run.ts", System.nanoTime())
                .addString("input.file.name", inputFile.toString())
                .addString("batchDate", batchDate)
                .toJobParameters();

        JobExecution execution = jobLauncher.run(fileImportJob, params);
        long duration = System.currentTimeMillis() - start;

        assertEquals(COMPLETED, execution.getStatus());
        List<ImportedRecordPartitioned> imported = importedRecordPartitionedRepository.findByBatchDate(batchDate);
        assertEquals(200, imported.size());
        assertTrue(duration < 120_000, "Import should complete within 2 minutes");
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
            jdbcTemplate.execute("TRUNCATE TABLE " + table + " RESTART IDENTITY CASCADE");
        }
    }
}
