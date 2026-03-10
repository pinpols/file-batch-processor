package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.model.DlqRecord;
import com.example.filebatchprocessor.model.ImportedRecord;
import com.example.filebatchprocessor.model.ImportedRecordPartitioned;
import com.example.filebatchprocessor.repository.DlqRecordRepository;
import com.example.filebatchprocessor.repository.ImportedRecordPartitionedRepository;
import com.example.filebatchprocessor.repository.ImportedRecordRepository;
import com.example.filebatchprocessor.support.PostgresContainerSupport;
import org.junit.jupiter.api.Test;
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
class BatchE2ERegressionITest extends PostgresContainerSupport {

    @Autowired
    @Qualifier("asyncJobLauncher")
    private JobLauncher jobLauncher;

    @Autowired
    @Qualifier("processFileJob")
    private Job processFileJob;

    @Autowired
    @Qualifier("dataExportJob")
    private Job dataExportJob;

    @Autowired
    private ImportedRecordPartitionedRepository partitionedRepository;

    @Autowired
    private ImportedRecordRepository importedRecordRepository;

    @Autowired
    private DlqRecordRepository dlqRecordRepository;

    @Autowired
    private DlqCompensationService dlqCompensationService;

    @Test
    void shouldRunImportDedupDlqReplayAndExportEndToEnd() throws Exception {
        String batchDate = "2026-03-01";

        Path csv = Files.createTempFile("batch-e2e", ".csv");
        Files.writeString(csv,
                "id,name,description\n" +
                "1,alice,first\n" +
                "2,alice,duplicate name\n" +
                "3,bob,second\n",
                StandardCharsets.UTF_8);

        JobExecution firstImport = jobLauncher.run(processFileJob, new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .addString("input.file.name", csv.toString())
                .addString("batchDate", batchDate)
                .addString("file.format", "CSV")
                .addString("file.delimiter", ",")
                .toJobParameters());
        assertEquals(BatchStatus.COMPLETED, firstImport.getStatus());
        assertEquals(2L, partitionedRepository.countByBatchDate(batchDate));

        JobExecution secondImport = jobLauncher.run(processFileJob, new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis() + 1)
                .addString("input.file.name", csv.toString())
                .addString("batchDate", batchDate)
                .addString("file.format", "CSV")
                .addString("file.delimiter", ",")
                .addString("rerun.id", "re-run-1")
                .toJobParameters());
        assertEquals(BatchStatus.COMPLETED, secondImport.getStatus());
        assertEquals(2L, partitionedRepository.countByBatchDate(batchDate), "duplicate import should be idempotent");

        DlqRecord dlq = new DlqRecord();
        dlq.setJobName("importJob");
        dlq.setParams("businessKey=CHARLIE:" + batchDate +
                "&name=CHARLIE&description=replayed&batchDate=" + batchDate +
                "&source=record-writer");
        dlq.setErrorMessage("synthetic replay case");
        dlq.setHandled(false);
        DlqRecord savedDlq = dlqRecordRepository.save(dlq);

        int replayed = dlqCompensationService.replayPending(10);
        assertTrue(replayed >= 1);
        DlqRecord handled = dlqRecordRepository.findById(savedDlq.getId()).orElseThrow();
        assertTrue(Boolean.TRUE.equals(handled.getHandled()));
        assertEquals(3L, partitionedRepository.countByBatchDate(batchDate));

        List<ImportedRecordPartitioned> partitioned = partitionedRepository.findByBatchDate(batchDate);
        List<ImportedRecord> plainRows = partitioned.stream().map(p -> {
            ImportedRecord r = new ImportedRecord();
            r.setBusinessKey(p.getBusinessKey());
            r.setName(p.getName());
            r.setDescription(p.getDescription());
            r.setBatchDate(p.getBatchDate());
            return r;
        }).toList();
        importedRecordRepository.saveAll(plainRows);

        Path output = Files.createTempFile("batch-e2e-export", ".csv");
        JobExecution exportExecution = jobLauncher.run(dataExportJob, new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis() + 2)
                .addString("export.sql", "select id, business_key, name, description, batch_date from imported_records where batch_date='" + batchDate + "'")
                .addString("output.file.name", output.toString())
                .toJobParameters());

        assertEquals(BatchStatus.COMPLETED, exportExecution.getStatus());
        assertTrue(Files.exists(output));
        List<String> lines = Files.readAllLines(output, StandardCharsets.UTF_8);
        assertTrue(lines.size() >= 4, "export should contain header + 3 data rows");
        assertTrue(lines.get(0).contains("business_key"));
    }
}
