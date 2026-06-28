package com.example.filebatchprocessor.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.batch.core.BatchStatus.COMPLETED;

import com.example.filebatchprocessor.model.ImportedRecordPartitioned;
import com.example.filebatchprocessor.model.RecordTrace;
import com.example.filebatchprocessor.repository.ImportedRecordPartitionedRepository;
import com.example.filebatchprocessor.repository.RecordTraceRepository;
import com.example.filebatchprocessor.support.PostgresContainerSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
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

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CompleteBusinessFlowE2ETest extends PostgresContainerSupport {

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
    void shouldImportAndExportFlow() throws Exception {
        String batchDate = LocalDate.now().toString();
        Path inputFile = tempDir.resolve("business_flow_input.csv");
        String inputContent = "id,name,description\n"
                + "1,Flow Test 1,Business Description 1\n"
                + "2,Flow Test 2,Business Description 2\n"
                + "3,Flow Test 3,Business Description 3\n";
        Files.writeString(inputFile, inputContent);

        JobParameters importParams = new JobParametersBuilder()
                .addString("test.run", UUID.randomUUID().toString())
                .addLong("run.ts", System.nanoTime())
                .addString("input.file.name", inputFile.toString())
                .addString("batchDate", batchDate)
                .toJobParameters();

        JobExecution importExecution = jobLauncher.run(fileImportJob, importParams);
        assertEquals(COMPLETED, importExecution.getStatus());

        List<ImportedRecordPartitioned> importedRecords =
                importedRecordPartitionedRepository.findByBatchDate(batchDate);
        assertEquals(3, importedRecords.size());

        List<RecordTrace> traces = recordTraceRepository.findAll();
        assertTrue(traces.stream().anyMatch(t -> "fileImportJob".equals(t.getJobName())));
    }

    @Test
    void shouldSkipInvalidRecordAndStillComplete() throws Exception {
        String batchDate = LocalDate.now().toString();
        Path inputFile = tempDir.resolve("invalid_record.csv");
        String inputContent = "id,name,description\n"
                + "1,Valid Record 1,Valid Description 1\n"
                + "2,,Invalid Description\n"
                + "3,Valid Record 3,Valid Description 3\n";
        Files.writeString(inputFile, inputContent);

        JobParameters importParams = new JobParametersBuilder()
                .addString("test.run", UUID.randomUUID().toString())
                .addLong("run.ts", System.nanoTime())
                .addString("input.file.name", inputFile.toString())
                .addString("batchDate", batchDate)
                .toJobParameters();

        JobExecution importExecution = jobLauncher.run(fileImportJob, importParams);
        assertEquals(COMPLETED, importExecution.getStatus());

        List<ImportedRecordPartitioned> importedRecords =
                importedRecordPartitionedRepository.findByBatchDate(batchDate);
        assertEquals(2, importedRecords.size());
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
