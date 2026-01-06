package com.example.filebatchprocessor.batch.config;

import com.example.filebatchprocessor.batch.processor.ImportFileRecordProcessor;
import com.example.filebatchprocessor.batch.reader.ImportFileRecordReader;
import com.example.filebatchprocessor.batch.writer.ImportFileRecordWriter;

import com.example.filebatchprocessor.listener.JobCompletionNotificationListener;
import com.example.filebatchprocessor.model.FileRecord;
import com.example.filebatchprocessor.repository.ImportedRecordRepository;


import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;

import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;

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
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 导入链路 Job 配置：读取文件 -> 处理 -> 写入表 imported_records。
 */
@Configuration
@EnableTransactionManagement
public class ImportJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    @Autowired
    public ImportJobConfig(JobRepository jobRepository,
                           PlatformTransactionManager transactionManager) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
    }

    @Value("${batch.input.file:data/sample.csv}")
    private String inputFile;

    @Bean
    @StepScope
    public ImportFileRecordReader importReader(
            @Value("#{jobParameters['input.file.name']}") String inputFileName,
            @Value("#{jobParameters['shard.index']}") Integer shardIndex,
            @Value("#{jobParameters['shard.total']}") Integer shardTotal,
            @Value("#{jobParameters['file.format']}") String fileFormat,
            @Value("#{jobParameters['file.delimiter']}") String fileDelimiter) {
        Resource resource;
        if (StringUtils.hasText(inputFileName)) {
            resource = new FileSystemResource(inputFileName);
        } else {
            resource = new ClassPathResource(inputFile);
        }
        return new ImportFileRecordReader(resource, shardIndex, shardTotal, fileFormat, fileDelimiter);
    }

    @Bean
    @StepScope
    public ImportFileRecordWriter importWriter(@Value("#{jobParameters['batch.date']}") String batchDate,
                                               ImportedRecordRepository importedRecordRepository) {
        return new ImportFileRecordWriter(batchDate, importedRecordRepository);
    }

    @Bean
    public Step importStep(ImportFileRecordReader reader,
                           ImportFileRecordProcessor processor,
                           ImportFileRecordWriter writer) {
        return new StepBuilder("importStep", jobRepository)
            .<FileRecord, FileRecord>chunk(10)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .faultTolerant()
            .skip(org.springframework.dao.DataIntegrityViolationException.class)
            .skipLimit(Integer.MAX_VALUE)
            .transactionManager(transactionManager)
            .build();
    }

    @Bean(name = "processFileJob")
    public Job processFileJob(JobCompletionNotificationListener listener,
                              @Qualifier("importStep") Step importStep) {
        return new JobBuilder("importJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(listener)
                .start(importStep)
                .build();
    }
}
