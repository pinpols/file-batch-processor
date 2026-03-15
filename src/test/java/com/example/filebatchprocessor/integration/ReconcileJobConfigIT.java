package com.example.filebatchprocessor.integration;

import com.example.filebatchprocessor.model.ImportedRecordPartitioned;
import com.example.filebatchprocessor.model.ReconcileDiffRecord;
import com.example.filebatchprocessor.model.ReconcileRunRecord;
import com.example.filebatchprocessor.repository.ImportedRecordPartitionedRepository;
import com.example.filebatchprocessor.repository.ReconcileDiffRecordRepository;
import com.example.filebatchprocessor.repository.ReconcileRunRecordRepository;
import com.example.filebatchprocessor.support.PostgresContainerSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ENABLE_DOCKER_TESTS", matches = "true")
class ReconcileJobConfigIT extends PostgresContainerSupport {

    @Autowired
    @Qualifier("asyncJobLauncher")
    private JobLauncher jobLauncher;

    @Autowired
    @Qualifier("reconcileImportJob")
    private Job reconcileImportJob;

    @Autowired
    private ImportedRecordPartitionedRepository importedRecordPartitionedRepository;

    @Autowired
    private ReconcileRunRecordRepository reconcileRunRecordRepository;

    @Autowired
    private ReconcileDiffRecordRepository reconcileDiffRecordRepository;

    @Test
    void shouldPersistFailRunAndDiffSamplesWhenMismatch() throws Exception {
        String batchDate = "2026-03-02";

        ImportedRecordPartitioned row = new ImportedRecordPartitioned();
        row.setBusinessKey("Alice:" + batchDate);
        row.setName("ALICE");
        row.setDescription("x");
        row.setBatchDate(batchDate);
        importedRecordPartitionedRepository.save(row);

        Path csv = Files.createTempFile("reconcile", ".csv");
        Files.writeString(csv,
                "id,name,description\n" +
                "1,Alice,x\n" +
                "2,Bob,y\n",
                StandardCharsets.UTF_8);

        JobExecution execution = jobLauncher.run(reconcileImportJob, new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .addString("input.file.name", csv.toString())
                .addString("batchDate", batchDate)
                .addString("target.job.name", "importJob")
                .toJobParameters());

        assertEquals(BatchStatus.COMPLETED, execution.getStatus());

        List<ReconcileRunRecord> runs = reconcileRunRecordRepository.findTop50ByOrderByCreatedAtDesc();
        ReconcileRunRecord latest = runs.stream().filter(r -> batchDate.equals(r.getBatchDate())).findFirst().orElseThrow();
        assertEquals("FAIL", latest.getStatus());
        assertNotNull(latest.getSourceHash());
        assertNotNull(latest.getTargetHash());

        List<ReconcileDiffRecord> diffs = reconcileDiffRecordRepository.findTop200ByReconcileRunIdOrderByIdAsc(latest.getId());
        assertFalse(diffs.isEmpty(), "diff samples should be persisted when FAIL");
    }
}
