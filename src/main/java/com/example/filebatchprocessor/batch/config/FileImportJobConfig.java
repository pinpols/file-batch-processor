package com.example.filebatchprocessor.batch.config;

import com.example.filebatchprocessor.batch.BatchJobNames;
import com.example.filebatchprocessor.batch.listener.ParseErrorRateGateListener;
import com.example.filebatchprocessor.batch.listener.ShardContextListener;
import com.example.filebatchprocessor.batch.preprocess.FilePreprocessor;
import com.example.filebatchprocessor.batch.preprocess.ImportTempFileHolder;
import com.example.filebatchprocessor.batch.preprocess.PreprocessResult;
import com.example.filebatchprocessor.batch.preprocess.TempFileManager;
import com.example.filebatchprocessor.batch.processor.FileImportRecordProcessor;
import com.example.filebatchprocessor.batch.processor.MappingImportProcessor;
import com.example.filebatchprocessor.batch.reader.FileImportRecordReader;
import com.example.filebatchprocessor.batch.reader.spi.DocumentReadOptions;
import com.example.filebatchprocessor.batch.reader.spi.DocumentRecordReaderFactory;
import com.example.filebatchprocessor.batch.reader.spi.RecordLineParserFactory;
import com.example.filebatchprocessor.batch.writer.FileImportRecordWriter;
import com.example.filebatchprocessor.batch.writer.strategy.BatchChunkImportStrategy;
import com.example.filebatchprocessor.batch.writer.strategy.ChunkImportStrategy;
import com.example.filebatchprocessor.batch.writer.strategy.PerRecordChunkImportStrategy;
import com.example.filebatchprocessor.exception.RecordValidationException;
import com.example.filebatchprocessor.exception.TransientImportException;
import com.example.filebatchprocessor.listener.JobCompletionNotificationListener;
import com.example.filebatchprocessor.mapping.MappingEngine;
import com.example.filebatchprocessor.mapping.TransformOp;
import com.example.filebatchprocessor.model.FeedDefinition;
import com.example.filebatchprocessor.model.FieldMapping;
import com.example.filebatchprocessor.model.FileRecord;
import com.example.filebatchprocessor.params.ImportJobParams;
import com.example.filebatchprocessor.repository.DlqRecordRepository;
import com.example.filebatchprocessor.repository.FeedDefinitionRepository;
import com.example.filebatchprocessor.repository.FieldMappingRepository;
import com.example.filebatchprocessor.repository.RecordTraceRepository;
import com.example.filebatchprocessor.service.DlqCompensationService;
import com.example.filebatchprocessor.service.PartitionedImportService;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.StringUtils;

/**
 * 导入链路 Job 配置：读取文件 -> 处理 -> 写入表 imported_records。
 */
@Slf4j
@Configuration
public class FileImportJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    @Autowired
    public FileImportJobConfig(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
    }

    @Value("${batch.input.file:}")
    private String inputFile;

    @Value("${batch.io.input-base-dir:}")
    private String inputBaseDir;

    @Value("${batch.import.retry-limit:3}")
    private int retryLimit;

    @Value("${batch.import.skip-limit:100}")
    private int skipLimit;

    @Bean
    @StepScope
    public FileImportRecordReader importReader(
            @Value("#{jobParameters}") Map<String, Object> jobParameters,
            RecordLineParserFactory recordLineParserFactory,
            DocumentRecordReaderFactory documentReaderFactory,
            FilePreprocessor filePreprocessor,
            ImportTempFileHolder tempFileHolder,
            FeedDefinitionRepository feedDefinitionRepository) {

        ImportJobParams params = ImportJobParams.from(jobParameters);
        params.validateForReader();

        Resource resource;
        if (StringUtils.hasText(params.getInputFileName())) {
            // 先做路径穿越防护(限定在 batch.io.input-base-dir 之内),再对受限路径做预处理(解密/解压)
            String confinedInput =
                    com.example.filebatchprocessor.util.PathSafety.confine(inputBaseDir, params.getInputFileName());
            try {
                PreprocessResult pr = filePreprocessor.prepare(
                        confinedInput, params.getInputFileEncrypted(), params.getInputFileCompression());
                tempFileHolder.set(pr.tempFileOrNull());
                resource = new FileSystemResource(pr.plaintextPath().toFile());
            } catch (Exception e) {
                throw new IllegalStateException("failed to preprocess input file: " + params.getInputFileName(), e);
            }
        } else if (StringUtils.hasText(inputFile)) {
            resource = new ClassPathResource(inputFile);
        } else {
            throw new IllegalArgumentException("input.file.name is required; default sample file is disabled");
        }

        String feedId = params.getFeedId();
        if (StringUtils.hasText(feedId)) {
            FeedDefinition feed = feedDefinitionRepository
                    .findByFeedIdAndEnabledTrue(feedId)
                    .orElseThrow(() -> new IllegalArgumentException("unknown or disabled feedId: " + feedId));
            String fmt = feed.getFormat() == null ? "CSV" : feed.getFormat().toUpperCase();
            if (!"CSV".equals(fmt)) {
                throw new IllegalArgumentException("feed 模式当前仅支持 CSV: feedId=" + feedId + " format=" + fmt);
            }
            String feedDelimiter = StringUtils.hasText(feed.getDelimiter()) ? feed.getDelimiter() : ",";
            // feed 模式:用 9 参构造器,feedHeaderColumns 传空 list(自动探测文件首行表头)
            return new FileImportRecordReader(
                    resource,
                    params.getShardIndex(),
                    params.getShardTotal(),
                    "CSV",
                    feedDelimiter,
                    recordLineParserFactory,
                    documentReaderFactory,
                    new DocumentReadOptions(params.getExcelSheetIndex(), params.getExcelSheetName()),
                    java.util.List.of());
        }

        return new FileImportRecordReader(
                resource,
                params.getShardIndex(),
                params.getShardTotal(),
                params.getFileFormat(),
                params.getFileDelimiter(),
                recordLineParserFactory,
                documentReaderFactory,
                new DocumentReadOptions(params.getExcelSheetIndex(), params.getExcelSheetName()));
    }

    @Value("${batch.import.max-dedup-keys:1000000}")
    private int maxDedupKeys;

    @Bean
    @StepScope
    public FileImportRecordWriter importWriter(
            @Value("#{jobParameters}") Map<String, Object> jobParameters,
            PartitionedImportService partitionedImportService,
            DlqRecordRepository dlqRecordRepository,
            RecordTraceRepository recordTraceRepository,
            FeedDefinitionRepository feedDefinitionRepository,
            FieldMappingRepository fieldMappingRepository) {
        ImportJobParams params = ImportJobParams.from(jobParameters);
        params.validateForWriter();
        // Strategy 模式:批量快路径 + 逐条降级路径,由 writer 作为 Context 选择
        ChunkImportStrategy batchStrategy =
                new BatchChunkImportStrategy(partitionedImportService, recordTraceRepository, transactionManager);
        ChunkImportStrategy fallbackStrategy = new PerRecordChunkImportStrategy(
                partitionedImportService, dlqRecordRepository, recordTraceRepository, transactionManager);

        String feedId = params.getFeedId();
        if (StringUtils.hasText(feedId)) {
            FeedDefinition feed = feedDefinitionRepository
                    .findByFeedIdAndEnabledTrue(feedId)
                    .orElseThrow(() -> new IllegalArgumentException("unknown or disabled feedId: " + feedId));
            String bkf = feed.getBusinessKeyFields();
            List<String> businessKeyFields = StringUtils.hasText(bkf)
                    ? java.util.Arrays.stream(bkf.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .toList()
                    : null;
            return new FileImportRecordWriter(
                    params.getBatchDate(), batchStrategy, fallbackStrategy, maxDedupKeys, businessKeyFields);
        }

        return new FileImportRecordWriter(params.getBatchDate(), batchStrategy, fallbackStrategy, maxDedupKeys);
    }

    @Bean
    @StepScope
    public org.springframework.batch.infrastructure.item.ItemProcessor<FileRecord, FileRecord> importProcessor(
            @Value("#{jobParameters}") Map<String, Object> jobParameters,
            FileImportRecordProcessor defaultProcessor,
            MappingEngine mappingEngine,
            FieldMappingRepository fieldMappingRepository) {
        ImportJobParams params = ImportJobParams.from(jobParameters);
        String feedId = params.getFeedId();
        if (!StringUtils.hasText(feedId)) {
            return defaultProcessor;
        }
        List<FieldMapping> mappings = fieldMappingRepository.findByFeedIdAndEnabledTrueOrderByOrderNoAsc(feedId);
        List<MappingEngine.MappingRule> rules = mappings.stream()
                .map(m -> new MappingEngine.MappingRule(
                        m.getSourceColumn(),
                        m.getTargetField(),
                        m.getTransformOp() == null ? TransformOp.NONE : m.getTransformOp(),
                        m.getTransformArg(),
                        m.isRequired()))
                .toList();
        return new MappingImportProcessor(defaultProcessor, mappingEngine, rules);
    }

    /**
     * afterStep 清理预处理产生的临时明文文件(透传时 holder 为空,什么也不做)。
     */
    @Bean
    public StepExecutionListener importTempCleanupListener(
            TempFileManager tempFileManager, ImportTempFileHolder tempFileHolder) {
        return new StepExecutionListener() {
            @Override
            public ExitStatus afterStep(StepExecution stepExecution) {
                java.nio.file.Path temp = tempFileHolder.get();
                if (temp != null) {
                    tempFileManager.delete(temp);
                    tempFileHolder.clear();
                }
                return stepExecution.getExitStatus();
            }
        };
    }

    @Bean
    public Step importStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            FileImportRecordReader reader,
            @Qualifier("importProcessor")
                    org.springframework.batch.infrastructure.item.ItemProcessor<FileRecord, FileRecord> processor,
            FileImportRecordWriter writer,
            JobCompletionNotificationListener listener,
            ParseErrorRateGateListener parseErrorRateGateListener,
            ShardContextListener shardContextListener,
            StepExecutionListener importTempCleanupListener,
            @Value("${batch.retry.limit:3}") int retryLimit,
            @Value("${batch.skip.limit:100}") int skipLimit,
            @Value("${batch.import.chunk-size:200}") int chunkSize) {
        return new StepBuilder("importStep", jobRepository)
                .<FileRecord, FileRecord>chunk(chunkSize)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .listener(parseErrorRateGateListener)
                .listener(shardContextListener)
                .listener(importTempCleanupListener)
                .faultTolerant()
                .retry(TransientImportException.class)
                .retryLimit(retryLimit)
                .skip(RecordValidationException.class)
                .skipLimit(skipLimit)
                .transactionManager(transactionManager)
                .build();
    }

    @Bean(BatchJobNames.FILE_IMPORT_JOB)
    public Job fileImportJob(JobCompletionNotificationListener listener, @Qualifier("importStep") Step importStep) {
        return new JobBuilder(BatchJobNames.FILE_IMPORT_JOB, jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(listener)
                .start(importStep)
                .build();
    }

    @Bean
    @StepScope
    public Tasklet dlqReplayTasklet(
            DlqCompensationService dlqCompensationService, @Value("#{jobParameters['limit'] ?: 50}") int limit) {
        return (contribution, chunkContext) -> {
            log.info("Starting DLQ replay with limit: {}", limit);
            int processed = dlqCompensationService.replayPending(limit);
            log.info("DLQ replay completed, processed {} records", processed);
            return RepeatStatus.FINISHED;
        };
    }

    @Bean
    public Step dlqReplayStep(Tasklet dlqReplayTasklet) {
        return new StepBuilder("dlqReplayStep", jobRepository)
                .tasklet(dlqReplayTasklet, transactionManager)
                .build();
    }

    @Bean(BatchJobNames.DLQ_REPLAY_JOB)
    public Job dlqReplayJob(Step dlqReplayStep, JobCompletionNotificationListener listener) {
        return new JobBuilder(BatchJobNames.DLQ_REPLAY_JOB, jobRepository)
                .listener(listener)
                .start(dlqReplayStep)
                .build();
    }
}
