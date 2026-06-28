package com.example.filebatchprocessor.config;

import com.example.filebatchprocessor.batch.writer.strategy.ImportContext;
import com.example.filebatchprocessor.listener.JobCompletionNotificationListener;
import com.example.filebatchprocessor.model.ReconcileDiffRecord;
import com.example.filebatchprocessor.model.ReconcileRunRecord;
import com.example.filebatchprocessor.params.ReconcileJobParams;
import com.example.filebatchprocessor.repository.ImportedRecordPartitionedRepository;
import com.example.filebatchprocessor.repository.ReconcileDiffRecordRepository;
import com.example.filebatchprocessor.repository.ReconcileRunRecordRepository;
import com.example.filebatchprocessor.util.PathSafety;
import com.opencsv.CSVReader;
import java.io.BufferedReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class ReconcileJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final ImportedRecordPartitionedRepository importedRecordPartitionedRepository;
    private final ReconcileRunRecordRepository reconcileRunRecordRepository;
    private final ReconcileDiffRecordRepository reconcileDiffRecordRepository;

    @Value("${batch.io.input-base-dir:}")
    private String inputBaseDir;

    public ReconcileJobConfig(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            ImportedRecordPartitionedRepository importedRecordPartitionedRepository,
            ReconcileRunRecordRepository reconcileRunRecordRepository,
            ReconcileDiffRecordRepository reconcileDiffRecordRepository) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.importedRecordPartitionedRepository = importedRecordPartitionedRepository;
        this.reconcileRunRecordRepository = reconcileRunRecordRepository;
        this.reconcileDiffRecordRepository = reconcileDiffRecordRepository;
    }

    @Bean
    @StepScope
    public Tasklet fileVsDbCountReconcileTasklet(@Value("#{jobParameters}") Map<String, Object> jobParameters) {

        ReconcileJobParams params = ReconcileJobParams.from(jobParameters);
        params.validate();

        return (contribution, chunkContext) -> {
            long sourceCount = countDataLines(params.getInputFileName());
            long targetCount = importedRecordPartitionedRepository.countByBatchDate(params.getBatchDate());

            String sourceHash = calculateSourceHash(params.getInputFileName(), params.getBatchDate());
            String targetHash = calculateTargetHash(params.getBatchDate());

            ReconcileRunRecord record = new ReconcileRunRecord();
            record.setJobName(params.getTargetJobName());
            record.setBatchDate(params.getBatchDate());
            record.setInputFileName(params.getInputFileName());
            record.setSourceCount(sourceCount);
            record.setTargetCount(targetCount);
            record.setSourceHash(sourceHash);
            record.setTargetHash(targetHash);

            if (sourceCount == targetCount) {
                record.setStatus("PASS");
                record.setMessage("sourceCount == targetCount");
            } else {
                record.setStatus("FAIL");
                record.setMessage("count mismatch: source=" + sourceCount + ", target=" + targetCount);
            }

            if (sourceHash != null && targetHash != null && !sourceHash.equalsIgnoreCase(targetHash)) {
                record.setStatus("FAIL");
                record.setMessage((record.getMessage() == null ? "" : record.getMessage() + "; ")
                        + "hash mismatch: source=" + sourceHash + ", target=" + targetHash);
            }

            reconcileRunRecordRepository.save(record);

            if ("FAIL".equalsIgnoreCase(record.getStatus())) {
                persistDiffSamples(record.getId(), params.getInputFileName(), params.getBatchDate());
            }
            return RepeatStatus.FINISHED;
        };
    }

    private void persistDiffSamples(Long reconcileRunId, String inputFileName, String batchDate) {
        if (reconcileRunId == null) {
            return;
        }
        try {
            // 对账失败时只保留有限样本，避免一次失败对账写入过多差异明细。
            List<String> sourceKeys = sampleSourceBusinessKeys(inputFileName, batchDate, 20);
            List<String> targetKeys = sampleTargetBusinessKeys(batchDate, 20);

            for (String k : sourceKeys) {
                if (!targetKeys.contains(k)) {
                    ReconcileDiffRecord d = new ReconcileDiffRecord();
                    d.setReconcileRunId(reconcileRunId);
                    d.setDiffType("SOURCE_ONLY");
                    d.setBusinessKey(k);
                    reconcileDiffRecordRepository.save(d);
                }
            }
            for (String k : targetKeys) {
                if (!sourceKeys.contains(k)) {
                    ReconcileDiffRecord d = new ReconcileDiffRecord();
                    d.setReconcileRunId(reconcileRunId);
                    d.setDiffType("TARGET_ONLY");
                    d.setBusinessKey(k);
                    reconcileDiffRecordRepository.save(d);
                }
            }
        } catch (Exception ex) {
            // 差异样本是辅助排查信息，采样失败不覆盖主对账结论。
        }
    }

    private List<String> sampleSourceBusinessKeys(String inputFileName, String batchDate, int limit) throws Exception {
        return canonicalSourceRows(inputFileName, batchDate).stream()
                .map(line -> line.split(",", 2)[0])
                .limit(Math.max(1, limit))
                .toList();
    }

    private List<String> sampleTargetBusinessKeys(String batchDate, int limit) {
        ArrayList<String> keys = new ArrayList<>();
        Page<?> p = importedRecordPartitionedRepository.findByBatchDateOrderByBusinessKeyAsc(
                batchDate, PageRequest.of(0, Math.max(1, limit)));
        p.forEach(r -> keys.add(((com.example.filebatchprocessor.model.ImportedRecordPartitioned) r).getBusinessKey()));
        return keys;
    }

    private long countDataLines(String inputFileName) throws Exception {
        FileSystemResource resource = new FileSystemResource(confinedInput(inputFileName));
        long count = 0L;
        try (BufferedReader br = Files.newBufferedReader(resource.getFile().toPath(), StandardCharsets.UTF_8)) {
            boolean first = true;
            String line;
            while ((line = br.readLine()) != null) {
                if (first) {
                    first = false;
                    continue;
                }
                if (!line.isBlank()) {
                    count++;
                }
            }
        }
        return count;
    }

    private String calculateSourceHash(String inputFileName, String batchDate) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        for (String line : canonicalSourceRows(inputFileName, batchDate)) {
            md.update(line.getBytes(StandardCharsets.UTF_8));
            md.update((byte) '\n');
        }
        return HexFormat.of().formatHex(md.digest());
    }

    private String calculateTargetHash(String batchDate) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        int page = 0;
        int size = 500;
        while (true) {
            Page<com.example.filebatchprocessor.model.ImportedRecordPartitioned> p =
                    importedRecordPartitionedRepository.findByBatchDateOrderByBusinessKeyAsc(
                            batchDate, PageRequest.of(page, size));
            // 不吞异常:单条拼接失败若被静默跳过,会让目标哈希少算记录却仍"完整"返回,
            // 把它本该保护的完整性校验悄悄掏空。任何失败应让对账可见地失败(方法已 throws Exception)。
            for (com.example.filebatchprocessor.model.ImportedRecordPartitioned rec : p) {
                String line = canonicalLine(rec.getBusinessKey(), rec.getName(), rec.getDescription());
                md.update(line.getBytes(StandardCharsets.UTF_8));
                md.update((byte) '\n');
            }
            if (!p.hasNext()) {
                break;
            }
            page++;
        }
        return HexFormat.of().formatHex(md.digest());
    }

    private List<String> canonicalSourceRows(String inputFileName, String overrideBatchDate) throws Exception {
        List<String> rows = new ArrayList<>();
        Path input = Path.of(confinedInput(inputFileName));
        try (Reader reader = Files.newBufferedReader(input, StandardCharsets.UTF_8);
                CSVReader csv = new CSVReader(reader)) {
            String[] header = csv.readNext();
            if (header == null) {
                return rows;
            }
            int nameIndex = indexOf(header, "name", header.length > 1 ? 1 : 0);
            int descriptionIndex = indexOf(header, "description", header.length > 2 ? 2 : -1);
            String[] row;
            while ((row = csv.readNext()) != null) {
                if (isBlankRow(row)) {
                    continue;
                }
                String rawName = valueAt(row, nameIndex);
                if (rawName.isBlank()) {
                    continue;
                }
                String batchDate = overrideBatchDate;
                String name = rawName.toUpperCase(Locale.ROOT);
                String businessKey = new ImportContext(batchDate, null, inputFileName, null)
                        .buildBusinessKeyFromFields(
                                Map.of("name", name, "description", valueAt(row, descriptionIndex)));
                rows.add(canonicalLine(businessKey, name, valueAt(row, descriptionIndex)));
            }
        }
        rows.sort(Comparator.naturalOrder());
        return rows;
    }

    private String confinedInput(String inputFileName) {
        return PathSafety.confine(inputBaseDir, inputFileName);
    }

    private static String canonicalLine(String businessKey, String name, String description) {
        return (businessKey == null ? "" : businessKey) + "," + (name == null ? "" : name) + ","
                + (description == null ? "" : description);
    }

    private static int indexOf(String[] header, String name, int fallback) {
        for (int i = 0; i < header.length; i++) {
            if (header[i] != null && header[i].trim().equalsIgnoreCase(name)) {
                return i;
            }
        }
        return fallback;
    }

    private static String valueAt(String[] row, int index) {
        if (index < 0 || index >= row.length || row[index] == null) {
            return "";
        }
        return row[index].trim();
    }

    private static boolean isBlankRow(String[] row) {
        if (row == null || row.length == 0) {
            return true;
        }
        for (String cell : row) {
            if (cell != null && !cell.isBlank()) {
                return false;
            }
        }
        return true;
    }

    @Bean
    public Step reconcileImportStep(Tasklet fileVsDbCountReconcileTasklet) {
        return new StepBuilder("reconcileImportStep", jobRepository)
                .tasklet(fileVsDbCountReconcileTasklet, transactionManager)
                .build();
    }

    @Bean
    public Job reconcileImportJob(Step reconcileImportStep, JobCompletionNotificationListener listener) {
        return new JobBuilder("reconcileImportJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(listener)
                .start(reconcileImportStep)
                .build();
    }
}
